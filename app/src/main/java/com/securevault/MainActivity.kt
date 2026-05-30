package com.securevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.securevault.ui.SecureVaultNavHost
import com.securevault.ui.theme.SecureVaultTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecureVaultTheme {
                SecureVaultNavHost()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SecureVaultTheme {
        MaterialTheme {
            // Preview content
        }
    }
}
