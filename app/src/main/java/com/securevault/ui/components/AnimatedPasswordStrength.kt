package com.securevault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    val strengthColor = when (strength) {
        PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
        PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary
        PasswordGenerator.Strength.STRONG -> MaterialTheme.colorScheme.primary
        PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF2E7D32)
    }
    
    val barWidth = when (strength) {
        PasswordGenerator.Strength.WEAK -> 60.dp
        PasswordGenerator.Strength.MEDIUM -> 120.dp
        PasswordGenerator.Strength.STRONG -> 180.dp
        PasswordGenerator.Strength.VERY_STRONG -> 240.dp
    }
    
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Надёжность:", fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(6.dp)
                    .background(color = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(6.dp)
                        .background(color = strengthColor)
                )
            }
        }
        Text(
            text = strength.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = strengthColor,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
