package com.crm.phoneagent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.crm.phoneagent.data.SecureDeviceStore
import com.crm.phoneagent.service.PhoneAgentConnectionService

/** No DI framework: the app is small enough that a couple of app-scoped singletons are clearer
 *  than wiring Hilt/Koin for three collaborators. */
class PhoneAgentApplication : Application() {

    lateinit var deviceStore: SecureDeviceStore
        private set

    override fun onCreate() {
        super.onCreate()
        deviceStore = SecureDeviceStore(this)
        createNotificationChannel()
        if (deviceStore.isPaired()) {
            PhoneAgentConnectionService.start(this)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PhoneAgentConnectionService.CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
