package com.mobplayer.tv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mobplayer.tv.R
import com.mobplayer.tv.network.NetworkStateMonitor
import com.mobplayer.tv.network.NsdHelper
import com.mobplayer.tv.server.KtorServerManager

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Monitor network changes to broadcast the correct IP via NSD
        networkMonitor.startMonitoring(
            onNetworkAvailable = {
                // Restart only the NSD broadcast
                nsdHelper.tearDown()
                nsdHelper.registerService(port = port, serviceName = "MobPlayer TV")
            },
            onNetworkLost = {
                // Stop broadcasting if network is lost
                nsdHelper.tearDown()
            }
        )
        
        return START_STICKY
    }

    override fun onDestroy() {
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
                "MobPlayer TV Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MobPlayer TV Server")
            .setContentText("Listening for controller connections...")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "MobPlayerTvServerChannel"
        private const val NOTIFICATION_ID = 1
    }
}
