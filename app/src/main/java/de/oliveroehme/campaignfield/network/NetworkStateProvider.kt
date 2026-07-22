package de.oliveroehme.campaignfield.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun interface NetworkStateProvider {
    fun isOnline(): Boolean
}

class AndroidNetworkStateProvider(context: Context) : NetworkStateProvider {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

object AlwaysOnlineNetworkStateProvider : NetworkStateProvider {
    override fun isOnline(): Boolean = true
}
