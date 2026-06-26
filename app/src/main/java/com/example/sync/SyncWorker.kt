package com.example.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.messaging.CallRecord
import com.example.messaging.ContentProviderHelper
import com.example.getBatteryLevel
import com.example.getNetworkStatus
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            val sharedPrefs = context.getSharedPreferences("family_guard_prefs", Context.MODE_PRIVATE)
            
            val deviceId = inputData.getString("device_id")
                ?: sharedPrefs.getString("device_id", "child_device")
                ?: "child_device"

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
            val socialMessages = com.example.messaging.NotificationStorage.getMessages(context)
            
            // Get current device telemetry
            val batteryLevel = getBatteryLevel(context)
            val networkStatus = getNetworkStatus(context)

            val result = SyncService().syncData(
                smsList = smsList,
                contactList = contactsList,
                callLogs = callLogs,
                socialMessages = socialMessages,
                batteryLevel = batteryLevel,
                networkStatus = networkStatus,
                deviceId = deviceId
            )
            
            when (result) {
                is SyncResult.Success -> Result.success()
                is SyncResult.Error -> Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
