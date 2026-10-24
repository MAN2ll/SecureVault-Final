package com.securevault.utils

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

object MnemonicPasswordGenerator {
    const val ALGORITHM_VERSION = 4
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
    data class PhrasePreview(val words: List<String>, val transliteratedWords: List<String>, val skippedWords: List<String>)
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
    private data class Plan(val words: List<Word>, val prefix: Int, val marker: String, val padding: Int)
    private data class Slot(val part: Int, val base: Char?, val initial: Boolean, val choices: List<Char>)
    private data class Built(val parts: List<String>, val hints: List<List<String>>)

    fun transliterate(text: String): String = buildString {
        text.lowercase(Locale.ROOT).forEach { append(translit[it] ?: it.toString()) }
    }
    fun preparePhrase(phrase: String): Result<PhrasePreview> = runCatching {
        require(phrase.length <= 512) { "Сократите фразу до 512 знаков" }
        val text = Normalizer.normalize(phrase, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace('–', '-').replace('—', '-')
        require(text.none { it.isLetter() && it !in 'а'..'я' && it != 'ё' && it !in 'a'..'z' }) {
            "Для фразы используйте русские или английские буквы"
        }
        val all = Regex("[а-яёa-z]+(?:-[а-яёa-z]+)*|[0-9]+").findAll(text).map { it.value }.toList()
        require(all.joinToString(" ") !in weakPhrases) { "Выберите собственную непубличную фразу вместо известной" }
        val kept = mutableListOf<String>(); val skipped = mutableListOf<String>()
        for (token in all) {
            if (token in compoundStopWords || token.all(Char::isDigit)) { skipped.add(token); continue }
            for (word in token.split('-')) {
                if (word in stopWords || word.length == 1) skipped.add(word) else kept.add(word)
            }
        }
        require(kept.size >= 2) { "После исключения служебных слов нужно минимум два значимых слова" }
        PhrasePreview(kept, kept.map { transliterate(it).replaceFirstChar(Char::uppercaseChar) }, skipped)
    }
    private fun words(preview: PhrasePreview): List<Word> = preview.words.map { word ->
        val starts = initialAlternatives[word.first()] ?: listOf(translit[word.first()] ?: word.first().toString())
        val spellings = starts.map { it + transliterate(word.drop(1)) }.filter { it.length >= 2 }.distinct()
        require(spellings.isNotEmpty()) { "Слово «$word» слишком короткое после транслитерации" }
        Word(word, spellings)
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
        require(wordLists.all { it.size * 2 <= partLength }) {
            "После исключения служебных слов осталось слишком много слов: увеличьте длину или сократите фразу"
        }
        require(canAssignInitials(wordLists.flatten(), emptySet())) {
            "Начала значимых слов требуют одинаковых заглавных букв. Замените одно из таких слов: " +
                wordLists.flatten().groupBy { it.spellings.first().first() }.values.filter { it.size > 1 }
                    .flatten().joinToString { it.original }
        }
        val service = transliterate(options.serviceName).filter { it in 'a'..'z' }
        require(!options.addServiceMarker || service.isNotEmpty()) { "Введите имя сервиса для маркера" }
        val serviceMarker = if (options.addServiceMarker) "${service.first().uppercaseChar()}${service.last()}" else ""
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
                    append("AMPG 4: одинаковые начальные префиксы, точные повторы исключены; A и a различаются.\n")
                    built.parts.indices.forEach { i ->
                        append("Часть ${i + 1}. Латиницей: ${previews[i].transliteratedWords.joinToString(" ")}.\n")
                        if (previews[i].skippedWords.isNotEmpty()) append("Пропущено: ${previews[i].skippedWords.joinToString(", ")}.\n")
                        append("По ${selected[i].prefix} букв от каждого слова.\n")
                        built.hints[i].forEach { append(it).append('\n') }
                    }
                    if (options.addServiceMarker) append(if (sm.isNotEmpty()) "Сервис: $sm.\n" else "Маркер сервиса пропущен: не помещается или повторяет символы.\n")
                    if (options.addYearMarker) append(if (ym.isNotEmpty()) "Год: $ym.\n" else "Маркер года пропущен: не помещается или повторяет цифры.\n")
                    append("Известная фраза и замены предсказуемы: используйте собственную непубличную фразу.")
                }
                results.putIfAbsent(password, GenerationResult(password, "AMPG 4: исходная фраза не сохранена",
                    "Вариант ${offset + 1}", PasswordGenerator.Strength.MEDIUM,
                    if (two) built.parts[0] else null, if (two) built.parts[1] else null,
                    options.splitMode, explanation, offset, selected.map { it.prefix }))
                break
            }
            if (results.size >= count) break
        }
        require(results.isNotEmpty()) {
            "Для этой длины не хватает различных букв или похожих замен в равных префиксах. " +
                "Выберите другую длину либо замените короткое/повторяющееся слово. " +
                "Значимые слова: ${previews.joinToString(" / ") { it.words.joinToString(" ") }}"
        }
        results.values.toList()
    }
    private fun plans(words: List<Word>, length: Int, marker: String): List<Plan> {
        val maxPrefix = minOf((length - marker.length) / words.size, words.minOf { it.spellings.maxOf(String::length) })
        if (maxPrefix < 2) return emptyList()
        return (maxPrefix downTo 2).mapNotNull { prefix ->
            val padding = length - marker.length - words.size * prefix
            if (padding in 0..4 && words.all { w -> w.spellings.any { it.length >= prefix } })
                Plan(words, prefix, marker, padding) else null
        }
    }
    private fun canAssignInitials(words: List<Word>, reserved: Set<Char>): Boolean {
        val owners = mutableMapOf<Char, Int>()
        fun assign(i: Int, seen: MutableSet<Char>): Boolean {
            for (char in words[i].spellings.map { it.first().uppercaseChar() }.distinct()) {
                if (char in reserved || !seen.add(char)) continue
                val old = owners[char]
                if (old == null || assign(old, seen)) { owners[char] = i; return true }
            }
            return false
        }
        return words.indices.all { assign(it, mutableSetOf()) }
    }
    private fun <T> rotate(values: List<T>, offset: Int): List<T> {
        val start = Math.floorMod(offset, values.size.coerceAtLeast(1))
        return values.drop(start) + values.take(start)
    }
    private fun options(char: Char, offset: Int): List<Char> {
        val replacements = lookalikes[char].orEmpty().toList()
        return when (Math.floorMod(offset, 3)) {
            1 -> replacements + char + char.uppercaseChar()
            2 -> listOf(char, char.uppercaseChar()) + replacements
            else -> listOf(char) + replacements + char.uppercaseChar()
        }.distinct()
    }
    private fun build(plans: List<Plan>, reserved: Set<Char>, offset: Int): Built? {
        val descriptors = plans.flatMapIndexed { part, plan -> plan.words.map { part to it } }
        val chosen = mutableListOf<String>(); val starts = reserved.toMutableSet()
        var spellingAttempts = 0
        fun select(i: Int): Built? {
            if (spellingAttempts > 512) return null
            if (i < descriptors.size) {
                val (part, word) = descriptors[i]
                val spellings = rotate(word.spellings.filter { it.length >= plans[part].prefix }, offset)
                for (spelling in spellings) {
                    val first = spelling.first().uppercaseChar()
                    if (!starts.add(first)) continue
                    chosen.add(spelling)
                    val remaining = descriptors.drop(i + 1).map { (p, w) ->
                        w.copy(spellings = w.spellings.filter { it.length >= plans[p].prefix })
                    }
                    if (canAssignInitials(remaining, starts)) select(i + 1)?.let { return it }
                    chosen.removeAt(chosen.lastIndex); starts.remove(first)
                }
                return null
            }
            spellingAttempts++
            val slots = mutableListOf<Slot>(); var wordIndex = 0
            for ((part, plan) in plans.withIndex()) {
                for (word in plan.words) {
                    val prefix = chosen[wordIndex++].take(plan.prefix)
                    prefix.forEachIndexed { pos, c -> slots.add(Slot(part, c, pos == 0,
                        if (pos == 0) listOf(c.uppercaseChar()) else options(c, offset))) }
                }
                repeat(plan.padding) { slots.add(Slot(part, null, false, rotate(PADDING.toList(), offset))) }
            }
            var assignment = match(slots, reserved) ?: return null
            val forced = mutableMapOf<Int, Char>()
            fun counts(chars: CharArray, part: Int, lower: Boolean): Int =
                slots.indices.count { slots[it].part == part && category(chars[it], lower) } +
                    plans[part].marker.count { category(it, lower) }
            fun deficit(chars: CharArray): Int = plans.indices.sumOf { part ->
                (2 - counts(chars, part, true)).coerceAtLeast(0) + (2 - counts(chars, part, false)).coerceAtLeast(0)
            }
            while (deficit(assignment) > 0) {
                val before = deficit(assignment); var improved: CharArray? = null
                repair@ for (part in plans.indices) for (lower in listOf(true, false)) {
                    if (counts(assignment, part, lower) >= 2) continue
                    val candidates = slots.indices.filter { slots[it].part == part && !slots[it].initial && it !in forced }
                        .sortedWith(compareBy({ if (lower) 0 else SPECIAL_PRIORITY.indexOf(slots[it].base ?: ' ').let { n -> if (n < 0) 100 else n } }, { it }))
                    for (slot in candidates) for (c in slots[slot].choices.filter { category(it, lower) }) {
                        val attempt = match(slots, reserved, forced + (slot to c), assignment) ?: continue
                        if (deficit(attempt) < before) { forced[slot] = c; improved = attempt; break@repair }
                    }
                }
                if (improved == null) break
                assignment = improved
            }
            if (deficit(assignment) > 0) assignment = exact(slots, reserved, plans.map { it.marker }, assignment) ?: return null
            val parts = plans.indices.map { part -> slots.indices.filter { slots[it].part == part }.map { assignment[it] }.joinToString("") + plans[part].marker }
            val hints = mutableListOf<List<String>>(); var cursor = 0; wordIndex = 0
            plans.forEach { plan ->
                val lines = mutableListOf<String>()
                plan.words.forEach { word ->
                    val prefix = chosen[wordIndex++].take(plan.prefix).replaceFirstChar(Char::uppercaseChar)
                    val piece = String(assignment, cursor, plan.prefix); cursor += plan.prefix
                    lines.add("${word.original}: $prefix → $piece")
                }
                if (plan.padding > 0) { lines.add("Суффикс длины: ${String(assignment, cursor, plan.padding)}"); cursor += plan.padding }
                hints.add(lines)
            }
            return Built(parts, hints)
        }
        return select(0)
    }
    private fun category(c: Char, lower: Boolean): Boolean = if (lower) c in 'a'..'z' else c != '\u0000' && !c.isLetterOrDigit()
    private fun match(slots: List<Slot>, reserved: Set<Char>, forced: Map<Int, Char> = emptyMap(), preferred: CharArray? = null): CharArray? {
        val chars = CharArray(slots.size); val owner = mutableMapOf<Char, Int>()
        val fixed = slots.indices.filter { slots[it].initial || it in forced }.toSet()
        for (i in fixed) {
            val c = forced[i] ?: slots[i].choices.single()
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
        for (i in slots.indices.filter { it !in fixed }) {
            val free = choices(i).firstOrNull { it !in reserved && it !in owner }
            if (free != null) { owner[free] = i; chars[i] = free }
            else if (!augment(i, mutableSetOf())) return null
        }
        return chars
    }
    private fun exact(slots: List<Slot>, reserved: Set<Char>, markers: List<String>, preferred: CharArray): CharArray? {
        val chars = CharArray(slots.size); val used = reserved.toMutableSet(); var steps = 0
        for (i in slots.indices.filter { slots[it].initial }) {
            val c = slots[i].choices.single(); if (!used.add(c)) return null; chars[i] = c
        }
        fun search(): Boolean {
            if (++steps > 50_000) return false
            val remaining = slots.indices.filter { chars[it] == '\u0000' }
            val available = remaining.associateWith { i -> slots[i].choices.filter { it !in used } }
            if (available.values.any { it.isEmpty() }) return false
            val needs = markers.indices.map { part ->
                val owned = slots.indices.filter { slots[it].part == part }.map { chars[it] } + markers[part].toList()
                (2 - owned.count { category(it, true) }).coerceAtLeast(0) to (2 - owned.count { category(it, false) }).coerceAtLeast(0)
            }
            for (part in markers.indices) {
                val rest = remaining.filter { slots[it].part == part }; val (low, special) = needs[part]
                if (low + special > rest.size) return false
                for ((lower, need) in listOf(true to low, false to special)) {
                    if (rest.flatMap { available.getValue(it).filter { c -> category(c, lower) } }.toSet().size < need) return false
                    if (rest.count { available.getValue(it).any { c -> category(c, lower) } } < need) return false
                }
            }
            if (remaining.isEmpty()) return needs.all { it.first == 0 && it.second == 0 }
            val i = remaining.minWith(compareBy({ available.getValue(it).size }, { it }))
            val part = slots[i].part; val (low, special) = needs[part]; val rest = remaining.count { slots[it].part == part }
            for (c in (listOf(preferred[i]) + available.getValue(i)).distinct()) {
                if (c !in available.getValue(i)) continue
                if (low == rest && !category(c, true)) continue
                if (special == rest && !category(c, false)) continue
                if (low + special == rest && !category(c, true) && !category(c, false)) continue
                chars[i] = c; used.add(c)
                if (search()) return true
                used.remove(c); chars[i] = '\u0000'
            }
            return false
        }
        return if (search()) chars else null
    }
}
