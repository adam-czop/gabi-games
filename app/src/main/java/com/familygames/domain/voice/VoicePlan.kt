package com.familygames.domain.voice

/** One sentence for the speech synthesizer (ADR-030: the app no longer plays recordings). */
sealed interface VoiceStep {
    data class Speech(val text: String) : VoiceStep
}

/**
 * What the games say, built from the texts the parent typed (ADR-030):
 * 1) the photo's own question ("Ile jest jabłek?"), 2) the game's starter + the photo's name ("Gdzie jest" + "babcia"),
 * 3) nothing — without a Polish synthesizer, or without any text, the game works silently.
 */
object VoicePlan {
    fun question(promptText: String?, label: String?, prefixText: String?, ttsAvailable: Boolean): List<VoiceStep> = when {
        !ttsAvailable -> emptyList()
        !promptText.isNullOrBlank() -> listOf(VoiceStep.Speech(promptText.trim()))
        label.isNullOrBlank() -> emptyList()
        prefixText.isNullOrBlank() -> listOf(VoiceStep.Speech(label.trim()))
        else -> listOf(VoiceStep.Speech("${prefixText.trim()} ${label.trim()}?"))
    }

    /** Just the photo's name, e.g. after a right answer or a found pair. */
    fun label(label: String?, ttsAvailable: Boolean): List<VoiceStep> =
        if (ttsAvailable && !label.isNullOrBlank()) listOf(VoiceStep.Speech(label.trim())) else emptyList()

    /** A fixed sentence of a game ("A kuku!", "Co tu nie pasuje?"). */
    fun phrase(text: String, ttsAvailable: Boolean): List<VoiceStep> =
        if (ttsAvailable && text.isNotBlank()) listOf(VoiceStep.Speech(text)) else emptyList()
}
