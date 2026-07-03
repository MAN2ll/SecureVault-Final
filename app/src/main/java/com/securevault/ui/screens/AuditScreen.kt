@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.security.MasterPasswordHasher
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordValidator
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val context = LocalContext.current

    var showDeepAuditDialog by remember { mutableStateOf(false) }
    var deepAuditResults by remember { mutableStateOf<DeepAuditResults?>(null) }
    var isDeepAuditLoading by remember { mutableStateOf(false) }

    //  ПОВЕРХНОСТНЫЙ АУДИТ (без расшифровки паролей)
    val surfaceAudit = remember(entries) {
        performSurfaceAudit(entries)
    }

    val score = surfaceAudit.securityScore

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аудит безопасности", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Общий рейтинг
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        score >= 80 -> MaterialTheme.colorScheme.primaryContainer
                        score >= 50 -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Общий рейтинг безопасности", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "$score%",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            score >= 80 -> MaterialTheme.colorScheme.primary
                            score >= 50 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Всего паролей: ${surfaceAudit.totalEntries}", fontSize = 14.sp)
                }
            }

            // Метрики поверхностного аудита
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Просрочено",
                    value = surfaceAudit.expiredCount,
                    color = MaterialTheme.colorScheme.error
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Без ротации",
                    value = surfaceAudit.noRotationCount,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Без fingerprint",
                    value = surfaceAudit.noFingerprintCount,
                    color = MaterialTheme.colorScheme.tertiary
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Дубли fingerprint",
                    value = surfaceAudit.duplicateFingerprintCount,
                    color = if (surfaceAudit.duplicateFingerprintCount > 0) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            }

            // Приблизительная оценка слабых паролей (по длине зашифрованного текста)
            if (surfaceAudit.potentiallyWeakCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Возможно слабых паролей: ${surfaceAudit.potentiallyWeakCount} (приблизительная оценка по длине)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Кнопка глубокого аудита
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Глубокий аудит", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Проверка длины, повторов символов, 60% уникальности",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showDeepAuditDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Запустить глубокий аудит")
                    }
                }
            }

            // Результаты глубокого аудита
            if (deepAuditResults != null) {
                DeepAuditResultsCard(results = deepAuditResults!!)
            }

            // Рекомендации
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Рекомендации", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))

                    val recommendations = buildRecommendations(surfaceAudit, deepAuditResults)
                    if (recommendations.isEmpty()) {
                        Text(
                            "✓ Отличный уровень безопасности!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        recommendations.forEach { rec ->
                            Text("• $rec", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }

    // Диалог подтверждения мастер-пароля для глубокого аудита
    if (showDeepAuditDialog) {
        ConfirmMasterPasswordForAudit(
            context = context,
            onConfirmed = {
                showDeepAuditDialog = false
                isDeepAuditLoading = true
                // Запуск глубокого аудита в фоне
                deepAuditResults = performDeepAudit(entries)
                isDeepAuditLoading = false
            },
            onDismiss = { showDeepAuditDialog = false }
        )
    }

    if (isDeepAuditLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Аудит...") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Расшифровка и анализ паролей")
                }
            },
            confirmButton = {}
        )
    }
}

// ===== ПОВЕРХНОСТНЫЙ АУДИТ =====

data class SurfaceAuditResults(
    val totalEntries: Int,
    val expiredCount: Int,
    val noRotationCount: Int,
    val noFingerprintCount: Int,
    val duplicateFingerprintCount: Int,
    val potentiallyWeakCount: Int
) {
    val securityScore: Int
        get() {
            if (totalEntries == 0) return 100
            var penalty = 0
            penalty += expiredCount * 15
            penalty += noRotationCount * 5
            penalty += noFingerprintCount * 10
            penalty += duplicateFingerprintCount * 20
            penalty += potentiallyWeakCount * 8
            return (100 - penalty).coerceAtLeast(0)
        }
}

private fun performSurfaceAudit(entries: List<com.securevault.data.Entry>): SurfaceAuditResults {
    val expiredCount = entries.count { it.isPasswordExpired() }
    val noRotationCount = entries.count { !it.rotationEnabled }
    val noFingerprintCount = entries.count { it.passwordFingerprint.isNullOrBlank() }

    // Поиск дублирующихся fingerprint
    val fingerprints = entries.mapNotNull { it.passwordFingerprint }
    val duplicateFingerprintCount = fingerprints.size - fingerprints.toSet().size

    // Приблизительная оценка слабых паролей по длине зашифрованного текста
    // AES-GCM добавляет ~28 байт (IV 12 + tag 16) + base64 overhead (~33%)
    // Пароль длиной 10 символов → ~52 символа в base64
    // Пароль длиной 8 символов → ~48 символов в base64
    val potentiallyWeakCount = entries.count { entry ->
        entry.encryptedPassword.length < 50  // примерно соответствует паролю < 8 символов
    }

    return SurfaceAuditResults(
        totalEntries = entries.size,
        expiredCount = expiredCount,
        noRotationCount = noRotationCount,
        noFingerprintCount = noFingerprintCount,
        duplicateFingerprintCount = duplicateFingerprintCount,
        potentiallyWeakCount = potentiallyWeakCount
    )
}

// ===== ГЛУБОКИЙ АУДИТ =====

data class DeepAuditResults(
    val shortPasswords: List<String>,        // сервисы с паролем < 10 символов
    val duplicateChars: List<String>,        // сервисы с повторами символов
    val similarToPrevious: List<String>,     // сервисы с паролем <60% уникальным от предыдущего
    val reusedPasswords: List<String>        // сервисы с повторным использованием пароля
)

private fun performDeepAudit(entries: List<com.securevault.data.Entry>): DeepAuditResults {
    val shortPasswords = mutableListOf<String>()
    val duplicateChars = mutableListOf<String>()
    val similarToPrevious = mutableListOf<String>()
    val reusedPasswords = mutableListOf<String>()

    for (entry in entries) {
        try {
            val password = entry.password

            // 1. Проверка длины
            if (password.length < 10) {
                shortPasswords.add(entry.service)
            }

            // 2. Проверка повторов символов
            if (PasswordValidator.hasDuplicateCharacters(password)) {
                duplicateChars.add(entry.service)
            }

            // 3. Проверка 60% уникальности от предыдущего пароля
            val history = entry.getPasswordHistory()
            val lastHistoryItem = history.firstOrNull()
            if (lastHistoryItem?.encryptedOldPassword != null) {
                try {
                    val oldPassword = CryptoUtils.decrypt(lastHistoryItem.encryptedOldPassword)
                    if (!PasswordValidator.isAtLeast60PercentUnique(oldPassword, password)) {
                        similarToPrevious.add(entry.service)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            // 4. Проверка повторного использования пароля
            if (PasswordValidator.wasPasswordUsedForEntry(entry, password, LocalContextProvidedHolder.context)) {
                // Пароль уже использовался для этой записи
                // Но текущий пароль — это и есть "используемый", поэтому проверяем историю
                val historyFingerprints = history.map { it.passwordFingerprint }.filter { it.isNotBlank() }
                val currentFingerprint = PasswordValidator.buildPasswordFingerprint(password, LocalContextProvidedHolder.context)
                if (historyFingerprints.contains(currentFingerprint)) {
                    reusedPasswords.add(entry.service)
                }
            }
        } catch (e: Exception) {
            // Если не можем расшифровать — пропускаем
        }
    }

    return DeepAuditResults(
        shortPasswords = shortPasswords,
        duplicateChars = duplicateChars,
        similarToPrevious = similarToPrevious,
        reusedPasswords = reusedPasswords
    )
}

// Хелпер для передачи контекста в performDeepAudit
private object LocalContextProvidedHolder {
    lateinit var context: Context
}

// ===== UI КОМПОНЕНТЫ =====

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$value",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun DeepAuditResultsCard(results: DeepAuditResults) {
    val hasIssues = results.shortPasswords.isNotEmpty() ||
            results.duplicateChars.isNotEmpty() ||
            results.similarToPrevious.isNotEmpty() ||
            results.reusedPasswords.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasIssues) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (hasIssues) Icons.Default.Warning else Icons.Default.CheckCircle,
                    null,
                    tint = if (hasIssues) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Результаты глубокого аудита",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (hasIssues) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(12.dp))

            if (!hasIssues) {
                Text(
                    "✓ Все пароли прошли проверку",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            } else {
                if (results.shortPasswords.isNotEmpty()) {
                    AuditIssueItem(
                        icon = Icons.Default.ShortText,
                        title = "Короткие пароли (< 10 символов)",
                        services = results.shortPasswords,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (results.duplicateChars.isNotEmpty()) {
                    AuditIssueItem(
                        icon = Icons.Default.ContentCopy,
                        title = "Повторяющиеся символы",
                        services = results.duplicateChars,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (results.similarToPrevious.isNotEmpty()) {
                    AuditIssueItem(
                        icon = Icons.Default.CompareArrows,
                        title = "Слишком похожи на предыдущий (< 60%)",
                        services = results.similarToPrevious,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (results.reusedPasswords.isNotEmpty()) {
                    AuditIssueItem(
                        icon = Icons.Default.History,
                        title = "Повторно использованные пароли",
                        services = results.reusedPasswords,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditIssueItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    services: List<String>,
    color: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(8.dp))
            Text(
                "$title (${services.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        services.take(5).forEach { service ->
            Text(
                "  • $service",
                fontSize = 11.sp,
                color = color.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (services.size > 5) {
            Text(
                "  ... и ещё ${services.size - 5}",
                fontSize = 11.sp,
                color = color.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ===== РЕКОМЕНДАЦИИ =====

private fun buildRecommendations(
    surface: SurfaceAuditResults,
    deep: DeepAuditResults?
): List<String> {
    val recommendations = mutableListOf<String>()

    if (surface.expiredCount > 0) {
        recommendations.add("Обновить ${surface.expiredCount} просроченных паролей")
    }
    if (surface.noRotationCount > 0) {
        recommendations.add("Включить ротацию для ${surface.noRotationCount} записей")
    }
    if (surface.noFingerprintCount > 0) {
        recommendations.add("Выполнить backfill fingerprint для ${surface.noFingerprintCount} записей")
    }
    if (surface.duplicateFingerprintCount > 0) {
        recommendations.add("Проверить ${surface.duplicateFingerprintCount} записей с дублирующимися паролями")
    }
    if (surface.potentiallyWeakCount > 0) {
        recommendations.add("Усилить ${surface.potentiallyWeakCount} потенциально слабых паролей")
    }

    if (deep != null) {
        if (deep.shortPasswords.isNotEmpty()) {
            recommendations.add("Увеличить длину паролей в: ${deep.shortPasswords.take(3).joinToString(", ")}")
        }
        if (deep.duplicateChars.isNotEmpty()) {
            recommendations.add("Заменить пароли с повторами символов в: ${deep.duplicateChars.take(3).joinToString(", ")}")
        }
        if (deep.similarToPrevious.isNotEmpty()) {
            recommendations.add("Сильнее изменить пароли в: ${deep.similarToPrevious.take(3).joinToString(", ")}")
        }
        if (deep.reusedPasswords.isNotEmpty()) {
            recommendations.add("Не использовать старые пароли в: ${deep.reusedPasswords.take(3).joinToString(", ")}")
        }
    }

    return recommendations
}

// ===== ДИАЛОГ ПОДТВЕРЖДЕНИЯ =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmMasterPasswordForAudit(
    context: Context,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    //  Передаём контекст в holder для глубокого аудита
    LaunchedEffect(Unit) {
        LocalContextProvidedHolder.context = context
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Глубокий аудит") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Для глубокого аудита потребуется расшифровать все пароли. Это безопасная операция — данные не покидают устройство.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Мастер-пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val storedHash = prefs.getString("master_hash", null)
                val storedSalt = prefs.getString("master_salt", null)
                val iterations = prefs.getInt("master_iterations", 100_000)

                if (storedHash != null && storedSalt != null &&
                    MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
                    onConfirmed()
                } else {
                    error = "Неверный пароль"
                }
                password = ""
            }) {
                Text("Подтвердить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
