package com.shadowai.backend

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import com.shadowai.backend.cache.TaskCache

fun main() {
    embeddedServer(Netty, port = 8081, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val json = Json { ignoreUnknownKeys = true }
    val novitaConfig = NovitaConfig(
        apiKey = environment.config.propertyOrNull("novita.apiKey")?.getString().orEmpty(),
        baseUrl = environment.config.propertyOrNull("novita.baseUrl")?.getString().orEmpty(),
        webhookSecret = environment.config.propertyOrNull("novita.webhookSecret")?.getString()
    )
    val pixaiConfig = PixAiConfig(
        apiKey = environment.config.propertyOrNull("pixai.apiKey")?.getString().orEmpty(),
        baseUrl = environment.config.propertyOrNull("pixai.baseUrl")?.getString() ?: "https://api.pixai.art",
        webhookSecret = environment.config.propertyOrNull("pixai.webhookSecret")?.getString()
    )
    if (novitaConfig.apiKey.isBlank() && pixaiConfig.apiKey.isBlank()) {
        throw IllegalStateException("At least one provider API KEY must be configured (NOVITA_API_KEY or PIXAI_API_KEY)")
    }
    val client = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(json)
        }
    }
    val taskCache = TaskCache()
    val novitaService = if (novitaConfig.apiKey.isNotBlank()) {
        NovitaService(client, novitaConfig, taskCache, json)
    } else null
    val pixaiService = if (pixaiConfig.apiKey.isNotBlank()) {
        PixAiService(client, pixaiConfig, taskCache, json)
    } else null
    val logger = environment.log
    // The API key is required for the webhook endpoints.  It is read from
    // the application configuration and validated at startup.
    val apiKey = environment.config.propertyOrNull("api.key")?.getString()
        ?: throw IllegalStateException("API key must be configured via 'api.key' property")
    val rateLimitMap = ConcurrentHashMap<String, Pair<Long, Int>>()

    /**
     * Simple in-memory rate limiter.  The map is cleaned up lazily when a
     * request is processed.  This keeps the memory footprint small while
     * still enforcing a 10-request-per-minute window.
     */
    fun isRateLimited(key: String): Boolean {
        val now = System.currentTimeMillis()
        val entry = rateLimitMap[key]
        if (entry == null || now - entry.first > 60000) {
            rateLimitMap[key] = Pair(now, 1)
            return false
        }
        if (entry.second >= 10) {
            return true
        }
        rateLimitMap[key] = Pair(entry.first, entry.second + 1)
        return false
    }

    this.install(ServerContentNegotiation) { json(json) }
    this.install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "unknown")))
        }
    }

    routing {
        post("/api/v1/image/generate") {
            val apiKeyHeader = call.request.headers["X-API-Key"]
            if (apiKeyHeader != apiKey) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid API key"))
                return@post
            }
            val clientIp = call.request.headers["X-Real-IP"] ?: call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim() ?: "127.0.0.1"
            if (isRateLimited(clientIp)) {
                call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limit exceeded"))
                return@post
            }
            val provider = call.request.queryParameters["provider"] ?: "novita"
            val request = call.receive<ImageGenerateRequest>()
            val entry = when (provider.lowercase()) {
                "pixai" -> pixaiService?.createImageTask(request)
                    ?: run {
                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "PixAI service not configured"))
                        return@post
                    }
                else -> novitaService?.createImageTask(request)
                    ?: run {
                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Novita service not configured"))
                        return@post
                    }
            }
            call.respond(HttpStatusCode.Accepted, TaskLaunchedResponse(taskId = entry.taskId))
        }

        get("/api/v1/image/status") {
            val apiKeyHeader = call.request.headers["X-API-Key"]
            if (apiKeyHeader != apiKey) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid API key"))
                return@get
            }
            val clientIp = call.request.headers["X-Real-IP"] ?: call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim() ?: "127.0.0.1"
            if (isRateLimited(clientIp)) {
                call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limit exceeded"))
                return@get
            }
            val taskId = call.request.queryParameters["task_id"]
            if (taskId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "task_id is required"))
                return@get
            }
            val entry = taskCache.get(taskId)
            if (entry == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "task not found"))
                return@get
            }
            call.respond(
                TaskStatusResponse(
                    taskId = entry.taskId,
                    status = entry.status,
                    progress = entry.progress,
                    images = entry.images.toList()
                )
            )
        }

        post("/api/v1/webhook/novita") {
            val body = call.receiveText()
            if (!WebhookVerifier.verify(body, call.request.headers["X-Novita-Signature"], novitaConfig.webhookSecret)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid signature"))
                return@post
            }
            val payload = json.decodeFromString<NovitaWebhookPayload>(body)
            if (payload.event_type != "ASYNC_TASK_RESULT") {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unsupported event"))
                return@post
            }
            val updated = novitaService?.processWebhook(payload)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Unknown task_id"))
            } else {
                call.respond(HttpStatusCode.Accepted, mapOf("status" to updated.status))
            }
        }

        post("/api/v1/webhook/pixai") {
            val body = call.receiveText()
            if (!WebhookVerifier.verify(body, call.request.headers["X-PixAI-Signature"], pixaiConfig.webhookSecret)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid signature"))
                return@post
            }
            val payload = json.decodeFromString<PixAiWebhookPayload>(body)
            if (payload.event_type != "ASYNC_TASK_RESULT") {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unsupported event"))
                return@post
            }
            val updated = pixaiService?.processWebhook(payload)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Unknown task_id"))
            } else {
                call.respond(HttpStatusCode.Accepted, mapOf("status" to updated.status))
            }
        }
    }
}