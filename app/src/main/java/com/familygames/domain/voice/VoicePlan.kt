package com.familygames.domain.voice

/** Spoken phrase starters (docs/12 M10.3), recorded once by the parent in S11 → "Głos". */
enum class PromptPrefix(val key: String) {
    WHERE_IS("prefix_where_is"),
    HOW_MANY("prefix_how_many"),
    ODD_ONE("prefix_odd_one"),
    PEEKABOO("prefix_peekaboo"),
    BRAVO("prefix_bravo"),
    TRY_AGAIN("prefix_try_again"),
}

/** One piece of a spoken prompt: a recorded clip or a sentence for the speech synthesizer. */
sealed interface VoiceStep {
    data class Clip(val path: String) : VoiceStep
    data class Speech(val text: String) : VoiceStep
}

/** What the prompt can be built from for one photo. */
data class PromptSources(
    /** Parent's recording of the whole question for this photo ("Gdzie jest babcia?"). */
    val questionClip: String?,
    /** Parent's recording of the prefix ("Gdzie jest…"). */
    val prefixClip: String?,
    /** Parent's recording of the photo's label ("babcia"). */
    val labelClip: String?,
    /** The written label. */
    val label: String?,
    /** The prefix as text for the synthesizer, e.g. "Gdzie jest". */
    val prefixText: String?,
    val ttsAvailable: Boolean,
)

/**
 * docs/12 M10.3 priority: 1) the recorded question, 2) recorded prefix + recorded label, 3) TTS of prefix + label,
 * 4) nothing (the game then works without voice). Empty list = no voice available.
 */
object VoicePlan {
    fun build(s: PromptSources): List<VoiceStep> = when {
        s.questionClip != null -> listOf(VoiceStep.Clip(s.questionClip))
        s.prefixClip != null && s.labelClip != null -> listOf(VoiceStep.Clip(s.prefixClip), VoiceStep.Clip(s.labelClip))
        s.ttsAvailable && !s.label.isNullOrBlank() -> listOf(VoiceStep.Speech(listOfNotNull(s.prefixText, s.label).joinToString(" ") + if (s.prefixText != null) "?" else ""))
        s.prefixClip != null && s.ttsAvailable && !s.label.isNullOrBlank() -> listOf(VoiceStep.Clip(s.prefixClip), VoiceStep.Speech(s.label))
        else -> emptyList()
    }

    /** Saying just the label after a right answer ("To jest babcia" is the parent's own recording if there is one). */
    fun label(labelClip: String?, label: String?, ttsAvailable: Boolean): List<VoiceStep> = when {
        labelClip != null -> listOf(VoiceStep.Clip(labelClip))
        ttsAvailable && !label.isNullOrBlank() -> listOf(VoiceStep.Speech(label))
        else -> emptyList()
    }
}
