package com.securevault.utils

import android.content.Context
import android.net.Uri
import com.securevault.data.Entry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class ExportManager(private val context: Context) {

    companion object {
        // ✅ CSV v2: добавлены новые поля
        private const val CSV_HEADER = "id,service,username,encrypted_password,profile_id,url,notes,is_favorite,text_hint,rotation_enabled,rotation_period_months,next_rotation_date,created_at,last_changed,password_history_json,generation_type,password_fingerprint,mnemonic_phrase_hint,mnemonic_options_json"
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
                writer.append(escapeCsv(entry.passwordHistoryJson ?: "")).append(",")
                writer.append(escapeCsv(entry.generationType)).append(",")
                // ✅ Новые поля
                writer.append(escapeCsv(entry.passwordFingerprint ?: "")).append(",")
                writer.append(escapeCsv(entry.mnemonicPhraseHint ?: "")).append(",")
                writer.append(escapeCsv(entry.mnemonicOptionsJson ?: "")).append("\n")
            }

            writer.flush()
            writer.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importFromCsv(uri: Uri, defaultProfileId: Int, generateNewIds: Boolean = true): ImportResult {
        val importedEntries = mutableListOf<Entry>()
        val errors = mutableListOf<String>()
        var keystoreErrors = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val header = reader.readLine()
                if (header == null || !header.startsWith("id,service,username")) {
                    reader.close()
                    return ImportResult(emptyList(), listOf("Неверный формат CSV-файла"), 0)
                }

                val lines = readAllCsvRecords(reader)
                for ((index, line) in lines.withIndex()) {
                    try {
                        val entry = parseCsvLine(line, defaultProfileId, generateNewIds)
                        if (entry != null) {
                            try {
                                @Suppress("UNUSED_EXPRESSION")
                                entry.password
                                importedEntries.add(entry)
                            } catch (e: Exception) {
                                keystoreErrors++
                                errors.add("Строка ${index + 2}: пароль нельзя расшифровать на этом устройстве")
                            }
                        } else {
                            errors.add("Строка ${index + 2}: некорректный формат")
                        }
                    } catch (e: Exception) {
                        errors.add("Строка ${index + 2}: ${e.message}")
                    }
                }
                reader.close()
            }
        } catch (e: Exception) {
            errors.add("Ошибка чтения файла: ${e.message}")
        }

        return ImportResult(importedEntries, errors, keystoreErrors)
    }

    data class ImportResult(
        val entries: List<Entry>,
        val errors: List<String>,
        val keystoreErrors: Int
    ) {
        val hasKeystoreErrors: Boolean get() = keystoreErrors > 0
    }

    private fun readAllCsvRecords(reader: BufferedReader): List<String> {
        val records = mutableListOf<String>()
        val currentRecord = StringBuilder()
        var inQuotes = false

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line!!

            if (currentRecord.isNotEmpty()) {
                currentRecord.append("\n")
            }
            currentRecord.append(currentLine)

            var i = 0
            while (i < currentLine.length) {
                val char = currentLine[i]
                if (char == '"') {
                    if (i + 1 < currentLine.length && currentLine[i + 1] == '"') {
                        i += 2
                        continue
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                i++
            }

            if (!inQuotes) {
                records.add(currentRecord.toString())
                currentRecord.clear()
            }
        }

        if (currentRecord.isNotEmpty()) {
            records.add(currentRecord.toString())
        }

        return records
    }

    private fun parseCsvLine(line: String, defaultProfileId: Int, generateNewIds: Boolean): Entry? {
        val values = parseCsvValues(line)
        if (values.size < 15) return null

        val targetProfileId = defaultProfileId

        // ✅ Новые поля (если есть)
        val passwordFingerprint = if (values.size > 16) values[16].takeIf { it.isNotEmpty() } else null
        val mnemonicPhraseHint = if (values.size > 17) values[17].takeIf { it.isNotEmpty() } else null
        val mnemonicOptionsJson = if (values.size > 18) values[18].takeIf { it.isNotEmpty() } else null

        return if (generateNewIds) {
            Entry.createWithNewId(
                service = values[1],
                username = values[2],
                encryptedPassword = values[3],
                profileId = targetProfileId,
                url = values[5].takeIf { it.isNotEmpty() },
                notes = values[6].takeIf { it.isNotEmpty() },
                isFavorite = values[7] == "1",
                textHint = values[8].takeIf { it.isNotEmpty() },
                rotationEnabled = values[9] == "1",
                rotationPeriodMonths = values[10].toIntOrNull() ?: 6,
                nextRotationDate = values[11].toLongOrNull(),
                createdAt = values[12].toLongOrNull() ?: System.currentTimeMillis(),
                lastChanged = values[13].toLongOrNull() ?: System.currentTimeMillis(),
                passwordHistoryJson = values[14].takeIf { it.isNotEmpty() },
                generationType = if (values.size > 15) values[15] else "random",
                passwordFingerprint = passwordFingerprint,
                mnemonicPhraseHint = mnemonicPhraseHint,
                mnemonicOptionsJson = mnemonicOptionsJson
            )
        } else {
            Entry(
                id = values[0],
                service = values[1],
                username = values[2],
                encryptedPassword = values[3],
                profileId = targetProfileId,
                url = values[5].takeIf { it.isNotEmpty() },
                notes = values[6].takeIf { it.isNotEmpty() },
                isFavorite = values[7] == "1",
                textHint = values[8].takeIf { it.isNotEmpty() },
                rotationEnabled = values[9] == "1",
                rotationPeriodMonths = values[10].toIntOrNull() ?: 6,
                nextRotationDate = values[11].toLongOrNull(),
                createdAt = values[12].toLongOrNull() ?: System.currentTimeMillis(),
                lastChanged = values[13].toLongOrNull() ?: System.currentTimeMillis(),
                passwordHistoryJson = values[14].takeIf { it.isNotEmpty() },
                generationType = if (values.size > 15) values[15] else "random",
                passwordFingerprint = passwordFingerprint,
                mnemonicPhraseHint = mnemonicPhraseHint,
                mnemonicOptionsJson = mnemonicOptionsJson
            )
        }
    }

    private fun parseCsvValues(line: String): List<String> {
        val values = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && !inQuotes -> {
                    inQuotes = true
                }
                char == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                char == ',' && !inQuotes -> {
                    values.add(current.toString())
                    current = StringBuilder()
                }
                else -> {
                    current.append(char)
                }
            }
            i++
        }
        values.add(current.toString())

        return values
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }

    fun generateExportFilename(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "securevault_export_$timestamp.csv"
    }
}
