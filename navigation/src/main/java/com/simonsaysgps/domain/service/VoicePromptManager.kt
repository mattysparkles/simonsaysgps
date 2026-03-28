package com.simonsaysgps.domain.service

import com.simonsaysgps.domain.model.PromptPersonality

interface VoicePromptManager {
    fun speak(
        prompt: String,
        personality: PromptPersonality = PromptPersonality.CLASSIC_SIMON,
        isPreview: Boolean = false
    )
    fun stop()
}
