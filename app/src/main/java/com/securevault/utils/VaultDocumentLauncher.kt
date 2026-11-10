package com.securevault.utils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.securevault.security.privacyHost

interface VaultDocumentHost {
    fun beginVaultDocumentPicker(request: Any): Boolean
    fun finishVaultDocumentPicker(request: Any): Boolean
}

/** Keep the initiating screen until the system file chooser delivers its result. */
@Composable
fun <I, O> rememberVaultDocumentLauncher(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): (I) -> Unit {
    val host = LocalContext.current.privacyHost() as? VaultDocumentHost
    val request = remember { Any() }
    val launcher = rememberLauncherForActivityResult(contract) { result ->
        if (host?.finishVaultDocumentPicker(request) != false) onResult(result)
    }
    return { input ->
        if (host?.beginVaultDocumentPicker(request) != false) {
            try {
                launcher.launch(input)
            } catch (error: RuntimeException) {
                host?.finishVaultDocumentPicker(request)
                throw error
            }
        }
    }
}
