package com.example.granta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CameraViewModel : ViewModel() {
    private val _isPhotoTaken = MutableStateFlow(false)
    val isPhotoTaken: StateFlow<Boolean> = _isPhotoTaken

    fun setPhotoTaken(isTaken: Boolean) {
        viewModelScope.launch {
            _isPhotoTaken.value = isTaken
        }
    }
}