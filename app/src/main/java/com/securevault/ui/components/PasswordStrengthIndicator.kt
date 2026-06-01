package com.securevault.ui.components

// ✅ FOUNDATION
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*

// ✅ MATERIAL3
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

// ✅ UI
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.securevault.utils.PasswordGenerator

@Composable
fun PasswordStrengthIndicator(
    strength: PasswordGenerator.Strength,
    modifier: Modifier = Modifier
) {
    val colorHex = when (strength) {
        PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
        PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary
        PasswordGenerator.Strength.STRONG -> MaterialTheme.colorScheme.primary
        PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF2E7D32)
    }
    
    val fillFraction = when (strength) {
        PasswordGenerator.Strength.WEAK -> 0.25f
        PasswordGenerator.Strength.MEDIUM -> 0.5f
        PasswordGenerator.Strength.STRONG -> 0.75f
        PasswordGenerator.Strength.VERY_STRONG -> 1f
    }
    
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(color = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(fillFraction)
                    .height(6.dp)
                    .background(color = colorHex)
            )
        }
        Text(
            text = strength.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colorHex,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
