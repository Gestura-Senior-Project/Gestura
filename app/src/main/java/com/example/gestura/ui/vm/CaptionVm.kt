package com.example.gestura.ui.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gestura.ai.GptGlossDecoder
import com.example.gestura.model.AslClassifier
import com.example.gestura.pipeline.SequenceRunner
import com.example.gestura.repo.LandmarkRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val uploading: Boolean = false,
    val glosses: List<String> = emptyList(),
    val english: String = "",
    val error: String? = null
)

class CaptionVm(
    private val repo: LandmarkRepo,
    private val openAiKey: () -> String,
    private val labelFor: (Int) -> String,
    private val classifierProvider: () -> AslClassifier
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun runPipeline(ctx: Context, uri: Uri) {
        _state.value = UiState(uploading = true)
        viewModelScope.launch {
            try {
                val (landmarks, T, _fps) = repo.uploadAndGetLandmarks(ctx, uri)
                val classifier = classifierProvider()
                val ids = SequenceRunner.run(classifier, landmarks, T, windowT = 32, hopT = 16)
                val glosses = ids.map(labelFor)
                val english = GptGlossDecoder.decode(openAiKey(), glosses)
                _state.value = UiState(uploading = false, glosses = glosses, english = english)
                classifier.close()
            } catch (e: Exception) {
                _state.value = UiState(uploading = false, error = e.message ?: "Unknown error")
            }
        }
    }
}

class CaptionVmFactory(
    private val repo: LandmarkRepo,
    private val openAiKey: () -> String,
    private val labelFor: (Int) -> String,
    private val classifierProvider: () -> AslClassifier
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CaptionVm::class.java))
        return CaptionVm(repo, openAiKey, labelFor, classifierProvider) as T
    }
}
