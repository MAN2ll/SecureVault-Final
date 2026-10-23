package com.securevault.utils

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

object MnemonicPasswordGenerator {
    const val ALGORITHM_VERSION = 3
    enum class SplitMode { SINGLE_USER, TWO_USERS }
    data class GenerationOptions(
        val phrase: String, val phrase2: String? = null, val serviceName: String = "",
        val username: String = "", val profileId: Int? = null, val targetLength: Int = 16,
        val rotationMonth: Int? = null, val rotationYear: Int? = null, val variantOffset: Int = 0,
        val splitMode: SplitMode = SplitMode.SINGLE_USER, val year: Int? = null,
        val addServiceMarker: Boolean = false, val addYearMarker: Boolean = false
    )
    data class GenerationResult(
        val password: String, val mnemonicHint: String, val variantName: String,
        val strength: PasswordGenerator.Strength, val part1: String?, val part2: String?,
        val splitMode: SplitMode, val explanation: String, val variantOffset: Int = 0,
        val prefixLengths: List<Int> = emptyList()
    )
    private val translit = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )
    // Варианты транслитерации и похожие латинские начертания первой буквы.
    private val initialAlternatives = mapOf(
        'в' to listOf("v", "w"), 'е' to listOf("e", "ye"), 'ё' to listOf("yo", "e"),
        'ж' to listOf("zh", "j"), 'й' to listOf("y", "j", "i"), 'н' to listOf("n", "h"),
        'р' to listOf("r", "p"), 'с' to listOf("s", "c"), 'ф' to listOf("f", "ph"),
        'х' to listOf("kh", "h", "x"), 'ц' to listOf("ts", "c"), 'ч' to listOf("ch", "tch"),
        'щ' to listOf("shch", "sch"), 'ю' to listOf("yu", "iu"), 'я' to listOf("ya", "ja")
    )
    private val lookalikes = mapOf(
        'a' to "@4", 'b' to "86", 'c' to "<(", 'e' to "&3", 'g' to "9",
        'h' to "#", 'i' to "!1", 'l' to "|!1", 'o' to "0", 's' to "$5", 't' to "+7", 'z' to "2"
    )
    private const val PADDING = "^%?*=-_:;~[]{}>!@#$&+|<("
    private val weakPhrases = setOf("мама мыла раму", "я люблю тебя", "мой пароль", "пароль от сайта", "qwerty", "password")
    private data class Word(val original: String, val spellings: List<String>)
    private data class PartPlan(val words: List<Word>, val prefix: Int, val marker: String, val padding: Int)
    private data class BuiltPart(val value: String, val hints: List<String>)

    fun transliterate(text: String): String = buildString {
        for (char in text.lowercase(Locale.ROOT)) append(translit[char] ?: char.toString())
    }
    private fun words(phrase: String): List<Word> {
        val normalized = Normalizer.normalize(phrase, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val tokens = Regex("[а-яёa-z]+").findAll(normalized).map { it.value }.toList()
        require(tokens.size >= 2) { "Введите не менее двух слов для каждой части" }
        require(tokens.joinToString(" ") !in weakPhrases) { "Выберите менее предсказуемую личную фразу" }
        return tokens.map { token ->
            val tail = transliterate(token.drop(1))
            val starts = initialAlternatives[token.first()] ?: listOf(translit[token.first()] ?: token.first().toString())
            val spellings = starts.map { it + tail }.filter { it.isNotEmpty() }.distinct()
            require(spellings.any { it.length >= 2 }) { "Замените короткое слово «$token» на более длинное" }
            Word(token, spellings)
        }
    }
    fun validatePassword(password: String, splitMode: SplitMode): Boolean {
        if (password.isEmpty() || password.toSet().size != password.length) return false
        if (password.any { it.code !in 33..126 }) return false
        if (splitMode == SplitMode.TWO_USERS && password.length % 2 != 0) return false
        val parts = if (splitMode == SplitMode.TWO_USERS) password.chunked(password.length / 2) else listOf(password)
        return parts.all { part -> part.count { it in 'a'..'z' } >= 2 && part.count { it in 'A'..'Z' } >= 2 &&
            part.count { !it.isLetterOrDigit() } >= 2 }
    }
    fun generateVariants(options: GenerationOptions, count: Int = 3): List<GenerationResult> =
        generateVariantsResult(options, count).getOrDefault(emptyList())

    fun generateVariantsResult(options: GenerationOptions, count: Int = 3): Result<List<GenerationResult>> = runCatching {
        require(count in 1..10) { "Количество вариантов должно быть от 1 до 10" }
        require(options.targetLength in 12..56) { "Длина AMPG должна быть от 12 до 56 символов" }
        require(options.variantOffset in 0..9999) { "Некорректный номер варианта" }
        val two = options.splitMode == SplitMode.TWO_USERS
        require(!two || (options.targetLength >= 16 && options.targetLength % 2 == 0)) {
            "Для двух равных частей выберите чётную длину не менее 16"
        }
        val phrases = listOf(options.phrase) + if (two) listOf(options.phrase2.orEmpty()) else emptyList()
        val allWords = phrases.map(::words)
        val partLength = options.targetLength / if (two) 2 else 1
        require(allWords.all { it.size * 2 <= partLength }) {
            "Слишком много слов для выбранной длины: увеличьте длину или сократите фразу"
        }
        val service = transliterate(options.serviceName).filter { it in 'a'..'z' }
        require(!options.addServiceMarker || service.isNotEmpty()) { "Введите имя сервиса для маркера" }
        val serviceMarker = if (options.addServiceMarker) "${service.first().uppercaseChar()}${service.last()}" else ""
        val actualYear = options.year ?: options.rotationYear ?: Calendar.getInstance().get(Calendar.YEAR)
        require(!options.addYearMarker || actualYear in 1000..9999) { "Укажите год четырьмя цифрами" }
        val yearMarker = if (options.addYearMarker) actualYear.toString().takeLast(2) else ""
        val results = linkedMapOf<String, GenerationResult>()
        val markerChoices = listOf(true to true, true to false, false to true, false to false)
            .map { (s, y) -> (if (s) serviceMarker else "") to (if (y) yearMarker else "") }.distinct()
        for (offset in options.variantOffset until options.variantOffset + count.coerceAtLeast(3) * 3) {
            for ((servicePart, yearPart) in markerChoices) {
                if (results.size >= count) break
                val markers = if (two) listOf(servicePart, yearPart) else listOf(servicePart + yearPart)
                val markerChars = markers.joinToString("")
                if (markerChars.toSet().size != markerChars.length) continue
                val partPlans = allWords.mapIndexed { index, list -> plans(list, partLength, markers[index]) }
                if (partPlans.any { it.isEmpty() }) continue
                val combinations = if (two) partPlans[0].flatMap { a -> partPlans[1].map { b -> listOf(a, b) } }
                    else partPlans[0].map { listOf(it) }
                var found: Pair<List<BuiltPart>, List<PartPlan>>? = null
                for (combination in combinations) {
                    val built = solve(combination, offset, markerChars.toSet()) ?: continue
                    found = built to combination
                    break
                }
                val (built, chosen) = found ?: continue
                val password = built.joinToString("") { it.value }
                if (!validatePassword(password, options.splitMode)) continue
                val explanation = buildString {
                    append("AMPG 3. A и a различаются; один и тот же символ не повторяется.\n")
                    built.forEachIndexed { i, part ->
                        append("Часть ${i + 1}: по ${chosen[i].prefix} начальных букв каждого слова.\n")
                        part.hints.forEach { append(it).append('\n') }
                    }
                    if (options.addServiceMarker) append(if (servicePart.isNotEmpty()) "Сервис: $servicePart.\n" else "Маркер сервиса не поместился или повторял символы.\n")
                    if (options.addYearMarker) append(if (yearPart.isNotEmpty()) "Год: $yearPart.\n" else "Маркер года не поместился или повторял символы.\n")
                    append("Стойкость зависит от секретности фразы; известная фраза и замены не дают гарантии защиты.")
                }
                results.putIfAbsent(password, GenerationResult(password,
                    "AMPG 3: сохраните исходную фразу и параметры отдельно", "Вариант ${results.size + 1}",
                    PasswordGenerator.Strength.MEDIUM, if (two) built[0].value else null,
                    if (two) built[1].value else null, options.splitMode, explanation, offset, chosen.map { it.prefix }))
                // Один offset однозначно задаёт вариант вместе с маркерами.
                break
            }
            if (results.size >= count) break
        }
        require(results.isNotEmpty()) {
            "Не удалось совместить одинаковые префиксы и уникальность. Измените повторяющиеся начала слов, длину или маркеры."
        }
        results.values.toList()
    }
    private fun plans(words: List<Word>, length: Int, marker: String): List<PartPlan> {
        val maxPrefix = minOf((length - marker.length) / words.size, words.minOf { w -> w.spellings.maxOf(String::length) })
        if (maxPrefix < 2) return emptyList()
        return (maxPrefix downTo 2).mapNotNull { prefix ->
            val padding = length - marker.length - prefix * words.size
            if (padding in 0..4 && words.all { word -> word.spellings.any { it.length >= prefix } })
                PartPlan(words, prefix, marker, padding) else null
        }
    }
    private fun <T> rotate(list: List<T>, offset: Int): List<T> {
        if (list.isEmpty()) return list
        val start = Math.floorMod(offset, list.size)
        return list.drop(start) + list.take(start)
    }
    private fun solve(plans: List<PartPlan>, offset: Int, reserved: Set<Char>): List<BuiltPart>? {
        val used = reserved.toMutableSet()
        val completed = mutableListOf<BuiltPart>()
        val allWords = plans.flatMap { it.words }
        var steps = 0
        fun canAssignInitials(remaining: List<Word>): Boolean {
            val owner = mutableMapOf<Char, Int>()
            fun assign(index: Int, visited: MutableSet<Char>): Boolean {
                for (char in remaining[index].spellings.map { it.first().uppercaseChar() }.distinct()) {
                    if (char in used || !visited.add(char)) continue
                    val previous = owner[char]
                    if (previous == null || assign(previous, visited)) { owner[char] = index; return true }
                }
                return false
            }
            return remaining.indices.all { assign(it, mutableSetOf()) }
        }
        if (!canAssignInitials(allWords)) return null
        fun buildPart(partIndex: Int): Boolean {
            if (partIndex == plans.size) return true
            val plan = plans[partIndex]
            val pieces = mutableListOf<String>()
            val hints = mutableListOf<String>()
            val later = plans.drop(partIndex + 1).flatMap { it.words }
            fun buildWord(wordIndex: Int): Boolean {
                if (++steps > 20_000) return false
                if (wordIndex == plan.words.size) {
                    val body = pieces.joinToString("")
                    val suffix = rotate(PADDING.toList(), offset).filter { it !in used }.take(plan.padding).joinToString("")
                    if (suffix.length != plan.padding) return false
                    val value = body + suffix + plan.marker
                    if (value.count { it in 'a'..'z' } < 2 || value.count { it in 'A'..'Z' } < 2 ||
                        value.count { !it.isLetterOrDigit() } < 2) return false
                    suffix.forEach { used.add(it) }
                    val extraHint = if (suffix.isEmpty()) emptyList() else listOf("Суффикс варианта: $suffix")
                    completed.add(BuiltPart(value, hints.toList() + extraHint))
                    if (buildPart(partIndex + 1)) return true
                    completed.removeAt(completed.lastIndex); suffix.forEach { used.remove(it) }
                    return false
                }
                val word = plan.words[wordIndex]
                for (spelling in rotate(word.spellings.filter { it.length >= plan.prefix }, offset)) {
                    val prefix = spelling.take(plan.prefix)
                    val output = StringBuilder()
                    fun fill(position: Int): Boolean {
                        if (++steps > 20_000) return false
                        if (position == prefix.length) {
                            if (!canAssignInitials(plan.words.drop(wordIndex + 1) + later)) return false
                            pieces.add(output.toString()); hints.add("${word.original}: ${prefix.replaceFirstChar { it.uppercaseChar() }} → $output")
                            if (buildWord(wordIndex + 1)) return true
                            pieces.removeAt(pieces.lastIndex); hints.removeAt(hints.lastIndex)
                            return false
                        }
                        val char = prefix[position]
                        val options = if (position == 0) listOf(char.uppercaseChar()) else {
                            val letters = listOf(char.lowercaseChar(), char.uppercaseChar())
                            val replacements = lookalikes[char.lowercaseChar()].orEmpty().toList()
                            when (Math.floorMod(offset, 3)) {
                                1 -> replacements + letters
                                2 -> listOf(char.lowercaseChar()) + replacements + listOf(char.uppercaseChar())
                                else -> letters + replacements
                            }.distinct()
                        }
                        for (candidate in options) {
                            if (!used.add(candidate)) continue
                            output.append(candidate)
                            if (fill(position + 1)) return true
                            output.setLength(output.length - 1); used.remove(candidate)
                        }
                        return false
                    }
                    if (fill(0)) return true
                }
                return false
            }
            return buildWord(0)
        }
        return if (buildPart(0)) completed.toList() else null
    }
}
