package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.CustomProfile
import com.securevault.data.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    val profiles: StateFlow<List<CustomProfile>> = repository.allCustomProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insert(profile: CustomProfile) = viewModelScope.launch {
        repository.insertProfile(profile)
    }

    fun delete(id: Int) = viewModelScope.launch {
        repository.deleteProfile(id)
    }
}
