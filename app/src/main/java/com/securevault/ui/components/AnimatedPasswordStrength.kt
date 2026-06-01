package com.securevault.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.securevault.utils.PasswordGenerator

@Composable
fun AnimatedPasswordStrength(
    strength: PasswordGenerator.Strength,
    modifier: Modifier = Modifier
) {
    val targetFraction by animateFloatAsState(
        targetValue = when (strength) {
            PasswordGenerator.Strength.WEAK -> 0.25f
            PasswordGenerator.Strength.MEDIUM -> 0.5f
            PasswordGenerator.Strength.STRONG -> 0.75f
            PasswordGenerator.Strength.VERY_STRONG -> 1f
        },
        animationSpec = tween(durationMillis = 300),
        label = "strengthFraction"
    )
    
    val targetColor by animateColorAsState(
        targetValue = when (strength) {
            PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
            PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary
            PasswordGenerator.Strength.STRONG -> MaterialTheme.colorScheme.primary
            PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF2E7D32)
        },
        animationSpec = tween(durationMillis = 300),
        label = "strengthColor"
    )
    
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Надёжность:", fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(targetFraction)
                        .height(6.dp)
                        .background(targetColor)
                )
            }
        }
        Text(
            text = strength.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = targetColor,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
