package com.unmiss.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pending_notification_uploads")
data class PendingNotificationUpload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "notification_key") val notificationKey: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "uploaded_at") val uploadedAt: Long? = null,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "package_name") val packageName: String = "",
    val title: String? = null,
    val body: String? = null,
    @ColumnInfo(name = "posted_at") val postedAt: Long = createdAt,
)

@Entity(tableName = "reminders")
data class LocalReminder(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val reason: String?,
    val importance: Int?,
    val quadrant: String = "important_not_urgent",
    val status: String,
    @ColumnInfo(name = "remind_at") val remindAt: String,
    @ColumnInfo(name = "displayed_at") val displayedAt: Long? = null,
)

@Dao
interface PendingNotificationUploadDao {

    @Insert
    suspend fun insert(entry: PendingNotificationUpload): Long

    @Query("SELECT * FROM pending_notification_uploads WHERE uploaded_at IS NULL ORDER BY id ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<PendingNotificationUpload>

    @Query("UPDATE pending_notification_uploads SET uploaded_at = :uploadedAt WHERE id = :id")
    suspend fun markUploaded(id: Long, uploadedAt: Long)

    @Query("UPDATE pending_notification_uploads SET uploaded_at = :uploadedAt, last_error = NULL WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>, uploadedAt: Long)

    @Query("UPDATE pending_notification_uploads SET retry_count = retry_count + 1, last_error = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE pending_notification_uploads SET retry_count = retry_count + 1, last_error = :error WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<Long>, error: String)

    @Query("DELETE FROM pending_notification_uploads")
    suspend fun clear()

    @Query("DELETE FROM pending_notification_uploads WHERE uploaded_at IS NOT NULL AND uploaded_at < :before")
    suspend fun deleteUploadedBefore(before: Long)

    @Query(
        """
        SELECT * FROM pending_notification_uploads
        WHERE (:fromTime IS NULL OR posted_at >= :fromTime)
          AND (:toTime IS NULL OR posted_at <= :toTime)
          AND (:packageName IS NULL OR package_name = :packageName)
          AND (:uploadState = 'all'
               OR (:uploadState = 'uploaded' AND uploaded_at IS NOT NULL)
               OR (:uploadState = 'pending' AND uploaded_at IS NULL))
          AND (:keyword = ''
               OR COALESCE(title, '') LIKE '%' || :keyword || '%' COLLATE NOCASE
               OR COALESCE(body, '') LIKE '%' || :keyword || '%' COLLATE NOCASE)
        ORDER BY posted_at DESC
        """,
    )
    fun observeHistory(
        fromTime: Long?,
        toTime: Long?,
        packageName: String?,
        uploadState: String,
        keyword: String,
    ): Flow<List<PendingNotificationUpload>>

    @Query("SELECT COUNT(*) FROM pending_notification_uploads")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_notification_uploads WHERE uploaded_at IS NULL")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT DISTINCT package_name FROM pending_notification_uploads WHERE package_name != '' ORDER BY package_name")
    fun observePackages(): Flow<List<String>>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE status IN ('candidate', 'pending') ORDER BY remind_at ASC")
    fun observePending(): kotlinx.coroutines.flow.Flow<List<LocalReminder>>

    @Query("SELECT * FROM reminders WHERE status IN ('candidate', 'pending')")
    suspend fun activeOnce(): List<LocalReminder>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun find(id: String): LocalReminder?

    @androidx.room.Upsert
    suspend fun upsertAll(reminders: List<LocalReminder>)

    @Query("DELETE FROM reminders WHERE status IN ('candidate', 'pending') AND id NOT IN (:remoteIds)")
    suspend fun deletePendingMissing(remoteIds: List<String>)

    @Query("DELETE FROM reminders WHERE status IN ('candidate', 'pending')")
    suspend fun deleteAllPending()

    @Query("UPDATE reminders SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("UPDATE reminders SET displayed_at = :displayedAt WHERE id = :id")
    suspend fun markDisplayed(id: String, displayedAt: Long)

    @Query("DELETE FROM reminders")
    suspend fun clear()
}

@Database(
    entities = [PendingNotificationUpload::class, LocalReminder::class],
    version = 4,
    exportSchema = false,
)
abstract class UnmissDatabase : RoomDatabase() {
    abstract fun pendingNotificationUploadDao(): PendingNotificationUploadDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_notification_uploads ADD COLUMN package_name TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pending_notification_uploads ADD COLUMN title TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE pending_notification_uploads ADD COLUMN body TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE pending_notification_uploads ADD COLUMN posted_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE pending_notification_uploads SET posted_at = created_at WHERE posted_at = 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN quadrant TEXT NOT NULL DEFAULT 'important_not_urgent'",
                )
            }
        }
    }
}
