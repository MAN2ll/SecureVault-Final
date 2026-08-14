package com.securevault.viewmodel

sealed class PasswordOperationResult {
    data class Success(val message: String = "") : PasswordOperationResult()
    data class Error(val message: String) : PasswordOperationResult()
}
