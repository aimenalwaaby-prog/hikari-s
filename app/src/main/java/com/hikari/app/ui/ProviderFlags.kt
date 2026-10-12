package com.hikari.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Country/language flags for extension rows — REAL flag images, not text.
 *
 * The image is the country's actual flag (flagcdn, w80 PNG) with the emoji
 * flag as the offline fallback, so a row never shows a broken tile. The
 * mapping reads what the repo listing (or the installed name) already says —
 * language words ("Hindi", "English", "Bangla", …), Bangladesh markers
 * ("BD", "Bangla", "Dhaka", "BDIX") and a small table of well-known
 * extension names whose country the name alone declares. Anything that names
 * no country gets NO flag — never guessed.
 */
object ProviderFlags {
    /** Real flag image for an ISO code ("BD" → flagcdn w80 PNG). */
    fun urlOf(code: String): String =
        "https://flagcdn.com/w80/" + code.lowercase() + ".png"

    /** Emoji flag for an ISO code — the offline fallback, never networked. */
    fun emojiOf(code: String): String {
        val up = code.uppercase()
        if (up.length != 2 || up.any { it !in 'A'..'Z' }) return "🏳"
        return String(Character.toChars(0x1F1E6 + (up[0] - 'A'))) +
            String(Character.toChars(0x1F1E6 + (up[1] - 'A')))
    }

    private fun has(hay: String, vararg words: String): Boolean = words.any { w ->
        Regex("(^|[^a-z])" + Regex.escape(w) + "([^a-z]|$)").containsMatchIn(hay)
    }

    // Well-known installed names whose country the name alone declares (the
    // reference provider list flags these: Emwbd/Cinefreak/Mlsbd/Banglaplex/
    // FibWatch/MovieLinkBD/BDIX on BD; Rtally/Movies4u/Castle/HDHub4U on IN;
    // UHDmovies/4K HDHUB on GB). Static name knowledge, not per-extension
    // behaviour — the rows play exactly as before either way.
    private fun aliasOf(hay: String): String? = when {
        "hdhub4u" in hay -> "IN"
        "4khdhub" in hay -> "GB"
        "uhdmovies" in hay -> "GB"
        "movies4u" in hay -> "IN"
        "rtally" in hay -> "IN"
        "castle tv" in hay || "castletv" in hay -> "IN"
        "cinefreak" in hay -> "BD"
        "fibwatch" in hay -> "BD"
        "emwbd" in hay -> "BD"
        "mlsbd" in hay -> "BD"
        "movielinkbd" in hay -> "BD"
        "banglaplex" in hay -> "BD"
        "bdix" in hay -> "BD"
        else -> null
    }

    /**
     * (ISO code, language label) for the given texts, or null when nothing
     * names a country. Bangladesh is checked before the Indian languages:
     * "Bangla" is Bangladesh's own word while "Bengali" alone stays Indian.
     */
    fun pairOf(vararg texts: String?): Pair<String, String>? {
        val hay = texts.filterNotNull().joinToString(" ").lowercase()
        if (hay.isBlank()) return null
        aliasOf(hay)?.let { code ->
            return code to when (code) {
                "BD" -> "Bangla"
                "GB" -> "English"
                else -> "Desi"
            }
        }
        if (has(hay, "bangladesh", "bangla", "dhaka") ||
            Regex("""bd([^a-z]|$)""").containsMatchIn(hay)
        ) return "BD" to "Bangla"
        return when {
            has(hay, "hindi", "hindi-audio", "bollywood") -> "IN" to "Hindi"
            has(hay, "tamil") -> "IN" to "Tamil"
            has(hay, "telugu") -> "IN" to "Telugu"
            has(hay, "malayalam") -> "IN" to "Malayalam"
            has(hay, "kannada") -> "IN" to "Kannada"
            has(hay, "bengali") -> "IN" to "Bengali"
            has(hay, "marathi", "punjabi", "gujarati", "desi") -> "IN" to "Desi"
            has(hay, "english", "hollywood") -> "GB" to "English"
            has(hay, "spanish", "espanol", "español", "latino") -> "ES" to "Spanish"
            has(hay, "french", "francais", "français") -> "FR" to "French"
            has(hay, "german", "deutsch") -> "DE" to "German"
            has(hay, "italian") -> "IT" to "Italian"
            has(hay, "portuguese", "brasileiro") -> "BR" to "Portuguese"
            has(hay, "russian") -> "RU" to "Russian"
            has(hay, "turkish") -> "TR" to "Turkish"
            has(hay, "arabic") -> "SA" to "Arabic"
            has(hay, "japanese", "anime") -> "JP" to "Japanese"
            has(hay, "korean", "kdrama", "k-drama") -> "KR" to "Korean"
            has(hay, "chinese", "donghua", "cdrama", "c-drama") -> "CN" to "Chinese"
            has(hay, "indonesian", "indo") -> "ID" to "Indonesian"
            has(hay, "vietnamese") -> "VN" to "Vietnamese"
            has(hay, "thai") -> "TH" to "Thai"
            else -> null
        }
    }

    /** ISO country code for the given texts, or null when none is named. */
    fun codeOf(vararg texts: String?): String? = pairOf(*texts)?.first

    /**
     * "🇮🇳 Hindi"-style label for the given texts, or null when none is
     * named — the same strings the extension rows always printed.
     */
    fun flagOf(vararg texts: String?): String? {
        val (code, label) = pairOf(*texts) ?: return null
        return emojiOf(code) + " " + label
    }
}

/**
 * A real flag image ([ProviderFlags.urlOf]) with the emoji flag as the
 * offline fallback, so the row shows a flag with or without network.
 */
@Composable
fun FlagIcon(
    code: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var failed by remember(code) { mutableStateOf(false) }
    if (failed) {
        Text(
            ProviderFlags.emojiOf(code),
            fontSize = (size.value * 0.72f).sp,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = ProviderFlags.urlOf(code),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}
