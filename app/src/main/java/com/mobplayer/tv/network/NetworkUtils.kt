package com.mobplayer.tv.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Checks if the app is currently running on an Android Emulator.
     */
    fun isEmulator(): Boolean {
        return try {
            (Build.FINGERPRINT?.startsWith("generic") == true
                    || Build.FINGERPRINT?.startsWith("unknown") == true
                    || Build.MODEL?.contains("google_sdk") == true
                    || Build.MODEL?.contains("Emulator") == true
                    || Build.MODEL?.contains("Android SDK built for x86") == true
                    || Build.MANUFACTURER?.contains("Genymotion") == true
                    || Build.HARDWARE?.contains("goldfish") == true
                    || Build.HARDWARE?.contains("ranchu") == true
                    || Build.PRODUCT?.contains("sdk_gphone") == true
                    || Build.PRODUCT?.contains("google_sdk") == true
                    || Build.PRODUCT?.contains("sdk") == true
                    || Build.PRODUCT?.contains("sdk_x86") == true
                    || Build.PRODUCT?.contains("vbox86p") == true
                    || Build.PRODUCT?.contains("emulator") == true
                    || Build.PRODUCT?.contains("simulator") == true)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns the primary IPv4 address of this TV device (e.g. 192.168.1.150 or 10.0.2.16).
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Priority 1: wlan (Wi-Fi) or eth (Ethernet)
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val name = networkInterface.name.lowercase()
                if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            }

            // Priority 2: Any other active non-loopback IPv4 interface
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving local IP address", e)
        }
        return null
    }
}
