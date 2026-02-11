package com.shadowai.app.providers

import com.shadowai.core.ProviderId

data class ActiveProviderConfig(
    val providerId: ProviderId,
    val baseUrl: okhttp3.HttpUrl?,
    val modelId: String,
    val apiStyle: ApiStyle,
    val authHeader: String?,
    val capabilities: List<Capability> = emptyList()
)
