package com.mobplayer.tv.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class NetworkStateMonitor @Inject constructor(@ApplicationContext context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    fun startMonitoring(onNetworkAvailable: () -> Unit, onNetworkLost: () -> Unit) {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
                onNetworkLost()
            }
        }
        
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    fun stopMonitoring() {
        try {
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
        } finally {
            networkCallback = null
        }
    }

    companion object {
        private const val TAG = "NetworkStateMonitor"
    }
}
