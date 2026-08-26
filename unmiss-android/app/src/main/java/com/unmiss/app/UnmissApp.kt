package com.unmiss.app

import android.app.Application
import com.unmiss.app.data.AppContainer
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.reminder.ReminderSyncWorker
import com.unmiss.app.upload.UploadWorker

class UnmissApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ServiceLocator.init(container)
        ReminderSyncWorker.schedule(this)
        UploadWorker.schedule(this)
    }
}
