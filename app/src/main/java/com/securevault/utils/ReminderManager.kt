package com.securevault.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import com.securevault.data.Entry
import javax.inject.Inject
import javax.inject.Singleton

/**
 *  Менеджер напоминаний о смене паролей
 * Использует AlarmManager для точных уведомлений
 */
@Singleton
class ReminderManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val ACTION_REMINDER = "com.securevault.REMINDER"
        private const val EXTRA_ENTRY_ID = "entry_id"
        private const val EXTRA_ENTRY_TITLE = "entry_title"
    }
    
    /**
     *  Запланировать напоминание для записи
     * @param entry Запись, для которой нужно напомнить
     * @param daysAhead За сколько дней до истечения напомнить (по умолчанию 7)
     */
    fun scheduleReminder(entry: Entry, daysAhead: Int = 7) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_REMINDER).apply {
            putExtra(EXTRA_ENTRY_ID, entry.id)
            putExtra(EXTRA_ENTRY_TITLE, entry.service)
        }
        
        // PendingIntent для запуска при срабатывании
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.id.hashCode(), // Уникальный requestCode для каждой записи
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        //  Время срабатывания: дата последнего изменения + интервал - дней на опережение
        val triggerTime = entry.lastChanged + 
            (entry.changeIntervalDays - daysAhead).toLong() * 24 * 60 * 60 * 1000
        
        // Планируем точное уведомление
        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }
    
    /**
     *  Отменить напоминание для записи
     */
    fun cancelReminder(entryId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_REMINDER).putExtra(EXTRA_ENTRY_ID, entryId)
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entryId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
    
    /**
     * Перепланировать все напоминания (после импорта/синхронизации)
     */
    fun rescheduleAll(entries: List<Entry>) {
        entries.forEach { entry ->
            if (!entry.isPasswordExpired()) {
                scheduleReminder(entry)
            }
        }
    }
    
    /**
     *  Проверка: пора ли уже напомнить?
     */
    fun isTimeToRemind(entry: Entry, daysAhead: Int = 7): Boolean {
        val daysSinceChange = (System.currentTimeMillis() - entry.lastChanged) / (1000 * 60 * 60 * 24)
        val daysUntilExpiry = entry.changeIntervalDays - daysSinceChange
        return daysUntilExpiry <= daysAhead && daysUntilExpiry >= 0
    }
    
    /**
     *  Сколько дней осталось до истечения пароля
     */
    fun getDaysUntilExpiry(entry: Entry): Int {
        val daysSinceChange = (System.currentTimeMillis() - entry.lastChanged) / (1000 * 60 * 60 * 24)
        return entry.changeIntervalDays - daysSinceChange.toInt()
    }
}
