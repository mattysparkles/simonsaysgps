package com.simonsaysgps.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.simonsaysgps.domain.model.PromptPersonality
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
    private var pendingUtterance: Triple<String, PromptPersonality, Boolean>? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.US
            configureBrandVoice(PromptPersonality.CLASSIC_SIMON, isPreview = false)
            pendingUtterance?.also { (prompt, personality, isPreview) ->
                pendingUtterance = null
                speak(prompt, personality, isPreview)
            }
        }
    }

    private fun ensureTts() {
        if (tts == null) tts = TextToSpeech(context, this)
    }

    override fun speak(prompt: String, personality: PromptPersonality, isPreview: Boolean) {
        if (prompt.isBlank()) return
        ensureTts()
        if (!ready) {
            pendingUtterance = Triple(prompt, personality, isPreview)
            return
        }
        configureBrandVoice(personality, isPreview)
        val utteranceId = if (isPreview) "simonsays-preview" else "simonsays-prompt"
        tts?.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
    }

    private fun configureBrandVoice(personality: PromptPersonality, isPreview: Boolean) {
        val engine = tts ?: return
        engine.voice = preferredSimonVoice(engine)
        engine.setPitch(
            when (personality) {
                PromptPersonality.CLASSIC_SIMON -> 0.74f
                PromptPersonality.SNARKY_SIMON -> 0.7f
                PromptPersonality.POLITE_SIMON -> 0.78f
            }
        )
        engine.setSpeechRate(
            when (personality) {
                PromptPersonality.CLASSIC_SIMON -> if (isPreview) 0.94f else 0.9f
                PromptPersonality.SNARKY_SIMON -> if (isPreview) 0.98f else 0.94f
                PromptPersonality.POLITE_SIMON -> if (isPreview) 0.92f else 0.88f
            }
        )
    }

    private fun preferredSimonVoice(engine: TextToSpeech) = engine.voices
        ?.asSequence()
        ?.filter { voice ->
            !voice.isNetworkConnectionRequired &&
                voice.locale?.language == Locale.US.language
        }
        ?.sortedWith(
            compareBy<Voice> { candidate ->
                val name = candidate.name.orEmpty().lowercase()
                when {
                    name.contains("male") || name.contains("masculine") || name.contains("man") || name.contains("boy") -> 0
                    name.contains("female") || name.contains("woman") || name.contains("girl") -> 3
                    else -> 1
                }
            }
                .thenBy { candidate ->
                    val name = candidate.name.orEmpty().lowercase()
                    if (name.contains("local")) 0 else 1
                }
                .thenByDescending { it.quality }
                .thenByDescending { it.latency }
                .thenBy { candidate ->
                    val name = candidate.name.orEmpty().lowercase()
                    when {
                        name.contains("male") -> 0
                        name.contains("en-us") || name.contains("en_us") -> 1
                        else -> 2
                    }
                }
        )
        ?.firstOrNull()
}
