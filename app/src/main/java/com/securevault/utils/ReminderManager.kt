package com.securevault.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import com.securevault.data.Entry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер напоминаний о смене паролей
 * Использует AlarmManager для точных отложенных уведомлений
 */
@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        // Действие для BroadcastReceiver
        const val ACTION_REMINDER = "com.securevault.action.PASSWORD_REMINDER"
        
        // Ключи для передачи данных в Intent
        const val EXTRA_ENTRY_ID = "extra_entry_id"
        const val EXTRA_ENTRY_TITLE = "extra_entry_title"
        const val EXTRA_DAYS_LEFT = "extra_days_left"
        
        // ID канала уведомлений (должен совпадать с тем, что в PasswordReminderReceiver)
        const val NOTIFICATION_CHANNEL_ID = "password_reminders"
    }
    
    /**
     * Запланировать напоминание для записи
     * @param entry Запись, для которой нужно напомнить
     * @param daysAhead За сколько дней до истечения напомнить (по умолчанию 7)
     */
    fun scheduleReminder(entry: Entry, daysAhead: Int = 7) {
        // Если ротация не включена или дата не задана — не планируем
        if (!entry.rotationEnabled || entry.nextRotationDate == null) return
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Создаём Intent для BroadcastReceiver
        val intent = Intent(ACTION_REMINDER).apply {
            putExtra(EXTRA_ENTRY_ID, entry.id)
            putExtra(EXTRA_ENTRY_TITLE, entry.service)
            putExtra(EXTRA_DAYS_LEFT, daysAhead)
        }
        
        // PendingIntent для запуска при срабатывании будильника
        // FLAG_IMMUTABLE обязателен для Android 12+
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.id.hashCode(), // Уникальный requestCode для каждой записи
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Время срабатывания: дата следующей ротации - дней на опережение
        // Переводим дни в миллисекунды
        val triggerTime = entry.nextRotationDate!! - (daysAhead.toLong() * 24 * 60 * 60 * 1000)
        
        // Если время уже прошло — не планируем
        if (triggerTime <= System.currentTimeMillis()) return
        
        // Планируем точное уведомление, которое сработает даже в режиме сна
        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }
    
    /**
     * Отменить напоминание для записи
     * @param entryId ID записи, для которой нужно отменить напоминание
     */
    fun cancelReminder(entryId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(ACTION_REMINDER).putExtra(EXTRA_ENTRY_ID, entryId)
        
        // FLAG_NO_CREATE возвращает null, если PendingIntent не существует
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entryId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Если будильник был запланирован — отменяем его
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
    
    /**
     * Перепланировать все напоминания
     * Вызывать после импорта данных или изменения настроек ротации
     * @param entries Список всех записей из базы данных
     */
    fun rescheduleAll(entries: List<Entry>) {
        // Сначала отменяем все старые будильники (опционально, можно не делать)
        // entries.forEach { cancelReminder(it.id) }
        
        // Планируем новые для записей с активной ротацией
        entries.forEach { entry ->
            if (entry.rotationEnabled && !entry.isPasswordExpired()) {
                scheduleReminder(entry)
            }
        }
    }
    
    /**
     * Проверка: пора ли уже напомнить?
     * Используется для мгновенной проверки при открытии приложения
     * @param entry Запись для проверки
     * @param daysAhead За сколько дней до истечения считать "пора напомнить"
     * @return true если до истечения осталось от 0 до daysAhead дней
     */
    fun isTimeToRemind(entry: Entry, daysAhead: Int = 7): Boolean {
        if (!entry.rotationEnabled || entry.nextRotationDate == null) return false
        
        val daysUntilRotation = getDaysUntilRotation(entry)
        return daysUntilRotation in 0..daysAhead
    }
    
    /**
     * Сколько дней осталось до следующей ротации пароля
     * @param entry Запись для расчёта
     * @return Количество дней (может быть отрицательным, если пароль уже просрочен)
     */
    fun getDaysUntilRotation(entry: Entry): Int {
        if (entry.nextRotationDate == null) return Int.MAX_VALUE
        
        val diffMillis = entry.nextRotationDate!! - System.currentTimeMillis()
        return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    }
    
    /**
     * Создать канал уведомлений (вызвать один раз при старте приложения)
     * Обязательно для Android 8.0+
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Напоминания о паролях"
            val descriptionText = "Уведомления о необходимости смены паролей"
            val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
            
            val channel = android.app.NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                name,
                importance
            ).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(
                android.app.NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}
