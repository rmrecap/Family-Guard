package com.example.messaging

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// =========================================================================
// 1. WhatsApp Business Integration
// =========================================================================
class WhatsAppBusinessClient(private val accessToken: String) {
    
    // Mimics the GraphRequest format and provides full logging / real REST capability
    suspend fun getChatHistory(onComplete: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://graph.facebook.com/v17.0/me/chats")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: "Empty Response"
                    Log.d("WhatsAppBusinessClient", "Response code: ${response.code}, Body: $body")
                    if (response.isSuccessful) {
                        onComplete("WhatsApp Success: $body")
                    } else {
                        onComplete("WhatsApp Error (HTTP ${response.code}): $body")
                    }
                }
            } catch (e: Exception) {
                Log.e("WhatsAppBusinessClient", "Failed to get chat history", e)
                onComplete("WhatsApp Error: ${e.localizedMessage}")
            }
        }
    }
}

// =========================================================================
// 2. Telegram Client (Supporting live message testing)
// =========================================================================
open class TelegramClient {
    
    open fun getBotToken(): String {
        return "YOUR_TELEGRAM_BOT_TOKEN"
    }

    suspend fun getUpdates(botToken: String): String {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/getUpdates")
                .get()
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        "SUCCESS"
                    } else {
                        "ERROR: ${response.code}"
                    }
                }
            } catch (e: Exception) {
                "ERROR: ${e.localizedMessage}"
            }
        }
    }

    // Real active integration: Send a message to Telegram channel using the Bot API
    suspend fun sendTelegramMessage(botToken: String, chatId: String, text: String): String {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            
            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }
            
            val requestBody = json.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/sendMessage")
                .post(requestBody)
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        "Telegram Send SUCCESS: $body"
                    } else {
                        "Telegram Send FAILED (HTTP ${response.code}): $body"
                    }
                }
            } catch (e: Exception) {
                Log.e("TelegramClient", "Error sending Telegram message", e)
                "Telegram Send ERROR: ${e.localizedMessage}"
            }
        }
    }
}

// =========================================================================
// 3. IMo Client (With highly interactive Sandbox Simulation)
// =========================================================================
class IMoClient {
    // Custom wrapper class mimicking the suggested 'imo.IMo' dependency
    class IMoSdkMock {
        fun fetchIncomingMessages(): List<String> {
            return listOf(
                "Dad: Hey, just checking in. Are you home?",
                "Mom: Remember to finish your homework!",
                "Brother: Can you bring some water?"
            )
        }
    }

    private val imo = IMoSdkMock()

    fun getMessages(): List<String> {
        Log.d("IMoClient", "Fetching messages using mock IMo SDK...")
        return imo.fetchIncomingMessages()
    }
}

// =========================================================================
// 4. Instagram Client Integration
// =========================================================================
class InstagramClient(private val accessToken: String) {
    
    // Mimics Instagram Graph API Request/Response
    suspend fun getMedia(onComplete: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://graph.instagram.com/me/media?fields=id,caption,media_type,media_url,timestamp&access_token=$accessToken")
                .get()
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: "Empty Response"
                    Log.d("InstagramClient", "Response: ${response.code}, Body: $body")
                    if (response.isSuccessful) {
                        onComplete("Instagram Success: $body")
                    } else {
                        onComplete("Instagram Error (HTTP ${response.code}): $body")
                    }
                }
            } catch (e: Exception) {
                Log.e("InstagramClient", "Failed to retrieve media", e)
                onComplete("Instagram Error: ${e.localizedMessage}")
            }
        }
    }
}
