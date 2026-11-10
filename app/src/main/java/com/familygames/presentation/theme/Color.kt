package com.familygames.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Muted v2 palette (docs/08 §2): the UI is a quiet background, the photos bring the colour.
 * One accent (sage) per child screen; Mist only tells the Puzzle tile apart in S01;
 * Honey is reserved for stars and the gentle "not this pair" hint. Danger only in the parent zone.
 */
object Palette {
    // neutrals
    val Bg = Color(0xFFF6F4F0)
    val Bg2 = Color(0xFFEDE9E3)
    val Surface = Color(0xFFFFFFFF)
    val Line = Color(0xFFE3DED6)
    val Line2 = Color(0xFFD6D0C6)
    val Ink = Color(0xFF3B3936)
    val Ink2 = Color(0xFF8B867F)
    val Ink3 = Color(0xFFB4AEA5)

    /** Dot pattern on the card back (mockup `.card .back`). */
    val CardDot = Color(0xFFE2DDD5)

    // accent
    val Accent = Color(0xFF7E9C8C)
    val AccentInk = Color(0xFF4E6A5C)
    val AccentSoft = Color(0xFFE5ECE7)

    // supporting
    val Mist = Color(0xFF7F95A6)
    val MistSoft = Color(0xFFE6ECF0)
    val Honey = Color(0xFFD6B47A)
    val HoneySoft = Color(0xFFF4EBDA)

    // parent zone only
    val ParentInk = Color(0xFF2F2D2A)
    val ParentInk2 = Color(0xFF6F6A63)
    val ParentLine = Color(0xFFE6E1DA)
    val Danger = Color(0xFFA9573F)

    /** Very light neutral photo placeholders (docs/08 §2) — the photo stays the only coloured element. */
    val PhotoPlaceholders = listOf(Color(0xFFF1EDE7), Color(0xFFEDF0F1), Color(0xFFEEF1EC))

    /** Confetti colours (docs/08 §5). */
    val Confetti = listOf(Accent, Honey, Mist, Color(0xFFCFC6BA))

    /** Crayon colours of Kolorowanki – the only fuller colours in the app, because they are content (docs/12 M18). */
    val Paints = listOf(
        Color(0xFFE07A5F), Color(0xFFF2CC8F), Color(0xFF81B29A), Color(0xFF3D85C6),
        Color(0xFF9C89B8), Color(0xFFF4A6A6), Color(0xFF8D6E63), Color(0xFF3B3936),
    )
}
