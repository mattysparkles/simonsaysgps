package com.simonsaysgps.service

import android.content.Context
import android.speech.tts.TextToSpeech
import com.simonsaysgps.domain.service.VoicePromptManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidVoicePromptManager @Inject constructor(
    @ApplicationContext private val context: Context
) : VoicePromptManager, TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts?.language = Locale.US
    }

    private fun ensureTts() {
        if (tts == null) tts = TextToSpeech(context, this)
    }

    override fun speak(prompt: String) {
        if (prompt.isBlank()) return
        ensureTts()
        if (ready) {
            tts?.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, "simonsays-prompt")
        }
    }

    override fun stop() {
        tts?.stop()
    }
}
