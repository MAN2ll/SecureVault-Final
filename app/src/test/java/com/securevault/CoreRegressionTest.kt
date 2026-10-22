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

    @Test
    fun twoPartsAlwaysRespectBothQuotas() {
        for (length in listOf(16, 18, 20)) {
            repeat(1000) {
                val password = PasswordGenerator.generateTwoPart(
                    length,
                    true,
                    true,
                    true,
                    true
                ).getOrThrow().password

                assertEquals(length, password.length)
                assertEquals(length, password.lowercase().toSet().size)

                password.chunked(length / 2).forEach { part ->
                    assertTrue(part.count(Char::isLowerCase) >= 2)
                    assertTrue(part.count(Char::isUpperCase) >= 2)
                    assertTrue(part.count(Char::isDigit) >= 2)
                    assertTrue(part.count { !it.isLetterOrDigit() } >= 2)
                }
            }
        }
    }

    @Test
    fun randomRespectsEveryCategorySelection() {
        for (mask in 1..15) {
            repeat(100) {
                val flags = (0..3).map { index ->
                    mask and (1 shl index) != 0
                }

                val password = PasswordGenerator.generate(
                    8,
                    flags[0],
                    flags[1],
                    flags[2],
                    flags[3]
                ).getOrThrow().password

                assertEquals(8, password.length)
                assertEquals(8, password.lowercase().toSet().size)
                assertEquals(flags[0], password.any(Char::isLowerCase))
                assertEquals(flags[1], password.any(Char::isUpperCase))
                assertEquals(flags[2], password.any(Char::isDigit))
                assertEquals(
                    flags[3],
                    password.any { !it.isLetterOrDigit() }
                )
            }
        }

        assertTrue(
            PasswordGenerator.generate(
                8,
                false,
                false,
                false,
                false
            ).isFailure
        )

        assertTrue(
            PasswordGenerator.generateTwoPart(
                15,
                true,
                true,
                true,
                true
            ).isFailure
        )
    }

    @Test
    fun anchorRejectsOversizeAndHonorsComposition() {
        assertTrue(
            PasswordGenerator.generateWithAnchor(
                "щцжфюяыэбгдк",
                12,
                true,
                true,
                true,
                true
            ).isFailure
        )

        repeat(200) {
            val password = PasswordGenerator.generateWithAnchor(
                "кот",
                16,
                true,
                true,
                true,
                true
            ).getOrThrow().password

            assertEquals(16, password.length)
            assertEquals(16, password.lowercase().toSet().size)
            assertTrue(password.any(Char::isUpperCase))
            assertTrue(password.any(Char::isLowerCase))
            assertTrue(password.any(Char::isDigit))
            assertTrue(password.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun csvPreservesQuotesNewlinesAndEmptyFields() {
        val rows = listOf(
            listOf("service", "notes", "tags_csv", "empty"),
            listOf(
                "Сервис, 1",
                "Он сказал: \"да\"\r\nВторая строка\nТретья",
                "Работа, почта",
                ""
            ),
            listOf("", "", "", "")
        )

        val encoded = "\uFEFF" +
            rows.joinToString("\r\n") { CsvCodec.encode(it) } +
            "\r\n"

        assertEquals(rows, CsvCodec.decode(encoded))

        assertTrue(
            runCatching {
                CsvCodec.decode("a,\"unfinished")
            }.isFailure
        )
    }

    @Test
    fun historyUsesObjectsAndReadsBrokenPreviousFormat() {
        val item = PasswordHistoryItem(
            encryptedOldPassword = "cipher==",
            encryptedNewPassword = "new==",
            date = 1234,
            type = "manual",
            passwordFingerprint = "fingerprint",
            relatedService = "Сервис, имя",
            hint = "Текст, с запятой"
        )

        val json = JsonUtils.toJson(listOf(item))

        assertTrue(JSONArray(json).get(0) is org.json.JSONObject)
        assertEquals(listOf(item), JsonUtils.readHistory(json))

        val previousFormat = JSONArray()
            .put(item.toString())
            .toString()

        assertEquals(
            listOf(item),
            JsonUtils.readHistory(previousFormat)
        )

        assertEquals(
            1234L,
            JsonUtils.readHistory("hash:1234:manual").single().date
        )

        assertTrue(
            runCatching {
                JsonUtils.readHistory("[broken]")
            }.isFailure
        )
    }

    @Test
    fun accessRequiresOriginalOwnerAndFailsClosed() {
        val profile = Profile(
            id = 2,
            name = "Второй",
            passwordHash = "hash",
            passwordSalt = "salt",
            passwordAccessMode = "no_confirmation"
        )

        val entry = Entry(
            service = "test",
            username = "",
            encryptedPassword = "",
            profileId = 2
        )

        for (mode in listOf(
            "PIN_REQUIRED",
            "pin_required",
            "PiN_ReQuIrEd"
        )) {
            assertSame(
                AccessResult.PinRequired,
                resolveAccess(
                    entry.copy(passwordAccessMode = mode),
                    profile
                )
            )
        }

        assertSame(
            AccessResult.PinRequired,
            resolveAccess(
                entry.copy(passwordAccessMode = "unknown"),
                profile
            )
        )

        assertSame(
            AccessResult.Granted,
            resolveAccess(entry, profile)
        )

        assertSame(
            AccessResult.PinRequired,
            resolveProfileAccess(
                profile.copy(profileAccessMode = "unknown")
            )
        )

        assertTrue(
            runCatching {
                resolveAccess(entry, profile.copy(id = 1))
            }.isFailure
        )
    }

    @Test
    fun rotationChangesOnlyForRelevantEdits() {
        val entry = Entry(
            service = "test",
            username = "",
            encryptedPassword = "",
            profileId = 1,
            rotationEnabled = true,
            nextRotationDate = 9999
        )

        assertEquals(
            9999L,
            EntryChanges.nextRotationDate(
                entry,
                true,
                6,
                false,
                1234
            )
        )

        assertNull(
            EntryChanges.nextRotationDate(
                entry,
                false,
                6,
                false,
                1234
            )
        )

        assertEquals(
            1234 + 6 * 30L * 86400000,
            EntryChanges.nextRotationDate(
                entry,
                true,
                6,
                true,
                1234
            )
        )

        assertEquals(
            1234 + 3 * 30L * 86400000,
            EntryChanges.nextRotationDate(
                entry,
                true,
                3,
                false,
                1234
            )
        )
    }

    @Test
    fun androidSevenKdfMatchesPlatformIncludingUnicode() {
        val salt = ByteArray(16) { it.toByte() }

        for (password in listOf("password", "Пароль🙂", "")) {
            for (iterations in listOf(1, 2, 1000)) {
                val reference = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(
                        PBEKeySpec(
                            password.toCharArray(),
                            salt,
                            iterations,
                            256
                        )
                    )
                    .encoded

                assertArrayEquals(
                    reference,
                    PasswordKdf.deriveCompatible(
                        password,
                        salt,
                        iterations
                    )
                )
            }
        }
    }

    @Test
    fun ampgIsUnchangedAndDeterministic() {
        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = "метроном жёлтый камень",
            targetLength = 16,
            serviceName = "Gmail",
            addServiceMarker = true
        )

        val first = MnemonicPasswordGenerator.generateVariants(options)

        assertTrue(first.isNotEmpty())

        assertEquals(
            first,
            MnemonicPasswordGenerator.generateVariants(options)
        )

        first.forEach { variant ->
            assertEquals(16, variant.password.length)
            assertEquals(
                16,
                variant.password.lowercase().toSet().size
            )
        }
    }
}
