package com.dhruv.ftpserver

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.android.synthetic.main.activity_main.*;

class MainActivity : AppCompatActivity() {


    private var isRunning = false
    private var wifi = false
    private var hotspot = false
    private var ip: String? = null
    private var URL: String? = null
    private var requestDialog: AlertDialog? = null
    private var infoDialog: AlertDialog? = null

    companion object {
        private const val REQUEST_CODE = 15
        const val PORT = 2121
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initDialogs()
        checkAndRequestPermissions()
    }


    private fun initDialogs() {
        requestDialog = AlertDialog.Builder(this)
            .setTitle("Permissions Needed")
            .setMessage(R.string.permission)
            .setNegativeButton("Cancel") { dialog, which -> finish() }
            .setPositiveButton("Ok") { dialog, which -> requestPermissions() }
            .setCancelable(false)
            .create()
        infoDialog = AlertDialog.Builder(this)
            .setTitle("Permissions Denied")
            .setMessage(R.string.permission_denied)
            .setPositiveButton("Ok") { dialog, which ->
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_CODE)
            }
            .setCancelable(false)
            .create()
        requestDialog!!.setCanceledOnTouchOutside(false)
        infoDialog!!.setCanceledOnTouchOutside(false)
    }

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION == intent.action) {
                val connManager =
                    getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                wifi = mWifi.isConnected
            } else if ("android.net.wifi.WIFI_AP_STATE_CHANGED" == intent.action) {
                when (intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN
                ) % 10) {
                    WifiManager.WIFI_STATE_ENABLED -> hotspot = true
                    WifiManager.WIFI_STATE_DISABLED -> hotspot = false
                }
            }
            if (!(wifi || hotspot)) {
                notConnected()
            } else {
                connected()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val wifiFilter = IntentFilter()
        wifiFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        wifiFilter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
        registerReceiver(wifiStateReceiver, wifiFilter)
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        isRunning = isServiceRunning()
        startBtn.setText(if (isRunning) R.string.stop_btn else R.string.start_btn)
        display.visibility = if (isRunning) View.VISIBLE else View.GONE
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(wifiStateReceiver)
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public fun onFtpReceiveActions(signal: FTPService.FtpReceiverActions?) {
        when (signal) {
            FTPService.FtpReceiverActions.STARTED -> {
                Toast.makeText(applicationContext, "Service Started", Toast.LENGTH_SHORT).show()
                startBtn.setText(R.string.stop_btn)
                display.visibility = View.VISIBLE
            }
            FTPService.FtpReceiverActions.FAILED_TO_START -> {
                Toast.makeText(applicationContext, "Service Failed to start", Toast.LENGTH_SHORT).show()
                startBtn.setText(R.string.start_btn)
                display.visibility = View.GONE
            }
            FTPService.FtpReceiverActions.STOPPED -> {
                Toast.makeText(applicationContext, "Service Stopped", Toast.LENGTH_SHORT).show()
                startBtn.setText(R.string.start_btn)
                display.visibility = View.GONE
                isRunning = false
            }
        }
    }

    private fun connected() {
        wifiStatus.text = "Connected"
        do {
            ip = getIpAddress()
        } while (hotspot && ip == null)
        ipAddress!!.text = ip
        URL = "ftp://$ip:$PORT"
        url.text = URL
        val multiFormatWriter = MultiFormatWriter()
        try {
            val bitMatrix = multiFormatWriter.encode(URL, BarcodeFormat.QR_CODE, 200, 200)
            val bitmap = BarcodeEncoder.createBitmap(bitMatrix)
            qrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
        }
        networkType.text = if (wifi) "Wifi" else "Wifi AP"
        startBtn.isEnabled = true
    }

    private fun notConnected() {
        wifiStatus.text = "Disconnected"
        ipAddress.text = "NA"
        networkType.text = "NA"
        startBtn.isEnabled = false
    }

    private fun startService() {
        val serviceIntent = Intent(this, FTPService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            serviceIntent.putExtra("URL", URL)
            startForegroundService(serviceIntent)
            isRunning = true
        }
    }

    private fun stopService() {
        val serviceIntent = Intent(this, FTPService::class.java)
        stopService(serviceIntent)
        isRunning = false
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) +
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
                , Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), REQUEST_CODE
        )
    }

    private fun showRationale(): Boolean {
        return (ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
                || ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ))
    }

    private fun checkAndRequestPermissions() {
        if (checkPermissions()) {
            requestPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] + grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            } else {
                if (showRationale()) {
                    requestDialog!!.show()
                } else {
                    infoDialog!!.show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (checkPermissions() && !showRationale()) {
                infoDialog!!.show()
            }
        }
    }

    private fun getIpAddress(): String? {
        val netInterface = NetworkInterface.getByName("wlan0")
        val addresses = netInterface.inetAddresses
        while (addresses.hasMoreElements()) {
            val address = addresses.nextElement()
            if (address is Inet4Address) {
                return address.getHostAddress()
            }
        }
        return null
    }

    private fun isServiceRunning(): Boolean {
        val activityManager =
            applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = activityManager.getRunningServices(Int.MAX_VALUE)
        for (runningServiceInfo in runningServices) {
            if (runningServiceInfo.service.className == FTPService::class.java.name) {
                return true
            }
        }
        return false
    }

    fun btnClick(v: View) {
        if (isRunning) {
            EventBus.getDefault().post(FTPService.FtpReceiverActions.STOPPED)
            stopService()
        } else {
            startService()
        }
    }

}
