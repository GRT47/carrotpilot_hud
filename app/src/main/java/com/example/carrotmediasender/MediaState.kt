package com.example.carrotmediasender

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object MediaState {
    private val _title = MutableStateFlow("?�생 중인 곡이 ?�습?�다")
    val title: StateFlow<String> = _title
    
    private val _artist = MutableStateFlow("")
    val artist: StateFlow<String> = _artist
    
    private val _themeMode = MutableStateFlow("auto")
    val themeMode: StateFlow<String> = _themeMode
    
    fun update(t: String, a: String) {
        _title.value = t
        _artist.value = a
    }
    
    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }
}
