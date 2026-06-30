package com.securevault.utils

import android.content.Context
import android.net.Uri
import com.securevault.data.Entry
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
        private const val CSV_HEADER = "id,service,username,encrypted_password,profile_id,url,notes,is_favorite,text_hint,rotation_enabled,rotation_period_months,next_rotation_date,created_at,last_changed,password_history_json"
    }

    fun exportToCsv(entries: List<Entry>, outputStream: OutputStream): Boolean {
        return try {
            val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
            writer.append(CSV_HEADER).append("\n")
            
            entries.forEach { entry ->
                writer.append(escapeCsv(entry.id)).append(",")
                writer.append(escapeCsv(entry.service)).append(",")
                writer.append(escapeCsv(entry.username)).append(",")
                writer.append(escapeCsv(entry.encryptedPassword)).append(",")
                writer.append(entry.profileId.toString()).append(",")
                writer.append(escapeCsv(entry.url ?: "")).append(",")
                writer.append(escapeCsv(entry.notes ?: "")).append(",")
                writer.append(if (entry.isFavorite) "1" else "0").append(",")
                writer.append(escapeCsv(entry.textHint ?: "")).append(",")
                writer.append(if (entry.rotationEnabled) "1" else "0").append(",")
                writer.append(entry.rotationPeriodMonths.toString()).append(",")
                writer.append(entry.nextRotationDate?.toString() ?: "").append(",")
                writer.append(entry.createdAt.toString()).append(",")
                writer.append(entry.lastChanged.toString()).append(",")
                writer.append(escapeCsv(entry.passwordHistoryJson ?: "")).append("\n")
            }
            
            writer.flush()
            writer.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importFromCsv(uri: Uri, defaultProfileId: Int): List<Entry> {
        val importedEntries = mutableListOf<Entry>()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val header = reader.readLine()
                if (header != CSV_HEADER) {
                    reader.close()
                    return emptyList()
                }
                
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    try {
                        val entry = parseCsvLine(line!!, defaultProfileId)
                        entry?.let { importedEntries.add(it) }
                    } catch (e: Exception) {
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

    private fun parseCsvLine(line: String, defaultProfileId: Int): Entry? {
        val values = parseCsvValues(line)
        if (values.size < 15) return null

        return Entry(
            id = values[0],
            service = values[1],
            username = values[2],
            encryptedPassword = values[3],
            profileId = values[4].toIntOrNull() ?: defaultProfileId,
            url = values[5].takeIf { it.isNotEmpty() },
            notes = values[6].takeIf { it.isNotEmpty() },
            isFavorite = values[7] == "1",
            textHint = values[8].takeIf { it.isNotEmpty() },
            rotationEnabled = values[9] == "1",
            rotationPeriodMonths = values[10].toIntOrNull() ?: 6,
            nextRotationDate = values[11].toLongOrNull(),
            createdAt = values[12].toLongOrNull() ?: System.currentTimeMillis(),
            lastChanged = values[13].toLongOrNull() ?: System.currentTimeMillis(),
            passwordHistoryJson = values[14].takeIf { it.isNotEmpty() }
        )
    }

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

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }

    private fun unescapeCsv(value: String): String {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length - 1).replace("\"\"", "\"")
        }
        return value
    }

    fun generateExportFilename(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "securevault_export_$timestamp.csv"
    }

    fun filterByProfile(entries: List<Entry>, profileId: Int?): List<Entry> {
        return if (profileId == null) entries else entries.filter { it.profileId == profileId }
    }

    fun filterExpired(entries: List<Entry>): List<Entry> {
        return entries.filter { it.isPasswordExpired() }
    }

    fun filterUpcomingRotation(entries: List<Entry>, daysAhead: Int = 7): List<Entry> {
        return entries.filter { entry ->
            entry.rotationEnabled && 
            entry.nextRotationDate != null && 
            entry.getDaysUntilRotation() in 0..daysAhead
        }
    }
}
