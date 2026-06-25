package com.example.messaging

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class InterceptedMessage(
    val appName: String,     // e.g. "WhatsApp", "Messenger", "Instagram", "IMO", "Telegram"
    val packageName: String, // e.g. "com.whatsapp"
    val sender: String,
    val message: String,
    val timestamp: Long
)

object NotificationStorage {
    private const val PREFS_NAME = "family_guard_notifications"
    private const val KEY_NOTIFS = "notifications_list"

    fun saveMessage(context: Context, msg: InterceptedMessage) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = getMessages(context).toMutableList()
        // Add new message to the top
        list.add(0, msg)
        // Keep at most 150 messages
        if (list.size > 150) {
            list.removeAt(list.size - 1)
        }
        
        val jsonArray = JSONArray()
        for (item in list) {
            val jsonObject = JSONObject().apply {
                put("appName", item.appName)
                put("packageName", item.packageName)
                put("sender", item.sender)
                put("message", item.message)
                put("timestamp", item.timestamp)
            }
            jsonArray.put(jsonObject)
        }
        
        sharedPrefs.edit().putString(KEY_NOTIFS, jsonArray.toString()).apply()
    }

    fun getMessages(context: Context): List<InterceptedMessage> {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = sharedPrefs.getString(KEY_NOTIFS, "[]") ?: "[]"
        val messages = mutableListOf<InterceptedMessage>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                messages.add(
                    InterceptedMessage(
                        appName = jsonObject.optString("appName", "Unknown App"),
                        packageName = jsonObject.optString("packageName", ""),
                        sender = jsonObject.optString("sender", "Unknown"),
                        message = jsonObject.optString("message", ""),
                        timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return messages
    }
    
    fun clearMessages(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().remove(KEY_NOTIFS).apply()
    }
}
