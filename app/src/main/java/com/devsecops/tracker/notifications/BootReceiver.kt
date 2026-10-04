package com.devsecops.tracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// AlarmManager alarms are wiped on reboot — this puts the daily reminder
// back (at the saved time) so it survives the tablet being restarted.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.refresh(context)
        }
    }
}
