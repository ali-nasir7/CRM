package com.crm.phoneagent.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** The permissions the app actually asks for - nothing more. CALL_PHONE is the only one that
 *  gates being able to pair at all; the others degrade gracefully (documented in the final
 *  report) rather than blocking the rep from connecting their phone. */
object AgentPermissions {
    val required: Array<String> = buildList {
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasCallPermission(context: Context) = isGranted(context, Manifest.permission.CALL_PHONE)

    fun allGranted(context: Context) = required.all { isGranted(context, it) }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
