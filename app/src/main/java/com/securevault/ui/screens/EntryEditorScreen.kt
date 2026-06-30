@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.securevault.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel
import java.security.MessageDigest
import kotlin.random.Random

enum class CipherMethod(
    val label: String,
    val icon: String,
    val description: String,
    val scientificName: String,
    val complexity: Int
) {
    FMP("Фонемно-матричное", "M", "Матричная транспозиция + модульный сдвиг", "Phonetic-Matrix Transformation", 75),
    VMS("Векторный многомерный", "V", "Полиномиальный сдвиг с квадратичной зависимостью", "Vector Multidimensional Shift", 85),
    HID("Хэш-инъекция с диффузией", "H", "SHA-256 + XOR + диффузия Шеннона", "Hash Injection with Diffusion", 95),
    PPK("Полиалфавитная подстановка", "P", "Шифр Виженера с автоключом", "Polyalphabetic Substitution with Autokey", 80),
    BPI("Блочное перемешивание", "B", "Блочная перестановка + инверсия", "Block Permutation with Inversion", 70),
    SOFT("Мягкий (читаемый)", "S", "Транслит + мягкие замены", "Soft Translit Plus", 60)
}

data class TransformationStep(
