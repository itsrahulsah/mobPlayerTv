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
import com.mobplayer.tv.repository.ServerRepository
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class WebSocketServerService : Service() {

    @Inject lateinit var serverManager: KtorServerManager
    @Inject lateinit var nsdHelper: NsdHelper
    @Inject lateinit var networkMonitor: NetworkStateMonitor
    @Inject lateinit var serverRepository: ServerRepository

    private val port = PORT

    /** Set in onDestroy so a network callback queued behind the shutdown doesn't re-register NSD. */
    @Volatile private var isStopped = false

    override fun onCreate() {
        // First thing, ahead of super.onCreate() (Hilt injection builds the server graph), so the
        // startForegroundService() promise is kept even if the work below is slow
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        super.onCreate()

        val serviceName = getString(R.string.app_name)
        // Ktor start-up (class loading, socket bind) and NSD/network-callback binder calls take
        // seconds on a loaded TV; off the main thread they can't stall the UI or trip an ANR.
        // Serialised on serverExecutor so a restart's start never overtakes the previous stop.
        serverExecutor.execute {
            // Start Ktor server exactly once. It binds to 0.0.0.0, so it handles IP changes automatically.
            serverManager.startServer(port = port)

            // Fires onAvailable right away when a network is up, which registers the NSD broadcast;
            // later changes re-register it so phones discover the current IP.
            networkMonitor.startMonitoring(
                onNetworkAvailable = {
                    val newIp = NetworkUtils.getLocalIpAddress() ?: "0.0.0.0"
                    Log.i(TAG, "🌐 [Network Available] Socket server reachable on IP: $newIp, Port: $port | Endpoint: ws://$newIp:$port/control")
                    serverRepository.logRemoteEvent("Network", "Network connected. Socket server at ws://$newIp:$port/control")
                    serverExecutor.execute {
                        if (!isStopped) nsdHelper.registerService(port = port, serviceName = serviceName)
                    }
                },
                onNetworkLost = {
                    Log.w(TAG, "⚠️ [Network Lost] Wi-Fi/Ethernet disconnected for socket server on port $port")
                    serverRepository.logRemoteEvent("Network", "Network disconnected for socket server on port $port")
                    // Stop broadcasting if network is lost
                    serverExecutor.execute { nsdHelper.tearDown() }
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "🛑 [WebSocketServerService] App task removed from Recents. Stopping socket server.")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "🛑 [WebSocketServerService] Shutting down socket server on port $port")
        isStopped = true
        // stopServer() waits up to 2s for Ktor to shut down; keep that off the main thread too
        serverExecutor.execute {
            networkMonitor.stopMonitoring()
            nsdHelper.tearDown()
            serverManager.stopServer()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null 
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    companion object {
        private const val TAG = "WebSocketServerService"
        private const val CHANNEL_ID = "MobPlayerTvServerChannel"
        private const val NOTIFICATION_ID = 1

        private const val PORT = 8080

        /** Process-wide, so start/stop stay ordered across service instances (activity recreation). */
        private val serverExecutor = Executors.newSingleThreadExecutor { Thread(it, "SocketServer") }

        /**
         * Starts the server (and so the pairing PIN) right away. The service's own start waits for
         * onCreate, which the main thread only gets to after the activity's first frame — seconds
         * on a cold TV. Its later start is then a no-op.
         */
        fun startServerEarly(serverManager: KtorServerManager) {
            serverExecutor.execute { serverManager.startServer(port = PORT) }
        }

        /**
         * Stops the service along with an early-started server: a service stopped before its
         * onCreate (Back on the startup loader) never runs onDestroy to stop the server itself.
         */
        fun stop(context: Context, serverManager: KtorServerManager) {
            context.stopService(Intent(context, WebSocketServerService::class.java))
            serverExecutor.execute { serverManager.stopServer() }
        }
    }
}
