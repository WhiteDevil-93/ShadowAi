package com.shadowai.core.providers

import com.shadowai.core.ProviderId
import com.shadowai.core.Capability
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Configuration for an active AI provider model.
 * Used for routing and execution of AI tasks.
 */
@Parcelize
data class ActiveProviderConfig(
    val providerId: ProviderId,
    val modelId: String,
    val displayName: String,
    val apiStyle: ApiStyle,
    val baseUrl: String,
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val capabilities: List<Capability> = emptyList()
) : Parcelable {
    val isLocal: Boolean get() = providerId.isLocal()
}
