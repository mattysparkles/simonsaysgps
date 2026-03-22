package com.simonsaysgps.domain.service

interface VoicePromptManager {
    fun speak(prompt: String)
    fun stop()
}
