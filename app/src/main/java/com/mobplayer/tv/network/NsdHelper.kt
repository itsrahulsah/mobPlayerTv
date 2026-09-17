package com.mobplayer.tv.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class NsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var registeredServiceName: String? = null
    
    fun registerService(port: Int, serviceName: String = "MobPlayer TV") {
        tearDown() // Ensure any existing is torn down
        
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }
        
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                registeredServiceName = NsdServiceInfo.serviceName
                Log.d(TAG, "Service registered successfully: $registeredServiceName")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service registration failed. Error code: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${arg0.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed. Error code: $errorCode")
            }
        }
        
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NSD Service", e)
        }
    }

    fun tearDown() {
        try {
            registrationListener?.let {
                nsdManager.unregisterService(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister NSD Service", e)
        } finally {
            registrationListener = null
            registeredServiceName = null
        }
    }

    companion object {
        private const val TAG = "NsdHelper"
        // standard _http._tcp or custom _mobplayer._tcp
        private const val SERVICE_TYPE = "_http._tcp."
    }
}
