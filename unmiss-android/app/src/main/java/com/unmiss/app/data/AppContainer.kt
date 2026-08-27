package com.unmiss.app.data

import android.content.Context
import androidx.room.Room
import com.unmiss.app.data.db.PendingNotificationUploadDao
import com.unmiss.app.data.db.ReminderDao
import com.unmiss.app.data.db.UnmissDatabase
import com.unmiss.app.data.prefs.SettingsDataStore
import com.unmiss.app.data.remote.UnmissApiFactory
import com.unmiss.app.data.token.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsDataStore: SettingsDataStore = SettingsDataStore(context)

    val tokenStore: TokenStore = TokenStore(context)

    val database: UnmissDatabase by lazy {
        Room.databaseBuilder(context, UnmissDatabase::class.java, "unmiss.db")
            .addMigrations(UnmissDatabase.MIGRATION_2_3)
            .addMigrations(UnmissDatabase.MIGRATION_3_4)
            .addMigrations(UnmissDatabase.MIGRATION_4_5)
            .addMigrations(UnmissDatabase.MIGRATION_5_6)
            .addMigrations(UnmissDatabase.MIGRATION_6_7)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val pendingDao: PendingNotificationUploadDao get() = database.pendingNotificationUploadDao()
    val reminderDao: ReminderDao get() = database.reminderDao()

    val apiFactory: UnmissApiFactory = UnmissApiFactory(tokenStore)

    val notificationRepository: NotificationRepository by lazy {
        NotificationRepository(this)
    }

    val reminderRepository: ReminderRepository by lazy { ReminderRepository(this) }
}

object ServiceLocator {

    @Volatile
    private var container: AppContainer? = null

    fun init(appContainer: AppContainer) {
        if (container == null) container = appContainer
    }

    fun get(): AppContainer =
        requireNotNull(container) { "AppContainer not initialized" }
}
