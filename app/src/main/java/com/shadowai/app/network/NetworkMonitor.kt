package com.shadowai.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network connectivity observer with real-time status updates.
 * 
 * Provides:
 * - Current connectivity status
 * - Connection type (WiFi, Cellular, Ethernet)
 * - Reactive flow for status changes
 * - Connection quality hints
 * 
 * Usage:
 * ```kotlin
 * // Check current status
 * if (networkMonitor.isConnected()) {
 *     makeApiCall()
 * }
 * 
 * // Observe changes
 * networkMonitor.networkStatus.collect { status ->
 *     updateUI(status)
 * }
 * ```
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(getCurrentNetworkStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    /**
     * Network connection status.
     */
    data class NetworkStatus(
        val isConnected: Boolean,
        val type: ConnectionType,
        val isMetered: Boolean,
        val hasInternet: Boolean
    ) {
        companion object {
            val DISCONNECTED = NetworkStatus(
                isConnected = false,
                type = ConnectionType.NONE,
                isMetered = false,
                hasInternet = false
            )
        }
    }

    /**
     * Connection type enumeration.
     */
    enum class ConnectionType {
        WIFI,
        CELLULAR,
        ETHERNET,
        VPN,
        NONE
    }

    init {
        registerNetworkCallback()
    }

    /**
     * Check if currently connected to any network.
     */
    fun isConnected(): Boolean = _networkStatus.value.isConnected

    /**
     * Check if connected to WiFi.
     */
    fun isWifiConnected(): Boolean = 
        _networkStatus.value.isConnected && _networkStatus.value.type == ConnectionType.WIFI

    /**
     * Check if on a metered connection (cellular or metered WiFi).
     */
    fun isMetered(): Boolean = _networkStatus.value.isMetered

    /**
     * Check if the network has actual internet access.
     */
    fun hasInternetCapability(): Boolean = _networkStatus.value.hasInternet

    /**
     * Get current network status synchronously.
     */
    private fun getCurrentNetworkStatus(): NetworkStatus {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatus.DISCONNECTED
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) 
            ?: return NetworkStatus.DISCONNECTED

        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
            else -> ConnectionType.NONE
        }

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                         capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return NetworkStatus(
            isConnected = true,
            type = type,
            isMetered = isMetered,
            hasInternet = hasInternet
        )
    }

    /**
     * Register for network status callbacks.
     */
    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                _networkStatus.value = getCurrentNetworkStatus()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
                _networkStatus.value = NetworkStatus.DISCONNECTED
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                Log.d(TAG, "Network capabilities changed")
                _networkStatus.value = getCurrentNetworkStatus()
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            Log.i(TAG, "Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Flow-based network status observation.
     * Emits distinct status changes only.
     */
    fun observeNetworkStatus(): Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentNetworkStatus())
            }

            override fun onLost(network: Network) {
                trySend(NetworkStatus.DISCONNECTED)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(getCurrentNetworkStatus())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial status
        trySend(getCurrentNetworkStatus())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Check if suitable for large downloads (WiFi and not metered).
     */
    fun isSuitableForLargeDownload(): Boolean {
        val status = _networkStatus.value
        return status.isConnected && 
               status.type == ConnectionType.WIFI && 
               !status.isMetered &&
               status.hasInternet
    }

    /**
     * Get human-readable network status description.
     */
    fun getStatusDescription(): String {
        val status = _networkStatus.value
        return when {
            !status.isConnected -> "No network connection"
            !status.hasInternet -> "Connected but no internet access"
            status.type == ConnectionType.WIFI && !status.isMetered -> "Connected via WiFi"
            status.type == ConnectionType.WIFI && status.isMetered -> "Connected via metered WiFi"
            status.type == ConnectionType.CELLULAR -> "Connected via cellular"
            status.type == ConnectionType.ETHERNET -> "Connected via Ethernet"
            status.type == ConnectionType.VPN -> "Connected via VPN"
            else -> "Connected"
        }
    }
}
