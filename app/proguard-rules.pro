# SecureVault ProGuard rules

# Сохраняем имена для Hilt/Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# Сохраняем имена для Compose
-keep class androidx.compose.** { *; }

# Сохраняем имена для шифрования и безопасности
-keep class com.securevault.security.** { *; }
-keep class com.securevault.utils.CryptoUtils { *; }
-keep class com.securevault.utils.PasswordValidator { *; }
-keep class com.securevault.utils.MnemonicPasswordGenerator { *; }

# Сохраняем модели данных для JSON/Gson/Room
-keep class com.securevault.data.** { *; }
-keepclassmembers class com.securevault.data.** { *; }
