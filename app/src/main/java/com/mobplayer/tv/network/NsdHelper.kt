package com.mobplayer.tv.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log

class NsdHelper(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var registeredServiceName: String? = null
    
    fun registerService(port: Int, serviceName: String = "MobPlayer TV") {
        tearDown() // Ensure any existing registration is torn down

        try {
            multicastLock = wifiManager?.createMulticastLock("MobPlayerTvMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
                Log.d(TAG, "[NSD TV] Wi-Fi MulticastLock ACQUIRED (isHeld: $isHeld)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[NSD TV] Failed to acquire MulticastLock", e)
        }

        Log.i(TAG, "==================================================")
        Log.i(TAG, "[NSD TV] Registering local mDNS service:")
        Log.i(TAG, "[NSD TV]   Service Name: '$serviceName'")
        Log.i(TAG, "[NSD TV]   Service Type: '$SERVICE_TYPE' (mDNS: _mobplayer._tcp.local)")
        Log.i(TAG, "[NSD TV]   Port:         $port")
        Log.i(TAG, "==================================================")
        
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }
        
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registeredInfo: NsdServiceInfo) {
                registeredServiceName = registeredInfo.serviceName
                Log.i(TAG, "[NSD TV] >>> Service REGISTERED successfully!")
                Log.i(TAG, "[NSD TV]   Registered Name: '${registeredInfo.serviceName}'")
                Log.i(TAG, "[NSD TV]   Registered Type: '${registeredInfo.serviceType}'")
                Log.i(TAG, "[NSD TV]   Registered Port: ${registeredInfo.port}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(
                    TAG,
                    "[NSD TV] Service registration FAILED for '${serviceInfo.serviceName}'! " +
                            "Reason: ${errorCodeToString(errorCode)} (code: $errorCode)"
                )
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.i(TAG, "[NSD TV] <<< Service UNREGISTERED: '${arg0.serviceName}'")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(
                    TAG,
                    "[NSD TV] Service unregistration FAILED for '${serviceInfo.serviceName}'! " +
                            "Reason: ${errorCodeToString(errorCode)} (code: $errorCode)"
                )
            }
        }
        
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "[NSD TV] Exception during registerService for '$serviceName'", e)
        }
    }

    fun tearDown() {
        try {
            registrationListener?.let {
                Log.d(TAG, "[NSD TV] Tearing down existing NSD service registration...")
                nsdManager.unregisterService(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[NSD TV] Failed to unregister NSD Service during tearDown", e)
        } finally {
            registrationListener = null
            registeredServiceName = null
        }

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Log.d(TAG, "[NSD TV] Wi-Fi MulticastLock RELEASED")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[NSD TV] Failed to release MulticastLock", e)
        } finally {
            multicastLock = null
        }
    }

    private fun errorCodeToString(errorCode: Int): String {
        return when (errorCode) {
            NsdManager.FAILURE_INTERNAL_ERROR -> "FAILURE_INTERNAL_ERROR"
            NsdManager.FAILURE_ALREADY_ACTIVE -> "FAILURE_ALREADY_ACTIVE"
            NsdManager.FAILURE_MAX_LIMIT -> "FAILURE_MAX_LIMIT"
            else -> "UNKNOWN_ERROR"
        }
    }

    companion object {
        private const val TAG = "NsdHelper"
        // Custom MobPlayer TV service type: _mobplayer._tcp. (resolves on mDNS to _mobplayer._tcp.local)
        private const val SERVICE_TYPE = "_mobplayer._tcp."
    }
}
