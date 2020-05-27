package com.dhruv.ftpserver

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.apache.ftpserver.ConnectionConfigFactory
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.greenrobot.eventbus.EventBus
import java.util.ArrayList

class FTPService: Service() ,Runnable{

    private var server: FtpServer? = null
    var wifi = true
    var hotspot = true
    private val DEFAULT_PATH: String? = Environment.getExternalStorageDirectory().absolutePath
    private var serverThread: Thread? = null

    enum class FtpReceiverActions {
        STARTED, STOPPED, FAILED_TO_START
    }

    override fun onCreate() {
        super.onCreate()
        val wifiFilter = IntentFilter()
        wifiFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        wifiFilter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
        registerReceiver(wifiStateReceiver, wifiFilter)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        serverThread = Thread(this)
        serverThread!!.start()
        val input = intent.getStringExtra("URL")
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this,
            0, notificationIntent, 0)
        val notification = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("FTP Service Running")
            .setContentText(input)
            .setSmallIcon(R.drawable.ic_share)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
        return START_STICKY
    }

    override fun run() {
        val serverFactory = FtpServerFactory()
        val connectionConfigFactory = ConnectionConfigFactory()
        connectionConfigFactory.isAnonymousLoginEnabled = true
        serverFactory.connectionConfig = connectionConfigFactory.createConnectionConfig()
        val user = BaseUser()
        user.name = "anonymous"
        user.homeDirectory = DEFAULT_PATH
        val list: MutableList<Authority> = ArrayList()
        list.add(WritePermission())
        user.authorities = list
        try {
            serverFactory.userManager.save(user)
        } catch (e: FtpException) {
            e.printStackTrace()
        }
        val fac = ListenerFactory()
        fac.port = MainActivity.PORT
        serverFactory.addListener("default", fac.createListener())
        try {
            server = serverFactory.createServer()
            server!!.start()
            EventBus.getDefault().post(FtpReceiverActions.STARTED)
        } catch (e: Exception) {
            e.printStackTrace()
            EventBus.getDefault().post(FtpReceiverActions.FAILED_TO_START)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serverThread!!.interrupt()
        try {
            serverThread!!.join(10000) // wait 10 sec for server thread to finish
        } catch (e: InterruptedException) {
        }
        server!!.stop()
        unregisterReceiver(wifiStateReceiver)
    }

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN) % 10
            if (ConnectivityManager.CONNECTIVITY_ACTION == intent.action) {
                val connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                wifi = mWifi.isConnected
            } else if ("android.net.wifi.WIFI_AP_STATE_CHANGED" == intent.action) {
                when (wifiState) {
                    WifiManager.WIFI_STATE_ENABLED -> hotspot = true
                    WifiManager.WIFI_STATE_DISABLED -> hotspot = false
                }
            }
            if (!(wifi || hotspot)) {
                EventBus.getDefault().post(FtpReceiverActions.STOPPED)
                stopSelf()
            }
        }
    }
}