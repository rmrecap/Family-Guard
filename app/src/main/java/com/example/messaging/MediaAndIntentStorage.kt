package com.example.messaging

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TrackedMediaFile(
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val size: Long,
    val timestamp: Long,
    val fileContent: String = ""
)

data class TrackedIntent(
    val action: String,
    val dataString: String,
    val mimeType: String,
    val timestamp: Long,
    val extraDetails: String
)

object MediaAndIntentStorage {
    private const val PREFS_NAME = "family_guard_media_intents"
    private const val KEY_FILES = "tracked_files_list"
    private const val KEY_INTENTS = "tracked_intents_list"

    fun saveTrackedFile(context: Context, fileRecord: TrackedMediaFile) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = getTrackedFiles(context).toMutableList()
        // Prevent exact duplicates
        if (list.any { it.filePath == fileRecord.filePath && it.timestamp == fileRecord.timestamp }) {
            return
        }
        list.add(0, fileRecord)
        if (list.size > 100) {
            list.removeAt(list.size - 1)
        }
        
        val jsonArray = JSONArray()
        for (item in list) {
            val jsonObject = JSONObject().apply {
                put("fileName", item.fileName)
                put("filePath", item.filePath)
                put("mimeType", item.mimeType)
                put("size", item.size)
                put("timestamp", item.timestamp)
                put("fileContent", item.fileContent)
            }
            jsonArray.put(jsonObject)
        }
        sharedPrefs.edit().putString(KEY_FILES, jsonArray.toString()).apply()
    }

    fun getTrackedFiles(context: Context): List<TrackedMediaFile> {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = sharedPrefs.getString(KEY_FILES, "[]") ?: "[]"
        val list = mutableListOf<TrackedMediaFile>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TrackedMediaFile(
                        fileName = obj.optString("fileName", "Unknown File"),
                        filePath = obj.optString("filePath", ""),
                        mimeType = obj.optString("mimeType", "*/*"),
                        size = obj.optLong("size", 0L),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        fileContent = obj.optString("fileContent", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveTrackedIntent(context: Context, intentRecord: TrackedIntent) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = getTrackedIntents(context).toMutableList()
        
        // Prevent exact duplicate URLs / events within 5 seconds to reduce clutter
        val isDuplicate = list.take(5).any {
            it.dataString == intentRecord.dataString &&
            Math.abs(it.timestamp - intentRecord.timestamp) < 5000
        }
        if (isDuplicate) return

        list.add(0, intentRecord)
        if (list.size > 100) {
            list.removeAt(list.size - 1)
        }

        val jsonArray = JSONArray()
        for (item in list) {
            val jsonObject = JSONObject().apply {
                put("action", item.action)
                put("dataString", item.dataString)
                put("mimeType", item.mimeType)
                put("timestamp", item.timestamp)
                put("extraDetails", item.extraDetails)
            }
            jsonArray.put(jsonObject)
        }
        sharedPrefs.edit().putString(KEY_INTENTS, jsonArray.toString()).apply()
    }

    fun getTrackedIntents(context: Context): List<TrackedIntent> {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = sharedPrefs.getString(KEY_INTENTS, "[]") ?: "[]"
        val list = mutableListOf<TrackedIntent>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TrackedIntent(
                        action = obj.optString("action", "android.intent.action.VIEW"),
                        dataString = obj.optString("dataString", ""),
                        mimeType = obj.optString("mimeType", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        extraDetails = obj.optString("extraDetails", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun clearAll(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()
    }
}
