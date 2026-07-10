package com.securevault.utils

import android.content.Context
import android.net.Uri
import com.securevault.data.Entry
import com.securevault.utils.CryptoUtils
import java.io.BufferedReader
import java.io.InputStreamReader

class ExportManager(private val context: Context) {

    //  Совместимый CSV с plaintext password
    fun exportToCsv(entries: List<Entry>, outputStream: java.io.OutputStream): Boolean {
        return try {
            val writer = outputStream.bufferedWriter()

            //  Заголовок совместимого формата
            writer.write("service,username,password,url,notes,text_hint,rotation_enabled,rotation_period_months")
            writer.newLine()

            for (entry in entries) {
                //  Получаем plaintext пароль
                val plainPassword = try {
                    entry.password
                } catch (e: Exception) {
                    throw Exception("Не удалось расшифровать пароль '${entry.service}'")
                }

                val row = buildString {
                    append(escapeCsv(entry.service)).append(",")
                    append(escapeCsv(entry.username)).append(",")
                    append(escapeCsv(plainPassword)).append(",")
                    append(escapeCsv(entry.url ?: "")).append(",")
                    append(escapeCsv(entry.notes ?: "")).append(",")
                    append(escapeCsv(entry.textHint ?: "")).append(",")
                    append(entry.rotationEnabled).append(",")
                    append(entry.rotationPeriodMonths)
                }
                writer.write(row)
                writer.newLine()
            }

            writer.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    //  Импорт совместимого CSV через Entry.create
    data class ImportResult(
        val entries: List<Entry>,
        val errors: List<String>,
        val hasKeystoreErrors: Boolean = false,
        val keystoreErrors: Int = 0
    )

    fun importFromCsv(uri: Uri, targetProfileId: Int, generateNewIds: Boolean = true): ImportResult {
        val entries = mutableListOf<Entry>()
        val errors = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val header = reader.readLine() ?: return@use

                //  Определяем формат по заголовку
                val isCompatibleFormat = header.contains("password") && !header.contains("encrypted_password")

                var lineNumber = 1
                reader.forEachLine { line ->
                    lineNumber++
                    if (line.isBlank()) return@forEachLine

                    try {
                        val columns = parseCsvLine(line)

                        if (isCompatibleFormat) {
                            //  Совместимый формат: plaintext password
                            if (columns.size < 2) {
                                errors.add("Строка $lineNumber: недостаточно данных")
                                return@forEachLine
                            }

                            val service = columns[0]
                            val username = columns.getOrElse(1) { "" }
                            val plainPassword = columns.getOrElse(2) { "" }
                            val url = columns.getOrElse(3) { "" }.ifBlank { null }
                            val notes = columns.getOrElse(4) { "" }.ifBlank { null }
                            val textHint = columns.getOrElse(5) { "" }.ifBlank { null }
                            val rotationEnabled = columns.getOrElse(6) { "false" }.toBoolean()
                            val rotationPeriodMonths = columns.getOrElse(7) { "6" }.toIntOrNull() ?: 6

                            if (plainPassword.isBlank()) {
                                errors.add("Строка $lineNumber: пустой пароль")
                                return@forEachLine
                            }

                            //  Используем Entry.create — пароль зашифруется на текущем устройстве
                            val entry = Entry.create(
                                service = service,
                                username = username,
                                password = plainPassword,
                                profileId = targetProfileId,
                                passwordFingerprint = PasswordValidator.buildPasswordFingerprint(plainPassword, context),
                                url = url,
                                notes = notes,
                                textHint = textHint,
                                rotationEnabled = rotationEnabled,
                                rotationPeriodMonths = rotationPeriodMonths,
                                generationType = "manual"
                            )
                            entries.add(entry)
                        } else {
                            //  Legacy формат с encrypted_password
                            errors.add("Строка $lineNumber: legacy формат не поддерживается в совместимом режиме")
                        }
                    } catch (e: Exception) {
                        errors.add("Строка $lineNumber: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Ошибка чтения файла: ${e.message}")
        }

        return ImportResult(
            entries = entries,
            errors = errors
        )
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())

        return result
    }

    fun generateExportFilename(): String {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "securevault_export_$timestamp.csv"
    }
}
