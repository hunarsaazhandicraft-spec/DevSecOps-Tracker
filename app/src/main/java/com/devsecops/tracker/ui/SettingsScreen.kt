package com.devsecops.tracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.Supa
import com.devsecops.tracker.backup.BackupExporter
import io.github.jan.supabase.auth.auth
import com.devsecops.tracker.data.Repository
import com.devsecops.tracker.notifications.ReminderPrefs
import com.devsecops.tracker.notifications.ReminderScheduler
import kotlinx.coroutines.launch

// Everything here is device-local (SharedPreferences), not account data.
// Each change is saved and re-armed immediately — no "Save" button to
// forget to press.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ReminderPrefs.isEnabled(context)) }
    var hour by remember { mutableStateOf(ReminderPrefs.hour(context)) }
    var minute by remember { mutableStateOf(ReminderPrefs.minute(context)) }
    var showPicker by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var permissionNote by remember { mutableStateOf<String?>(null) }

    fun persist(newEnabled: Boolean, newHour: Int, newMinute: Int) {
        ReminderPrefs.save(context, newEnabled, newHour, newMinute)
        ReminderScheduler.refresh(context)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionNote = null
            enabled = true
            persist(true, hour, minute)
        } else {
            permissionNote = "Notifications are blocked for this app. Allow them in system Settings > Apps > DevSecOps Tracker > Notifications to get reminders."
            enabled = false
            persist(false, hour, minute)
        }
    }

    Scaffold(
        topBar = {
            AppTitleBar(title = "Settings", subtitle = "Reminders and your data", onBack = onBack)
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppCard(padding = 0.dp) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Daily study reminder",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "One notification a day, on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { on ->
                                if (!on) {
                                    permissionNote = null
                                    enabled = false
                                    persist(false, hour, minute)
                                } else if (Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    permissionNote = null
                                    enabled = true
                                    persist(true, hour, minute)
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Reminder time", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                formatTime(hour, minute),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        TextButton(
                            enabled = enabled,
                            onClick = { showPicker = true }
                        ) { Text("Change") }
                    }
                }
            }

            Text(
                if (enabled) {
                    "Next reminder: ${formatTime(hour, minute)}, every day."
                } else {
                    "Reminders are off. No notifications will be sent."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            permissionNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            AppCard(padding = 0.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Backup",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Exports every study session, weak spot, mastery record and streak day as one JSON file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = !exporting,
                        onClick = {
                            exporting = true
                            exportNote = null
                            scope.launch {
                                runCatching {
                                    val json = Repository.exportAll()
                                    BackupExporter.writeAndShare(context, json)
                                }.onSuccess { file ->
                                    exporting = false
                                    exportNote = "Saved ${file.name}"
                                }.onFailure {
                                    exporting = false
                                    exportNote = it.message ?: "Export failed"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (exporting) "Exporting..." else "Export my data")
                    }
                    exportNote?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            var confirmSignOut by remember { mutableStateOf(false) }
            AppCard(padding = 0.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text("Account", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Signing out removes the saved session from this device. You sign in again with your password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
                }
            }
            if (confirmSignOut) {
                AlertDialog(
                    onDismissRequest = { confirmSignOut = false },
                    title = { Text("Sign out?") },
                    text = { Text("You will need your password to get back in.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmSignOut = false
                            scope.launch { runCatching { Supa.client.auth.signOut() } }
                        }) { Text("Sign out") }
                    },
                    dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
                )
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = pickerState.hour
                    minute = pickerState.minute
                    showPicker = false
                    persist(enabled, pickerState.hour, pickerState.minute)
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
            text = {
                TimePicker(state = pickerState)
            }
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val suffix = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val paddedMinute = if (minute < 10) "0$minute" else "$minute"
    return "$displayHour:$paddedMinute $suffix"
}
