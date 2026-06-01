package com.securevault.utils

import android.content.Context
import android.net.Uri
import com.securevault.data.Entry
import com.securevault.data.Profile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val CSV_HEADER = "id,service,username,encrypted_password,profile,emoji_hint,rotation_enabled,rotation_period_months,next_rotation_date,created_at,last_changed,is_favorite,failed_attempts,notes"
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
    }
    
    /**
     * Экспортирует список записей в CSV-файл
     * @param entries Список записей для экспорта
     * @param outputStream Поток для записи файла
     * @return true если экспорт успешен
     */
    fun exportToCsv(entries: List<Entry>, outputStream: OutputStream): Boolean {
        return try {
            val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
            
            // Заголовок
            writer.append(CSV_HEADER).append("\n")
            
            // Данные
            entries.forEach { entry ->
                writer.append(escapeCsv(entry.id)).append(",")
                writer.append(escapeCsv(entry.service)).append(",")
                writer.append(escapeCsv(entry.username)).append(",")
                writer.append(escapeCsv(entry.encryptedPassword)).append(",")
                writer.append(entry.profile.name).append(",")
                writer.append(escapeCsv(entry.emojiHint ?: "")).append(",")
                writer.append(if (entry.rotationEnabled) "1" else "0").append(",")
                writer.append(entry.rotationPeriodMonths.toString()).append(",")
                writer.append(entry.nextRotationDate?.toString() ?: "").append(",")
                writer.append(entry.createdAt.toString()).append(",")
                writer.append(entry.lastChanged.toString()).append(",")
                writer.append(if (entry.isFavorite) "1" else "0").append(",")
                writer.append(entry.failedAttempts.toString()).append(",")
                writer.append(escapeCsv(entry.notes ?: "")).append("\n")
            }
            
            writer.flush()
            writer.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Импортирует записи из CSV-файла
     * @param uri URI файла для импорта
     * @return Список успешно импортированных записей
     */
    fun importFromCsv(uri: Uri): List<Entry> {
        val importedEntries = mutableListOf<Entry>()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                
                // Пропускаем заголовок
                val header = reader.readLine()
                if (header != CSV_HEADER) {
                    // Файл не соответствует ожидаемому формату
                    reader.close()
                    return emptyList()
                }
                
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    try {
                        val entry = parseCsvLine(line!!, header)
                        entry?.let { importedEntries.add(it) }
                    } catch (e: Exception) {
                        // Пропускаем проблемные строки
                        continue
                    }
                }
                reader.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return importedEntries
    }
    
    /**
     * Парсит одну строку CSV в объект Entry
     */
    private fun parseCsvLine(line: String, header: String): Entry? {
        val values = parseCsvValues(line)
        if (values.size < 14) return null
        
        return Entry(
            id = values[0],
            service = values[1],
            username = values[2],
            encryptedPassword = values[3],
            profile = Profile.valueOf(values[4]),
            emojiHint = values[5].takeIf { it.isNotEmpty() },
            rotationEnabled = values[6] == "1",
            rotationPeriodMonths = values[7].toIntOrNull() ?: 6,
            nextRotationDate = values[8].toLongOrNull(),
            createdAt = values[9].toLongOrNull() ?: System.currentTimeMillis(),
            lastChanged = values[10].toLongOrNull() ?: System.currentTimeMillis(),
            isFavorite = values[11] == "1",
            failedAttempts = values[12].toIntOrNull() ?: 0,
            notes = values[13].takeIf { it.isNotEmpty() }
        )
    }
    
    /**
     * Разбивает строку CSV с учётом кавычек
     */
    private fun parseCsvValues(line: String): List<String> {
        val values = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        for (char in line) {
            when (char) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    values.add(unescapeCsv(current.toString()))
                    current = StringBuilder()
                } else {
                    current.append(char)
                }
                else -> current.append(char)
            }
        }
        values.add(unescapeCsv(current.toString()))
        
        return values
    }
    
    /**
     * Экранирует значение для CSV (добавляет кавычки при необходимости)
     */
    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
    
    /**
     * Деэкранирует значение из CSV
     */
    private fun unescapeCsv(value: String): String {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length - 1).replace("\"\"", "\"")
        }
        return value
    }
    
    /**
     * Генерирует имя файла для экспорта
     */
    fun generateExportFilename(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "securevault_export_$timestamp.csv"
    }
    
    /**
     * Фильтрует записи по профилю для экспорта
     */
    fun filterByProfile(entries: List<Entry>, profile: Profile?): List<Entry> {
        return if (profile == null) {
            entries
        } else {
            entries.filter { it.profile == profile }
        }
    }
    
    /**
     * Фильтрует просроченные записи
     */
    fun filterExpired(entries: List<Entry>): List<Entry> {
        return entries.filter { it.isPasswordExpired() }
    }
    
    /**
     * Фильтрует записи, требующие ротации в ближайшие N дней
     */
    fun filterUpcomingRotation(entries: List<Entry>, daysAhead: Int = 7): List<Entry> {
        return entries.filter { entry ->
            entry.rotationEnabled && 
            entry.nextRotationDate != null && 
            entry.getDaysUntilExpiry() in 0..daysAhead
        }
    }
}
