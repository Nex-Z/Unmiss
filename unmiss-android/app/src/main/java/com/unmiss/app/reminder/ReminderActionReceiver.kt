package com.unmiss.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderDisplayWorker.KEY_REMINDER_ID) ?: return
        val action = intent.action ?: return
        val request = OneTimeWorkRequestBuilder<ReminderActionWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setInputData(
                workDataOf(
                    ReminderActionWorker.KEY_REMINDER_ID to id,
                    ReminderActionWorker.KEY_ACTION to action,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "unmiss-reminder-action-$id",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val ACTION_DONE = "com.unmiss.app.action.DONE"
        const val ACTION_SNOOZE = "com.unmiss.app.action.SNOOZE"
        const val ACTION_IGNORE = "com.unmiss.app.action.IGNORE"
    }
}
