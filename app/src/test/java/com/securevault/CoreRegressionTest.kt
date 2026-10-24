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

        for (password in listOf("password", "Пароль", "")) {
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
    fun ampgRemainsDeterministicWithCaseSensitiveUniqueness() {
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
            assertTrue(MnemonicPasswordGenerator.validatePassword(variant.password, options.splitMode))
            assertEquals(16, variant.password.length)
            assertEquals(
                16,
                variant.password.toSet().size
            )
        }
    }

    @Test
    fun ampgBothHalvesRespectQuotasAndDoNotRepeatExactCharacters() {
        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = "Сегодня стояла чудесная погода",
            phrase2 = "Ветер качает листья березы",
            targetLength = 24,
            splitMode = MnemonicPasswordGenerator.SplitMode.TWO_USERS
        )
        val variants = MnemonicPasswordGenerator.generateVariantsResult(options).getOrThrow()
        assertTrue(variants.isNotEmpty())
        variants.forEach { result ->
            assertEquals(24, result.password.length)
            assertEquals(24, result.password.toSet().size)
            assertEquals(result.password, result.part1!! + result.part2!!)
            listOf(result.part1, result.part2).forEach { part ->
                assertEquals(12, part!!.length)
                assertTrue(part.count(Char::isLowerCase) >= 2)
                assertTrue(part.count(Char::isUpperCase) >= 2)
                assertTrue(part.count { !it.isLetterOrDigit() } >= 2)
            }
            val reproduced = MnemonicPasswordGenerator.generateVariantsResult(
                options.copy(variantOffset = result.variantOffset), 1
            ).getOrThrow().single()
            assertEquals(result.password, reproduced.password)
        }
    }

    @Test
    fun ampgMarkersAreOptionalAndFitSelectedLength() {
        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = "Дикий ветер качает березы", serviceName = "Gmail", targetLength = 24,
            addServiceMarker = true, addYearMarker = true, year = 2026
        )
        val variants = MnemonicPasswordGenerator.generateVariantsResult(options).getOrThrow()
        assertTrue(variants.any { it.explanation.contains("Сервис: Gl.") && it.explanation.contains("Год: 26.") })
        variants.forEach { result ->
            assertEquals(24, result.password.length)
            assertEquals(24, result.password.toSet().size)
            assertTrue(MnemonicPasswordGenerator.validatePassword(result.password, options.splitMode))
        }
        MnemonicPasswordGenerator.generateVariantsResult(options.copy(year = 2022)).getOrThrow().forEach {
            assertEquals(24, it.password.length)
            assertEquals(24, it.password.toSet().size)
            assertFalse(it.explanation.contains("Год: 22."))
        }
        val pair = "AaBb!@cdEF"
        assertTrue(PasswordValidator.validateUniqueCharacters(pair, "ampg_v3_0").isValid)
        assertFalse(PasswordValidator.validateUniqueCharacters(pair, "random").isValid)
        assertFalse(PasswordValidator.validateUniqueCharacters(pair + "!", "ampg_v3_0").isValid)
    }

    @Test
    fun passwordScreenshotsAndRecentAppPreviewsHaveSeparateRules() {
        val policy = com.securevault.security.ScreenPrivacyPolicy
        assertEquals(com.securevault.security.ScreenPrivacyPolicy.Decision(false, true),
            policy.decide(true, true, true, false))
        assertEquals(com.securevault.security.ScreenPrivacyPolicy.Decision(true, true),
            policy.decide(true, false, true, false))
        for (allow in listOf(false, true)) {
            assertEquals(com.securevault.security.ScreenPrivacyPolicy.Decision(true, true),
                policy.decide(true, allow, false, true))
            assertEquals(com.securevault.security.ScreenPrivacyPolicy.Decision(true, true),
                policy.decide(false, allow, true, true))
            assertEquals(com.securevault.security.ScreenPrivacyPolicy.Decision(false, false),
                policy.decide(false, allow, true, false))
        }
    }

    @Test
    fun ampgExampleHasExactlyTheExplainedPassword() {
        val options = MnemonicPasswordGenerator.GenerationOptions("Летом в горах шумели кедры", targetLength = 16)
        val preview = MnemonicPasswordGenerator.preparePhrase(options.phrase).getOrThrow()
        assertEquals(listOf("летом", "горах", "шумели", "кедры"), preview.words)
        assertEquals(listOf("Letom", "Gorakh", "Shumeli", "Kedry"), preview.transliteratedWords)
        assertEquals(listOf("в"), preview.skippedWords)
        val first = MnemonicPasswordGenerator.generateVariantsResult(options).getOrThrow().first()
        assertEquals("Le+oG0r@ShumK3dR", first.password)
        assertEquals(listOf(4), first.prefixLengths)
        assertEquals(16, first.password.toSet().size)
        assertEquals(7, first.password.count(Char::isLowerCase))
        assertEquals(5, first.password.count(Char::isUpperCase))
        assertEquals(2, first.password.count { !it.isLetterOrDigit() })
    }

    @Test
    fun ampgSkipsFunctionWordsAndExplainsUnusablePhrases() {
        val preview = MnemonicPasswordGenerator.preparePhrase("Я был в лесу, и мы нашли грибы 2026!").getOrThrow()
        assertEquals(listOf("лесу", "нашли", "грибы"), preview.words)
        assertEquals(listOf("я", "был", "в", "и", "мы", "2026"), preview.skippedWords)
        val result = MnemonicPasswordGenerator.generateVariantsResult(
            MnemonicPasswordGenerator.GenerationOptions("Я был в лесу, и мы нашли грибы 2026!", targetLength = 16)
        ).getOrThrow()
        assertTrue(result.all { it.password.length == 16 && MnemonicPasswordGenerator.validatePassword(it.password, it.splitMode) })
        assertEquals(listOf("кот", "сидел", "высокой", "сосной"),
            MnemonicPasswordGenerator.preparePhrase("Наш кот сидел под этой высокой сосной").getOrThrow().words)
        assertTrue(MnemonicPasswordGenerator.preparePhrase("я и мы в").isFailure)
        val repeated = MnemonicPasswordGenerator.generateVariantsResult(
            MnemonicPasswordGenerator.GenerationOptions("Дом дача дуб", targetLength = 16)
        )
        assertTrue(repeated.isFailure)
        assertTrue(repeated.exceptionOrNull()!!.message!!.contains("заглавных"))
        assertTrue(MnemonicPasswordGenerator.generateVariantsResult(
            MnemonicPasswordGenerator.GenerationOptions("Летом в горах шумели кедры", phrase2 = "Ранним утром ветер будил птиц",
                targetLength = 17, splitMode = MnemonicPasswordGenerator.SplitMode.TWO_USERS)).isFailure)
    }

    @Test
    fun ampgSixteenIsReachableDespiteFloatRounding() {
        val single = MnemonicPasswordGenerator.SplitMode.SINGLE_USER
        val two = MnemonicPasswordGenerator.SplitMode.TWO_USERS
        assertEquals(16, MnemonicPasswordGenerator.normalizeLength(15.999999f, single))
        assertEquals(16, MnemonicPasswordGenerator.normalizeLength(16.000002f, single))
        assertEquals(16, MnemonicPasswordGenerator.normalizeLength(16f, two))
        for (length in 12..56) assertEquals(length, MnemonicPasswordGenerator.normalizeLength(length.toFloat(), single))
        for (length in 16..56 step 2) assertEquals(length, MnemonicPasswordGenerator.normalizeLength(length.toFloat(), two))
    }

    @Test
    fun privacyReturnRouteContainsNoOpenPassword() {
        val policy = com.securevault.security.ScreenPrivacyPolicy
        assertEquals("vault/7", policy.safeReturnRoute("entry/{entryId}?profileId={profileId}", 7))
        assertEquals("vault/7", policy.safeReturnRoute("vault/{profileId}", 7))
        assertEquals("vault/7", policy.safeReturnRoute("mnemonic_generator/{profileId}", 7))
        assertEquals("rotation_journal/7", policy.safeReturnRoute("rotation_journal/{profileId}", 7))
        assertEquals("settings", policy.safeReturnRoute("change_password", null))
        assertEquals("profiles", policy.safeReturnRoute("entry/{entryId}", null))
    }
}
