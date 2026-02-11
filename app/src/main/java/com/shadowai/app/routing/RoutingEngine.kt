package com.shadowai.app.routing

import com.shadowai.app.tasks.Task
import com.shadowai.app.providers.ActiveProviderManager
import com.shadowai.app.providers.ApiStyle
import com.shadowai.app.providers.Capability
import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.device.implementation.AndroidResourceMonitor
import com.shadowai.app.providers.ProviderRepository
import com.shadowai.core.ProviderId
import com.shadowai.app.tasks.TaskType
import javax.inject.Inject
import javax.inject.Singleton

interface RoutingEngine {
    suspend fun determineRouting(task: Task, policy: RoutingPolicy): RoutingDecision
}

@Singleton
class PriorityRoutingHub @Inject constructor(
    private val activeProviderManager: ActiveProviderManager,
    private val adminRepo: AdminRepository,
    private val resourceMonitor: AndroidResourceMonitor,
    private val providerRepository: ProviderRepository
) : RoutingEngine {
    override suspend fun determineRouting(task: Task, policy: RoutingPolicy): RoutingDecision {
        // Governance: Respect FORCE policies first
        if (policy == RoutingPolicy.FORCE_LOCAL) {
             return RoutingDecision(policy, ExecutionSource.LOCAL, "Policy enforced local execution", "Policy")
        }
        if (policy == RoutingPolicy.FORCE_CLOUD) {
             return RoutingDecision(policy, ExecutionSource.CLOUD, "Policy enforced cloud execution", "Policy")
        }

        // Phase 5.2: Resource Awareness
        val canExecuteLocal = resourceMonitor.canExecuteLocal()
        val resourceStatus = resourceMonitor.getResourceStatus()

        // Phase 4.1: Trust-Based Degradation
        val isTrusted = adminRepo.isBackendTrusted()

        // AUTO Policy Logic
        val configs = activeProviderManager.getActiveConfigs(null)
        val localConfigsExist = configs.any { isLocalStyle(it.apiStyle) && supportsTask(it.providerId, task.type) }
        val localAvailable = localConfigsExist && canExecuteLocal
        val cloudAvailable = configs.any { !isLocalStyle(it.apiStyle) && supportsTask(it.providerId, task.type) }

        // Phase 5.1: Priority Weights
        var selectedSource = when {
            // Priority 1: Sensitive Device Control stays LOCAL (Edge Intelligence)
            task.type == com.shadowai.app.tasks.TaskType.DEVICE_CONTROL && localAvailable -> {
                ExecutionSource.LOCAL
            }
            // Priority 2: Complex Conversation goes to CLOUD (if available and TRUSTED)
            task.type == com.shadowai.app.tasks.TaskType.CONVERSATION && cloudAvailable && isTrusted -> {
                ExecutionSource.CLOUD
            }
            // Priority 3: Fallback based on availability (Prefer local if untrusted)
            localAvailable -> ExecutionSource.LOCAL
            cloudAvailable && isTrusted -> ExecutionSource.CLOUD
            // Priority 4: If local is resource-restricted but no cloud available, 
            // still try local with reduced performance (LocalLiquidEngine handles this)
            localConfigsExist && !canExecuteLocal && !cloudAvailable -> ExecutionSource.LOCAL
            else -> ExecutionSource.LOCAL
        }
        
        // If untrusted and forced to cloud, we still allow but mark it
        if (!isTrusted && selectedSource == ExecutionSource.CLOUD) {
            if (localAvailable) selectedSource = ExecutionSource.LOCAL
        }

        val reason = when {
            !canExecuteLocal && cloudAvailable -> "Local compute restricted: $resourceStatus. Routing to Cloud."
            !canExecuteLocal && !cloudAvailable && localConfigsExist -> {
                "Local compute restricted: $resourceStatus. No cloud configured - attempting local with reduced performance."
            }
            !isTrusted -> "Trust Score below threshold. Forcing local execution/validation for safety."
            task.type == com.shadowai.app.tasks.TaskType.DEVICE_CONTROL && selectedSource == ExecutionSource.LOCAL -> {
                "Edge Intelligence: Routing sensitive device control to local compute."
            }
            else -> "Auto-routing based on task type and capability availability."
        }

        return RoutingDecision(
            policy = policy,
            selectedSource = selectedSource,
            reason = reason,
            overrideSource = "Hub"
        )
    }

    private fun supportsTask(providerId: ProviderId, taskType: TaskType): Boolean {
        val required = requiredCapability(taskType)
        return providerRepository.getProvider(providerId)?.capabilities?.contains(required) == true
    }

    private fun requiredCapability(taskType: TaskType): Capability {
        return when (taskType) {
            TaskType.IMAGE_GEN -> Capability.IMAGE_GEN
            else -> Capability.TEXT
        }
    }

    private fun isLocalStyle(style: ApiStyle): Boolean {
        return style == ApiStyle.LOCAL_IMAGE || style == ApiStyle.LOCAL_TEXT || style == ApiStyle.LIQUID
    }
}

