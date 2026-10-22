package com.securevault.utils

object CsvCodec {
    fun encode(row: List<String>): String = row.joinToString(",") { value ->
        if (value.any { it in ",\"\r\n" }) "\"${value.replace("\"", "\"\"") }\"" else value
    }
    fun decode(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var closed = false
        var i = if (text.startsWith('\uFEFF')) 1 else 0
        fun field() { row.add(cell.toString()); cell.clear(); closed = false }
        fun record() { field(); rows.add(row.toList()); row.clear() }
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else cell.append(ch)
            } else when (ch) {
                '"' -> { require(cell.isEmpty() && !closed) { "Некорректные кавычки CSV" }; quoted = true }
                ',' -> field()
                '\r', '\n' -> { record(); if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++ }
                else -> { require(!closed) { "Символы после закрывающей кавычки CSV" }; cell.append(ch) }
            }
            i++
        }
        require(!quoted) { "Незакрытые кавычки CSV" }
        if (cell.isNotEmpty() || row.isNotEmpty() || closed) record()
        return rows
    }
}
