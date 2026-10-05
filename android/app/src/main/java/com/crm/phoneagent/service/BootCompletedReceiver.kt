package com.crm.phoneagent.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.crm.phoneagent.PhoneAgentApplication

/** Reconnects automatically after the phone reboots - a rep should never have to remember to
 *  re-open CRM Phone Agent after their phone restarts for the CRM to be able to call them. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as PhoneAgentApplication
        if (app.deviceStore.isPaired()) {
            PhoneAgentConnectionService.start(context)
        }
    }
}
