package com.crm.phoneagent.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConfirmDisconnectDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect this phone?") },
        text = { Text("The CRM will no longer be able to place calls on this phone until you pair it again. Your call history in the CRM is not affected.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
