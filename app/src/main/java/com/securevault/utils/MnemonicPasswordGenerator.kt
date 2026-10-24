package com.securevault.utils

import java.text.Normalizer
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

object MnemonicPasswordGenerator {
    const val ALGORITHM_VERSION = 5
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
    data class PhrasePreview(
        val words: List<String>, val transliteratedWords: List<String>, val skippedWords: List<String>,
        val notes: List<String> = emptyList(), val predictable: Boolean = false
    )
    private val translit = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )
    private val initialAlternatives = mapOf(
        'в' to listOf("v", "w"), 'у' to listOf("u", "y"), 'е' to listOf("e", "ye"), 'ё' to listOf("yo", "e"),
        'ж' to listOf("zh", "j"), 'й' to listOf("y", "j", "i"), 'н' to listOf("n", "h"),
        'р' to listOf("r", "p"), 'с' to listOf("s", "c"), 'ф' to listOf("f", "ph"),
        'х' to listOf("kh", "h", "x"), 'ц' to listOf("ts", "c"), 'ч' to listOf("ch", "tch"),
        'щ' to listOf("shch", "sch"), 'ю' to listOf("yu", "iu"), 'я' to listOf("ya", "ja")
    )
    // Порядок замен стабилен: сначала понятный символ/цифра, затем иной регистр.
    private val lookalikes = mapOf(
        'a' to "@4", 'b' to "86", 'c' to "<(", 'e' to "3&", 'g' to "9",
        'h' to "#", 'i' to "!1", 'l' to "|1!", 'o' to "0", 's' to "$5", 't' to "+7", 'z' to "2"
    )
    private val stopWords = ("и а но или либо да зато однако чтобы чтоб что как когда если хотя пока чем " +
        "то же ли бы ведь вот это этот эта эти его её ее их в во на над под к ко у с со о об обо от ото до по " +
        "за из изо без для при про через между перед после около возле вдоль мимо вокруг не ни я ты он она " +
        "оно мы вы они был была было были будет есть кто где куда откуда почему " +
        "a an the and or but to of in on at by for from with without into over under as is are was were be " +
        "been being am i you he she it we they this that these those my your his her its our their not no if so than " +
        "ну уж лишь разве даже будто словно потому поэтому поскольку пусть пускай " +
        "меня мне мной мною тебя тебе тобой тобою нас нам нами вас вам вами ему им нем нём ним " +
        "ей ею ней неё нее ними них мой моя моё мое мои моего моей моему моим мою моих моими " +
        "твой твоя твоё твое твои твоего твоей твоему твоим твою твоих твоими " +
        "наш наша наше наши нашего нашей нашему нашим нашу наших нашими " +
        "ваш ваша ваше ваши вашего вашей вашему вашим вашу ваших вашими " +
        "свой своя своё свое свои своего своей своему своим свою своих своими " +
        "тот та те того той тому тем ту тех теми этого этой этому этим эту этих этими этом " +
        "сам сама само сами себя себе собой собою который которая которое которые " +
        "быть буду будешь будем будете будут кроме среди согласно вопреки благодаря вследствие")
        .split(' ').toSet()
    private val compoundStopWords = setOf("из-за", "из-под", "кто-то", "что-то", "где-то", "как-то", "когда-то",
        "кто-нибудь", "что-нибудь", "где-нибудь", "какой-то", "чей-то")
    private val weakPhrases = setOf("мама мыла раму", "я люблю тебя", "мой пароль", "пароль от сайта", "qwerty", "password")
    private const val PADDING = "!^%?*=-_:;~[]{}>@#$&+|<("
    private const val SPECIAL_PRIORITY = "tasilche"
    private data class Word(val original: String, val spellings: List<String>)
    private data class Plan(val words: List<Word>, val prefix: Int, val marker: String, val padding: Int, val grouped: Boolean = false)
    private data class Slot(val part: Int, val base: Char, val initial: Boolean, val choices: List<Char>)
    private data class Built(val parts: List<String>, val hints: List<List<String>>)
    private enum class Kind { UPPER, LOWER, SPECIAL }
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val COMPLETE = LOWER + UPPER + "0123456789" + PADDING

    fun transliterate(text: String): String = buildString {
        text.lowercase(Locale.ROOT).forEach { append(translit[it] ?: it.toString()) }
    }
    fun preparePhrase(phrase: String): Result<PhrasePreview> = runCatching {
        require(phrase.length <= 8192) { "Допустимо до 8192 знаков во фразе" }
        val text = Normalizer.normalize(phrase, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace('–', '-').replace('—', '-')
        require(text.none { it.isLetter() && it !in 'а'..'я' && it != 'ё' && it !in 'a'..'z' }) {
            "Для фразы используйте русские или английские буквы"
        }
        val all = Regex("[а-яёa-z]+(?:-[а-яёa-z]+)*|[0-9]+").findAll(text).map { it.value }.toList()
        val predictable = all.joinToString(" ") in weakPhrases
        val kept = mutableListOf<String>(); val skipped = mutableListOf<String>()
        for (token in all) {
            if (token in compoundStopWords || token.all(Char::isDigit)) { skipped.add(token); continue }
            for (word in token.split('-')) {
                if (word in stopWords || word.length == 1) skipped.add(word) else kept.add(word)
            }
        }
        val notes = mutableListOf<String>()
        if (kept.isEmpty()) {
            val originals = all.flatMap { it.split('-') }.filter { it.isNotBlank() }
            require(originals.isNotEmpty()) { "Введите фразу буквами или цифрами" }
            kept.addAll(originals); skipped.clear()
            notes.add("В этой фразе нет отдельных значимых слов: для генерации сохранены все введённые слова.")
        }
        if (predictable) notes.add("Эта фраза общеизвестна. Замены символов не делают известную фразу непредсказуемой.")
        PhrasePreview(kept, kept.map { transliterate(it).replaceFirstChar(Char::uppercaseChar) }, skipped, notes, predictable)
    }
    private fun words(preview: PhrasePreview): List<Word> = preview.words.map { word ->
        val starts = initialAlternatives[word.first()] ?: listOf(translit[word.first()] ?: word.first().toString())
        val spellings = starts.map { it + transliterate(word.drop(1)) }.filter { it.isNotEmpty() }.distinct()
        Word(word, spellings.ifEmpty { listOf("x") })
    }
    fun normalizeLength(value: Float, splitMode: SplitMode): Int {
        if (!value.isFinite()) return 16
        val two = splitMode == SplitMode.TWO_USERS
        val step = if (two) 2 else 1
        return ((value / step).roundToInt() * step).coerceIn(if (two) 16 else 12, 56)
    }
    fun validatePassword(password: String, splitMode: SplitMode): Boolean {
        if (password.isEmpty() || password.toSet().size != password.length || password.any { it.code !in 33..126 }) return false
        if (splitMode == SplitMode.TWO_USERS && password.length % 2 != 0) return false
        val parts = if (splitMode == SplitMode.TWO_USERS) password.chunked(password.length / 2) else listOf(password)
        return parts.all { it.count { c -> c in 'a'..'z' } >= 2 && it.count { c -> c in 'A'..'Z' } >= 2 &&
            it.count { c -> !c.isLetterOrDigit() } >= 2 }
    }
    fun generateVariants(options: GenerationOptions, count: Int = 3): List<GenerationResult> =
        generateVariantsResult(options, count).getOrDefault(emptyList())

    fun generateVariantsResult(options: GenerationOptions, count: Int = 3): Result<List<GenerationResult>> = runCatching {
        require(count in 1..10) { "Количество вариантов: от 1 до 10" }
        require(options.variantOffset in 0..9999) { "Некорректный номер варианта" }
        require(options.targetLength in 12..56) { "Длина одной части: от 12 до 56 символов" }
        val two = options.splitMode == SplitMode.TWO_USERS
        require(!two || (options.targetLength >= 16 && options.targetLength % 2 == 0)) {
            "Для двух частей выберите чётную общую длину от 16 до 56"
        }
        val previews = (listOf(options.phrase) + if (two) listOf(options.phrase2.orEmpty()) else emptyList())
            .map { preparePhrase(it).getOrThrow() }
        val wordLists = previews.map(::words)
        val partLength = options.targetLength / if (two) 2 else 1
        val service = transliterate(options.serviceName).filter { it in 'a'..'z' }
        val serviceMarker = if (options.addServiceMarker && service.isNotEmpty()) "${service.first().uppercaseChar()}${service.last()}" else ""
        val year = options.year ?: options.rotationYear ?: Calendar.getInstance().get(Calendar.YEAR)
        require(!options.addYearMarker || year in 1000..9999) { "Укажите год четырьмя цифрами" }
        val yearMarker = if (options.addYearMarker) year.toString().takeLast(2) else ""
        val markers = listOf(serviceMarker to yearMarker, serviceMarker to "", "" to yearMarker, "" to "").distinct()
        val results = linkedMapOf<String, GenerationResult>()
        for (offset in options.variantOffset until minOf(10_000, options.variantOffset + count.coerceAtLeast(3) * 3)) {
            for ((sm, ym) in markers) {
                val suffixes = if (two) listOf(sm, ym) else listOf(sm + ym)
                val reserved = suffixes.joinToString("")
                if (reserved.toSet().size != reserved.length) continue
                val choices = wordLists.mapIndexed { i, w -> plans(w, partLength, suffixes[i]) }
                if (choices.any { it.isEmpty() }) continue
                val combinations = if (two) choices[0].flatMap { a -> choices[1].map { b -> listOf(a, b) } }
                    else choices[0].map { listOf(it) }
                var found: Pair<Built, List<Plan>>? = null
                for (plan in combinations) {
                    val built = build(plan, reserved.toSet(), offset) ?: continue
                    found = built to plan; break
                }
                val (built, selected) = found ?: continue
                val password = built.parts.joinToString("")
                if (!validatePassword(password, options.splitMode)) continue
                val explanation = buildString {
                    append("AMPG 5: основа из вашей фразы; повторные буквы заменяются автоматически. A и a различаются.\n")
                    built.parts.indices.forEach { i ->
                        append("Часть ${i + 1}. Латиницей: ${previews[i].transliteratedWords.joinToString(" ")}.\n")
                        if (previews[i].skippedWords.isNotEmpty()) append("Пропущено: ${previews[i].skippedWords.joinToString(", ")}.\n")
                        previews[i].notes.forEach { append(it).append('\n') }
                        append("По ${selected[i].prefix} знаков от каждой основы.\n")
                        if (selected[i].grouped) append("Длинная фраза сжата в группы соседних слов; их порядок сохранён.\n")
                        built.hints[i].forEach { append(it).append('\n') }
                    }
                    if (options.addServiceMarker) append(if (sm.isNotEmpty()) "Сервис: $sm.\n" else "Маркер сервиса пропущен: не помещается или повторяет символы.\n")
                    if (options.addYearMarker) append(if (ym.isNotEmpty()) "Год: $ym.\n" else "Маркер года пропущен: не помещается или повторяет цифры.\n")
                    append("Известная фраза и замены предсказуемы: используйте собственную непубличную фразу.")
                }
                results.putIfAbsent(password, GenerationResult(password, "AMPG 5: исходная фраза не сохранена",
                    "Вариант ${offset + 1}", if (previews.any { it.predictable }) PasswordGenerator.Strength.WEAK else PasswordGenerator.Strength.MEDIUM,
                    if (two) built.parts[0] else null, if (two) built.parts[1] else null,
                    options.splitMode, explanation, offset, selected.map { it.prefix }))
                break
            }
            if (results.size >= count) break
        }
        check(results.isNotEmpty()) { "Внутренняя ошибка распределения символов AMPG" }
        results.values.toList()
    }
    private fun plans(words: List<Word>, length: Int, marker: String): List<Plan> {
        val space = length - marker.length
        if (space <= 0) return emptyList()
        val grouped = words.size > space
        val adapted = if (!grouped) words else (0 until space).map { i ->
            val group = words.subList(i * words.size / space, (i + 1) * words.size / space)
            val spelling = group.joinToString("") { it.spellings.first().take(1) } +
                group.joinToString("") { it.spellings.first().drop(1) }
            Word(group.joinToString(" ") { it.original }, listOf(spelling))
        }
        val prefix = space / adapted.size
        return listOf(Plan(adapted, prefix, marker, space % adapted.size, grouped))
    }
    private fun <T> rotate(values: List<T>, offset: Int): List<T> {
        val start = Math.floorMod(offset, values.size.coerceAtLeast(1))
        return values.drop(start) + values.take(start)
    }
    private fun replacements(c: Char): List<Char> = (lookalikes[c] ?: when (c) {
        '0' -> "oO"; '1' -> "ilIL!|"; '2' -> "zZ"; '3' -> "eE&"; '4' -> "aA@"
        '5' -> "sS$"; '6', '8' -> "bB"; '7' -> "tT+"; '9' -> "gG"; else -> ""
    }).toList()
    private fun options(c: Char, offset: Int): List<Char> = when (Math.floorMod(offset, 4)) {
        1 -> replacements(c) + c + c.uppercaseChar()
        2 -> listOf(c, c.uppercaseChar()) + replacements(c)
        3 -> listOf(c.uppercaseChar(), c) + replacements(c)
        else -> listOf(c) + replacements(c) + c.uppercaseChar()
    }.distinct()
    private fun prefix(spelling: String, length: Int): String = buildString {
        repeat(length) { append(spelling[it % spelling.length]) }
    }
    private fun reserveRow(seed: String): List<Char> {
        val bytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        val shift = ((bytes[0].toInt() and 255) * 256 + (bytes[1].toInt() and 255)) % COMPLETE.length
        return rotate(COMPLETE.toList(), shift)
    }
    private fun build(plans: List<Plan>, reserved: Set<Char>, offset: Int): Built? {
        val spellings = plans.map { p -> p.words.map { rotate(it.spellings, offset).first() } }
        val seed = plans.joinToString(" / ") { p -> p.words.joinToString(" ") { it.original } }
        for (stage in 0..3) {
            val slots = mutableListOf<Slot>()
            fun add(part: Int, word: Int, position: Int, initial: Boolean) {
                val spelling = spellings[part][word]
                val c = spelling[position % spelling.length]
                val own = spelling.toList().distinct()
                var choices = if (initial) listOf(c.uppercaseChar()) + replacements(c) +
                    own.filter { it != c }.map(Char::uppercaseChar) + c + own.filter { it != c }
                    else options(c, offset)
                if (stage >= 1) choices = choices + own.flatMap { options(it, offset) }
                if (stage >= 2) choices = choices + spellings[part].joinToString("").toList().distinct().flatMap { options(it, offset) }
                if (stage >= 3) choices = choices + reserveRow("$seed:$offset:$part:$word:$position")
                slots.add(Slot(part, c, initial, choices.distinct()))
            }
            plans.forEachIndexed { part, plan ->
                plan.words.indices.forEach { word -> repeat(plan.prefix) { pos -> add(part, word, pos, pos == 0) } }
                repeat(plan.padding) { word -> add(part, word, plan.prefix, false) }
            }
            val markers = plans.map { it.marker }
            val assignment = if (stage == 3) complete(slots, reserved, markers)
                else repair(slots, reserved, markers) ?: continue
            val parts = plans.indices.map { part -> slots.indices.filter { slots[it].part == part }
                .map { assignment[it] }.joinToString("") + markers[part] }
            val hints = mutableListOf<List<String>>(); var cursor = 0
            plans.forEachIndexed { part, plan ->
                val lines = mutableListOf<String>()
                plan.words.forEachIndexed { word, w ->
                    val spelling = spellings[part][word]
                    val input = prefix(spelling, plan.prefix).replaceFirstChar(Char::uppercaseChar)
                    val output = String(assignment, cursor, plan.prefix); cursor += plan.prefix
                    lines.add("${w.original}: $input → $output")
                    if (output.first() != input.first()) lines.add("Начало слова: ${input.first()} → ${output.first()} — свободная замена без повтора.")
                    if (plan.prefix > spelling.length) lines.add("Короткая основа продолжена по кругу до выбранной длины.")
                }
                if (plan.padding > 0) {
                    lines.add("Хвост из следующих букв слов: ${String(assignment, cursor, plan.padding)}"); cursor += plan.padding
                }
                if (stage >= 1) lines.add("Для повторов использованы дополнительные буквы тех же слов.")
                if (stage >= 2) lines.add("Расширенные замены учитывают остальные буквы этой фразы.")
                if (stage == 3) lines.add("Оставшиеся повторы заменены по постоянному резервному словарю. Та же фраза и параметры воспроизводят результат.")
                hints.add(lines)
            }
            return Built(parts, hints)
        }
        return null
    }
    private fun category(c: Char, kind: Kind): Boolean = when (kind) {
        Kind.UPPER -> c in 'A'..'Z'
        Kind.LOWER -> c in 'a'..'z'
        Kind.SPECIAL -> c != '\u0000' && !c.isLetterOrDigit()
    }
    private fun count(slots: List<Slot>, chars: CharArray, markers: List<String>, part: Int, kind: Kind): Int =
        slots.indices.count { slots[it].part == part && category(chars[it], kind) } + markers[part].count { category(it, kind) }
    private fun candidates(slots: List<Slot>, part: Int, kind: Kind): List<Int> =
        slots.indices.filter { slots[it].part == part }.sortedWith(compareBy(
            { if (kind == Kind.UPPER) if (slots[it].initial) 0 else 1 else if (slots[it].initial) 1 else 0 },
            { if (kind == Kind.SPECIAL) SPECIAL_PRIORITY.indexOf(slots[it].base).let { n -> if (n < 0) 100 else n } else 0 },
            { it }))
    private fun repair(slots: List<Slot>, reserved: Set<Char>, markers: List<String>): CharArray? {
        val forced = mutableMapOf<Int, Char>()
        val anchored = reserved.toMutableSet()
        // Сохраняем первое свободное заглавное начало; заменяются именно конфликты.
        for (i in slots.indices.filter { slots[it].initial }) {
            val first = slots[i].choices.first()
            if (first in 'A'..'Z' && anchored.add(first)) forced[i] = first
        }
        var assignment = match(slots, reserved, forced) ?: return null
        fun deficit(chars: CharArray): Int = markers.indices.sumOf { part ->
            Kind.values().sumOf { kind -> (2 - count(slots, chars, markers, part, kind)).coerceAtLeast(0) }
        }
        while (deficit(assignment) > 0) {
            val before = deficit(assignment); var improved: CharArray? = null
            categoryRepair@ for (part in markers.indices) for (kind in Kind.values()) {
                if (count(slots, assignment, markers, part, kind) >= 2) continue
                for (slot in candidates(slots, part, kind).filter { it !in forced }) {
                    for (c in slots[slot].choices.filter { category(it, kind) }) {
                        val attempt = match(slots, reserved, forced + (slot to c), assignment) ?: continue
                        if (deficit(attempt) < before) { forced[slot] = c; improved = attempt; break@categoryRepair }
                    }
                }
            }
            if (improved == null) return null
            assignment = improved
        }
        return assignment
    }
    // Полный словарь содержит больше разных символов, чем максимальная длина пароля.
    // Сначала резервируются квоты всех частей, затем заполняется остальная основа.
    private fun complete(slots: List<Slot>, reserved: Set<Char>, markers: List<String>): CharArray {
        val chars = CharArray(slots.size); val used = reserved.toMutableSet()
        for (kind in Kind.values()) for (part in markers.indices) {
            repeat((2 - markers[part].count { category(it, kind) }).coerceAtLeast(0)) {
                val i = candidates(slots, part, kind).first { chars[it] == '\u0000' }
                val c = slots[i].choices.first { it !in used && category(it, kind) }
                chars[i] = c; used.add(c)
            }
        }
        for (i in slots.indices.filter { chars[it] == '\u0000' }) {
            val c = slots[i].choices.first { it !in used }; chars[i] = c; used.add(c)
        }
        return chars
    }
    private fun match(slots: List<Slot>, reserved: Set<Char>, forced: Map<Int, Char> = emptyMap(), preferred: CharArray? = null): CharArray? {
        val chars = CharArray(slots.size); val owner = mutableMapOf<Char, Int>(); val fixed = forced.keys
        for (i in fixed.sorted()) {
            val c = forced.getValue(i)
            if (c !in slots[i].choices || c in reserved || owner.put(c, i) != null) return null
            chars[i] = c
        }
        fun choices(i: Int): List<Char> = ((preferred?.get(i)?.let { listOf(it) } ?: emptyList()) + slots[i].choices).distinct()
        fun augment(i: Int, seen: MutableSet<Char>): Boolean {
            for (c in choices(i)) {
                if (c in reserved || !seen.add(c)) continue
                val old = owner[c]
                if (old == null || (old !in fixed && augment(old, seen))) { owner[c] = i; chars[i] = c; return true }
            }
            return false
        }
        val order = slots.indices.sortedWith(compareBy({ if (slots[it].initial) 0 else 1 }, { it }))
        for (i in order.filter { it !in fixed }) {
            val free = choices(i).firstOrNull { it !in reserved && it !in owner }
            if (free != null) { owner[free] = i; chars[i] = free }
            else if (!augment(i, mutableSetOf())) return null
        }
        return chars
    }
}
