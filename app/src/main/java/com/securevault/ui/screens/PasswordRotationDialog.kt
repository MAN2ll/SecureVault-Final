package com.securevault.ui.screens

import androidx.compose.runtime.Composable
import com.securevault.data.Entry
import com.securevault.ui.components.UnifiedPasswordGeneratorDialog

@Composable
fun PasswordRotationDialog(
    currentEntryId: String,
    serviceName: String,
    currentHint: String?,
    generationType: String,
    rotationMonth: Int?,
    rotationYear: Int?,
    allProfileEntries: List<Entry>,
    onPasswordReplaced: (
        newPassword: String,
        newHint: String?,
        newGenerationType: String,
        newMnemonicPhraseHint: String?,
        newMnemonicOptionsJson: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    UnifiedPasswordGeneratorDialog(
        onDismiss = onDismiss,
        onGenerated = { password, type, hint, phraseHint, json ->
            onPasswordReplaced(password, hint, type, phraseHint, json)
        },
        initialServiceName = serviceName,
        initialMode = 3,
        ampgOnly = true,
        initialYear = rotationYear
    )
}
