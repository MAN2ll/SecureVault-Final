package com.securevault

import com.securevault.data.Entry
import com.securevault.data.PasswordHistoryItem
import com.securevault.data.Profile
import com.securevault.security.PasswordKdf
import com.securevault.utils.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class CoreRegressionTest {
    @Test fun twoPartsAlwaysRespectBothQuotas() {
        for (length in listOf(16, 18, 20)) repeat(1000) {
            val pwd = PasswordGenerator.generateTwoPart(length, true, true, true, true).getOrThrow().password
            assertEquals(length, pwd.length)
            assertEquals(length, pwd.lowercase().toSet().size)
            pwd.chunked(length / 2).forEach { part ->
                assertTrue(part.count(Char::isLowerCase) >= 2)
                assertTrue(part.count(Char::isUpperCase) >= 2)
                assertTrue(part.count(Char::isDigit) >= 2)
                assertTrue(part.count { !it.isLetterOrDigit() } >= 2)
            }
        }
    }
    @Test fun randomRespectsEveryCategorySelection() {
        for (mask in 1..15) repeat(100) {
            val flags = (0..3).map { mask and (1 shl it) != 0 }
            val pwd = PasswordGenerator.generate(8, flags[0], flags[1], flags[2], flags[3]).getOrThrow().password
            assertEquals(8, pwd.length); assertEquals(8, pwd.lowercase().toSet().size)
            assertEquals(flags[0], pwd.any(Char::isLowerCase))
            assertEquals(flags[1], pwd.any(Char::isUpperCase))
            assertEquals(flags[2], pwd.any(Char::isDigit))
            assertEquals(flags[3], pwd.any { !it.isLetterOrDigit() })
        }
        assertTrue(PasswordGenerator.generate(8, false, false, false, false).isFailure)
        assertTrue(PasswordGenerator.generateTwoPart(15, true, true, true, true).isFailure)
    }
    @Test fun anchorRejectsOversizeAndHonorsComposition() {
        assertTrue(PasswordGenerator.generateWithAnchor("щцжфюяыэбгдк", 12, true, true, true, true).isFailure)
        repeat(200) {
            val pwd = PasswordGenerator.generateWithAnchor("кот", 16, true, true, true, true).getOrThrow().password
            assertEquals(16, pwd.length); assertEquals(16, pwd.lowercase().toSet().size)
            assertTrue(pwd.any(Char::isUpperCase) && pwd.any(Char::isLowerCase) && pwd.any(Char::isDigit) && pwd.any { !it.isLetterOrDigit() })
        }
    }
    @Test fun csvPreservesQuotesNewlinesAndEmptyFields() {
        val rows = listOf(listOf("service", "notes", "tags_csv", "empty"),
            listOf("Сервис, 1", "Он сказал: \"да\"\r\nВторая строка\nТретья", "Работа, почта", ""),
            listOf("", "", "", ""))
        assertEquals(rows, CsvCodec.decode("\uFEFF" + rows.joinToString("\r\n") { CsvCodec.encode(it) } + "\r\n"))
        assertTrue(runCatching { CsvCodec.decode("a,\"unfinished") }.isFailure)
    }
    @Test fun historyUsesObjectsAndReadsBrokenPreviousFormat() {
        val item = PasswordHistoryItem(encryptedOldPassword = "cipher==", encryptedNewPassword = "new==",
            date = 1234, type = "manual", passwordFingerprint = "fingerprint", relatedService = "Сервис, имя", hint = "Текст, с запятой")
        val json = JsonUtils.toJson(listOf(item))
        assertTrue(JSONArray(json).get(0) is org.json.JSONObject)
        assertEquals(listOf(item), JsonUtils.readHistory(json))
        assertEquals(listOf(item), JsonUtils.readHistory(JSONArray().put(item.toString()).toString()))
        assertEquals(1234L, JsonUtils.readHistory("hash:1234:manual").single().date)
        assertTrue(runCatching { JsonUtils.readHistory("[broken]") }.isFailure)
    }
    @Test fun accessRequiresOriginalOwnerAndFailsClosed() {
        val profile = Profile(2, "Второй", "hash", "salt", passwordAccessMode = "no_confirmation")
        val base = Entry(service = "test", username = "", encryptedPassword = "", profileId = 2)
        for (raw in listOf("PIN_REQUIRED", "pin_required", "PiN_ReQuIrEd")) {
            assertSame(AccessResult.PinRequired, resolveAccess(base.copy(passwordAccessMode = raw), profile))
        }
        assertSame(AccessResult.PinRequired, resolveAccess(base.copy(passwordAccessMode = "unknown"), profile))
        assertSame(AccessResult.Granted, resolveAccess(base, profile))
        assertSame(AccessResult.PinRequired, resolveProfileAccess(profile.copy(profileAccessMode = "unknown")))
        assertTrue(runCatching { resolveAccess(base, profile.copy(id = 1)) }.isFailure)
    }
    @Test fun rotationChangesOnlyForRelevantEdits() {
        val entry = Entry(service = "test", username = "", encryptedPassword = "", profileId = 1,
            rotationEnabled = true, nextRotationDate = 9999)
        assertEquals(9999L, EntryChanges.nextRotationDate(entry, true, 6, false, 1234))
        assertNull(EntryChanges.nextRotationDate(entry, false, 6, false, 1234))
        assertEquals(1234 + 6 * 30L * 86400000, EntryChanges.nextRotationDate(entry, true, 6, true, 1234))
        assertEquals(1234 + 3 * 30L * 86400000, EntryChanges.nextRotationDate(entry, true, 3, false, 1234))
    }
    @Test fun androidSevenKdfMatchesPlatformIncludingUnicode() {
        val salt = ByteArray(16) { it.toByte() }
        for (password in listOf("password", "Пароль", "")) for (iterations in listOf(1, 2, 1000)) {
            val reference = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, 256)).encoded
            assertArrayEquals(reference, PasswordKdf.deriveCompatible(password, salt, iterations))
        }
    }
    @Test fun ampgIsUnchangedAndDeterministic() {
        val options = MnemonicPasswordGenerator.GenerationOptions(phrase = "метроном жёлтый камень",
            targetLength = 16, serviceName = "Gmail", addServiceMarker = true)
        val first = MnemonicPasswordGenerator.generateVariants(options)
        assertTrue(first.isNotEmpty()); assertEquals(first, MnemonicPasswordGenerator.generateVariants(options))
        first.forEach { assertEquals(16, it.password.length); assertEquals(16, it.password.lowercase().toSet().size) }
    }
}
