package com.mobplayer.tv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mobplayer.tv.R
import com.mobplayer.tv.network.NetworkStateMonitor
import com.mobplayer.tv.network.NetworkUtils
import com.mobplayer.tv.network.NsdHelper
import com.mobplayer.tv.server.KtorServerManager
import com.mobplayer.tv.viewmodel.ServerEventBus

class WebSocketServerService : Service() {

    private lateinit var serverManager: KtorServerManager
    private lateinit var nsdHelper: NsdHelper
    private lateinit var networkMonitor: NetworkStateMonitor

    private val port = 8080

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        serverManager = KtorServerManager(this)
        nsdHelper = NsdHelper(this)
        networkMonitor = NetworkStateMonitor(this)
        
        // Start Ktor server exactly once. It binds to 0.0.0.0, so it handles IP changes automatically.
        serverManager.startServer(port = port)

        val localIp = NetworkUtils.getLocalIpAddress() ?: "0.0.0.0"
        val isEmulator = NetworkUtils.isEmulator()
        val displayIp = if (isEmulator) "10.0.2.2 (Local: $localIp)" else localIp
        Log.i(TAG, "🚀 [WebSocketServerService] Socket server UP on IP: $displayIp, Port: $port | Endpoint: ws://$localIp:$port/control (Emulator: $isEmulator)")
        ServerEventBus.logRemoteEvent("Service", "Socket server UP on IP: $displayIp, Port: $port")

        // Register NSD broadcast immediately on service startup
        nsdHelper.registerService(port = port, serviceName = getString(R.string.app_name))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Monitor network changes to broadcast the correct IP via NSD
        networkMonitor.startMonitoring(
            onNetworkAvailable = {
                val newIp = NetworkUtils.getLocalIpAddress() ?: "0.0.0.0"
                Log.i(TAG, "🌐 [Network Available] Socket server reachable on IP: $newIp, Port: $port | Endpoint: ws://$newIp:$port/control")
                ServerEventBus.logRemoteEvent("Network", "Network connected. Socket server at ws://$newIp:$port/control")
                // Restart only the NSD broadcast
                nsdHelper.tearDown()
                nsdHelper.registerService(port = port, serviceName = getString(R.string.app_name))
            },
            onNetworkLost = {
                Log.w(TAG, "⚠️ [Network Lost] Wi-Fi/Ethernet disconnected for socket server on port $port")
                ServerEventBus.logRemoteEvent("Network", "Network disconnected for socket server on port $port")
                // Stop broadcasting if network is lost
                nsdHelper.tearDown()
            }
        )
        
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "🛑 [WebSocketServerService] App task removed from Recents. Stopping socket server.")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "🛑 [WebSocketServerService] Shutting down socket server on port $port")
        networkMonitor.stopMonitoring()
        nsdHelper.tearDown()
        serverManager.stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null 
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
    }

    companion object {
        private const val TAG = "WebSocketServerService"
        private const val CHANNEL_ID = "MobPlayerTvServerChannel"
        private const val NOTIFICATION_ID = 1
    }
}
