// ... (импорты те же, что и были) ...
// Главное изменение внутри функции generateMnemonicPassword:

fun generateMnemonicPassword() {
    if (phrase.isBlank()) {
        showError = "Введите фразу"
        return
    }
    // ✅ Логика превращения фразы в пароль
    val words = phrase.trim().split(Regex("\\s+"))
    val base = words.joinToString("") { word -> 
        word.firstOrNull()?.uppercaseChar()?.toString() ?: "" 
    }
    
    var result = base
    if (useDigits) result += (100..999).random().toString()
    if (useSpecial) result += listOf("!", "@", "#", "$", "%").random()
    
    // Дополняем до нужной длины
    while (result.length < length) {
        result += "abcdefghijklmnopqrstuvwxyz".random()
    }
    if (result.length > length) result = result.take(length)
    
    generatedPassword = result
    showError = null
}
