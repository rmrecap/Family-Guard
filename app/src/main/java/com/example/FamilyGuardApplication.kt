package com.example

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

class FamilyGuardApplication : Application(), Configuration.Provider {
    
    companion object {
        lateinit var instance: FamilyGuardApplication
            private set
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            WorkManager.initialize(this, workManagerConfiguration)
        } catch (e: IllegalStateException) {
            // Already initialized, ignore
        }
    }
}
