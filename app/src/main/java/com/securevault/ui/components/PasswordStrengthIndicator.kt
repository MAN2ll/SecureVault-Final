package com.securevault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
    val animatedProgress by animateFloatAsState(
        targetValue = strength.score / 4f,
        label = "strengthAnimation"
    )
    
    val color = Color(android.graphics.Color.parseColor(strength.colorHex))
    
    Column(modifier = modifier) {
        // Шкала
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .background(color)
            )
        }
        
        // Текст
        Text(
            text = when (strength) {
                PasswordGenerator.Strength.WEAK -> "Слабый"
                PasswordGenerator.Strength.MEDIUM -> "Средний"
                PasswordGenerator.Strength.STRONG -> "Надёжный"
                PasswordGenerator.Strength.VERY_STRONG -> "Очень надёжный"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
