package com.example.gestura.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestura.data.AvatarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch



data class AvatarUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val videoUrl: String? = null
)

class AvatarViewModel(
    private val repo: AvatarRepository = AvatarRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(AvatarUiState())
    val state = _state.asStateFlow()

    fun generate(text: String) {
        if (text.isBlank()) {
            _state.value = AvatarUiState(error = "Enter text")
            return
        }
        viewModelScope.launch {
            _state.value = AvatarUiState(loading = true)
            runCatching { repo.generate(text) }
                .onSuccess { url -> _state.value = AvatarUiState(videoUrl = url) }
                .onFailure { e -> _state.value = AvatarUiState(error = e.message ?: "Request failed") }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
