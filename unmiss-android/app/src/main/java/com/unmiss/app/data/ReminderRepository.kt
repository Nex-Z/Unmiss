package com.unmiss.app.data

import com.unmiss.app.data.db.LocalReminder
import com.unmiss.app.data.remote.ReminderDto
import com.unmiss.app.data.remote.SnoozeReminderRequest
import com.unmiss.app.data.remote.UnmissApi
import retrofit2.HttpException
import java.time.Instant

class ReminderRepository(private val container: AppContainer) {

    val pending = container.reminderDao.observePending()

    suspend fun sync(): List<LocalReminder> = authenticatedCall { api ->
        val remote = api.pendingReminders()
        val local = remote.map { dto -> dto.toLocal(container.reminderDao.find(dto.id)?.displayedAt) }
        if (local.isEmpty()) {
            container.reminderDao.deleteAllPending()
        } else {
            container.reminderDao.upsertAll(local)
            container.reminderDao.deletePendingMissing(local.map { it.id })
        }
        local
    }

    suspend fun done(id: String) = authenticatedCall { api ->
        api.completeReminder(id)
        container.reminderDao.setStatus(id, "done")
    }

    suspend fun ignore(id: String) = authenticatedCall { api ->
        api.ignoreReminder(id)
        container.reminderDao.setStatus(id, "ignored")
    }

    suspend fun snooze(id: String, remindAt: Instant) = authenticatedCall { api ->
        val updated = api.snoozeReminder(id, SnoozeReminderRequest(remindAt.toString()))
        container.reminderDao.upsertAll(listOf(updated.toLocal(displayedAt = null)))
    }

    suspend fun deleteAllData() = authenticatedCall { api ->
        api.deleteMyData()
        container.reminderDao.clear()
        container.pendingDao.clear()
        container.tokenStore.clear()
    }

    private suspend fun <T> authenticatedCall(block: suspend (UnmissApi) -> T): T {
        repeat(2) { attempt ->
            container.notificationRepository.ensureRegistered()
            val api = container.apiFactory.create(container.settingsDataStore.baseUrlOnce())
            try {
                return block(api)
            } catch (error: HttpException) {
                if (error.code() == 401 && attempt == 0) {
                    container.tokenStore.clear()
                } else {
                    throw error
                }
            }
        }
        error("request failed after re-registration")
    }
}

private fun ReminderDto.toLocal(displayedAt: Long?): LocalReminder = LocalReminder(
    id = id,
    title = title,
    description = description,
    reason = reason,
    importance = importance,
    status = status,
    remindAt = remindAt,
    displayedAt = displayedAt,
)
