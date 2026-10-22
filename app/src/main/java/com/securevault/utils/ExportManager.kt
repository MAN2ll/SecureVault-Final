package com.securevault.utils

import android.content.Context
import android.net.Uri
import com.securevault.data.Entry
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExportManager(private val context: Context) {
    private val columns = listOf("service", "username", "password", "url", "notes", "text_hint",
        "rotation_enabled", "rotation_period_months", "tags_csv", "is_favorite", "generation_type",
        "mnemonic_phrase_hint", "mnemonic_options_json", "password_access_mode", "id", "next_rotation_date")
    fun exportToCsv(entries: List<Entry>, outputStream: OutputStream): Boolean = try {
        val writer = outputStream.bufferedWriter(Charsets.UTF_8)
        writer.write(CsvCodec.encode(columns)); writer.write("\r\n")
        for (entry in entries) {
            writer.write(CsvCodec.encode(listOf(entry.service, entry.username, entry.password,
                entry.url.orEmpty(), entry.notes.orEmpty(), entry.textHint.orEmpty(),
                entry.rotationEnabled.toString(), entry.rotationPeriodMonths.toString(), entry.tagsCsv,
                entry.isFavorite.toString(), entry.generationType, entry.mnemonicPhraseHint.orEmpty(),
                entry.mnemonicOptionsJson.orEmpty(), entry.passwordAccessMode, entry.id,
                entry.nextRotationDate?.toString().orEmpty())))
            writer.write("\r\n")
        }
        writer.flush(); true
    } catch (_: Exception) { false }
    data class ImportResult(val entries: List<Entry>, val errors: List<String>,
        val hasKeystoreErrors: Boolean = false, val keystoreErrors: Int = 0)
    fun importFromCsv(uri: Uri, targetProfileId: Int, generateNewIds: Boolean = true): ImportResult = try {
        requireNotNull(context.contentResolver.openInputStream(uri)) { "Не удалось открыть CSV" }.use {
            importFromCsv(it, targetProfileId, generateNewIds)
        }
    } catch (e: Exception) { ImportResult(emptyList(), listOf(e.message ?: "Ошибка чтения CSV")) }
    fun importFromCsv(input: InputStream, targetProfileId: Int, generateNewIds: Boolean = true): ImportResult {
        val entries = mutableListOf<Entry>()
        val errors = mutableListOf<String>()
        var keystoreErrors = 0
        try {
            require(targetProfileId > 0) { "Выберите профиль для импорта" }
            val rows = CsvCodec.decode(input.bufferedReader(Charsets.UTF_8).readText())
            require(rows.isNotEmpty()) { "CSV пуст" }
            val header = rows.first().map { it.trim().lowercase(Locale.ROOT) }
            require("service" in header && ("password" in header || "encrypted_password" in header)) { "Нужны столбцы service и password либо encrypted_password" }
            for ((index, row) in rows.drop(1).withIndex()) {
                if (row.all { it.isEmpty() }) continue
                try {
                    require(row.size == header.size) { "Количество полей не совпадает с заголовком" }
                    fun value(key: String) = row.getOrNull(header.indexOf(key)).orEmpty()
                    fun optional(key: String) = value(key).ifBlank { null }
                    val pwd = if ("password" in header) value("password") else try {
                        CryptoUtils.decrypt(value("encrypted_password"))
                    } catch (e: Exception) {
                        keystoreErrors++
                        throw IllegalArgumentException("Старый зашифрованный CSV требует исходный ключ этой установки приложения", e)
                    }
                    fun boolean(key: String) = value(key) == "1" || value(key).equals("true", true)
                    require(pwd.isNotBlank() && value("service").isNotBlank()) { "Пустой сервис или пароль" }
                    val months = optional("rotation_period_months")?.toInt() ?: 6
                    require(months in 1..120) { "Некорректный период ротации" }
                    val entry = Entry.create(value("service"), value("username"), pwd, targetProfileId,
                        PasswordValidator.buildPasswordFingerprint(pwd, context), optional("url"), optional("notes"),
                        optional("text_hint"), boolean("rotation_enabled"), months,
                        boolean("is_favorite"), optional("generation_type") ?: "manual",
                        optional("mnemonic_phrase_hint"), optional("mnemonic_options_json"),
                        optional("password_access_mode") ?: AccessMode.INHERIT.value, value("tags_csv"))
                    entries.add(entry.copy(id = if (generateNewIds) entry.id else optional("id") ?: entry.id,
                        nextRotationDate = if (entry.rotationEnabled && "next_rotation_date" in header)
                            optional("next_rotation_date")?.toLong() else entry.nextRotationDate,
                        createdAt = optional("created_at")?.toLong() ?: entry.createdAt,
                        lastChanged = optional("last_changed")?.toLong() ?: entry.lastChanged,
                        passwordHistoryJson = optional("password_history_json")))
                } catch (e: Exception) { errors.add("Запись ${index + 2}: ${e.message}") }
            }
        } catch (e: Exception) { errors.add(e.message ?: "Ошибка CSV") }
        return ImportResult(entries, errors, keystoreErrors > 0, keystoreErrors)
    }
    fun generateExportFilename(): String = "securevault_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())}.csv"
}
