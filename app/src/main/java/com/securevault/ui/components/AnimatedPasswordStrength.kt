package com.securevault.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.utils.PasswordGenerator

@Composable
fun AnimatedPasswordStrength(
    strength: PasswordGenerator.Strength,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val targetProgress = strength.score / 4f
    
    LaunchedEffect(targetProgress) {
        progress.animateTo(
            targetValue = targetProgress,
            animationSpec = tween(durationMillis = 300)
        )
    }
    
    val color = when (strength) {
        PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
        PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary
        PasswordGenerator.Strength.STRONG -> Color(0xFF4CAF50)
        PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF2E7D32)
    }
    
    val label = when (strength) {
        PasswordGenerator.Strength.WEAK -> "Слабый"
        PasswordGenerator.Strength.MEDIUM -> "Средний"
        PasswordGenerator.Strength.STRONG -> "Надежный"
        PasswordGenerator.Strength.VERY_STRONG -> "Очень надежный"
    }
    
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.value)
                .height(6.dp)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
