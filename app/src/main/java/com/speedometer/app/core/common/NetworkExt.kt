package com.speedometer.app.core.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** True only when Android has validated real internet access (not just a connected Wi-Fi/captive network). */
fun Context.isNetworkConnected(): Boolean = runCatching {
    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}.getOrDefault(false)

/** Compose state that follows connectivity changes while a screen remains open. */
@Composable
fun rememberInternetConnected(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var connected by remember(context) { mutableStateOf(context.isNetworkConnected()) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                connected = manager.getNetworkCapabilities(network)?.isValidatedInternet() == true
            }
            override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
                connected = capabilities.isValidatedInternet()
            }
            override fun onLost(network: android.net.Network) {
                connected = context.isNetworkConnected()
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return connected
}

private fun NetworkCapabilities.isValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
