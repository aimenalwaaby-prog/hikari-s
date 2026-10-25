package com.hikari.app.ui

/**
 * Country flags for provider rows, the CloudStream extension-list look: one
 * flag beside the extension's name naming which language the extension serves.
 *
 * Mapped from LANGUAGE, not guessed from names: an Aniyomi/manga pack's member
 * details already ARE the language names (see [ProviderPacks] — "Bengali",
 * "Hindi", …), so member rows and uniform-language packs resolve a flag. Every
 * other engine stores no per-extension language, and a missing flag is honest
 * where a guessed one would be a lie — [flagFor] returns null there and the
 * row draws exactly as before.
 */
object ProviderFlags {

    private val byToken: Map<String, String> = mapOf(
        "en" to "🇬🇧", "eng" to "🇬🇧", "english" to "🇬🇧",
        "hi" to "🇮🇳", "hin" to "🇮🇳", "hindi" to "🇮🇳",
        "bn" to "🇮🇳", "ben" to "🇮🇳", "bengali" to "🇮🇳", "bangla" to "🇮🇳",
        "ta" to "🇮🇳", "tam" to "🇮🇳", "tamil" to "🇮🇳",
        "te" to "🇮🇳", "tel" to "🇮🇳", "telugu" to "🇮🇳",
        "ml" to "🇮🇳", "mal" to "🇮🇳", "malayalam" to "🇮🇳",
        "mr" to "🇮🇳", "mar" to "🇮🇳", "marathi" to "🇮🇳",
        "kn" to "🇮🇳", "kan" to "🇮🇳", "kannada" to "🇮🇳",
        "gu" to "🇮🇳", "guj" to "🇮🇳", "gujarati" to "🇮🇳",
        "pa" to "🇮🇳", "pan" to "🇮🇳", "punjabi" to "🇮🇳",
        "ur" to "🇵🇰", "urd" to "🇵🇰", "urdu" to "🇵🇰",
        "ne" to "🇳🇵", "nep" to "🇳🇵", "nepali" to "🇳🇵",
        "si" to "🇱🇰", "sin" to "🇱🇰", "sinhala" to "🇱🇰", "sinhalese" to "🇱🇰",
        "ar" to "🇸🇦", "ara" to "🇸🇦", "arabic" to "🇸🇦",
        "es" to "🇪🇸", "spa" to "🇪🇸", "spanish" to "🇪🇸", "espanol" to "🇪🇸", "español" to "🇪🇸",
        "fr" to "🇫🇷", "fre" to "🇫🇷", "fra" to "🇫🇷", "french" to "🇫🇷",
        "de" to "🇩🇪", "ger" to "🇩🇪", "deu" to "🇩🇪", "german" to "🇩🇪", "deutsch" to "🇩🇪",
        "pt" to "🇧🇷", "por" to "🇧🇷", "portuguese" to "🇧🇷",
        "it" to "🇮🇹", "ita" to "🇮🇹", "italian" to "🇮🇹",
        "ru" to "🇷🇺", "rus" to "🇷🇺", "russian" to "🇷🇺",
        "ja" to "🇯🇵", "jpn" to "🇯🇵", "japanese" to "🇯🇵",
        "ko" to "🇰🇷", "kor" to "🇰🇷", "korean" to "🇰🇷",
        "zh" to "🇨🇳", "zho" to "🇨🇳", "chi" to "🇨🇳", "chinese" to "🇨🇳",
        "tr" to "🇹🇷", "tur" to "🇹🇷", "turkish" to "🇹🇷",
        "id" to "🇮🇩", "ind" to "🇮🇩", "indonesian" to "🇮🇩",
        "ms" to "🇲🇾", "may" to "🇲🇾", "msa" to "🇲🇾", "malay" to "🇲🇾",
        "th" to "🇹🇭", "tha" to "🇹🇭", "thai" to "🇹🇭",
        "vi" to "🇻🇳", "vie" to "🇻🇳", "vietnamese" to "🇻🇳",
        "fil" to "🇵🇭", "filipino" to "🇵🇭", "tagalog" to "🇵🇭",
        "tl" to "🇵🇭",
        "nl" to "🇳🇱", "dut" to "🇳🇱", "nld" to "🇳🇱", "dutch" to "🇳🇱",
        "pl" to "🇵🇱", "pol" to "🇵🇱", "polish" to "🇵🇱",
        "uk" to "🇺🇦", "ukr" to "🇺🇦", "ukrainian" to "🇺🇦",
        "fa" to "🇮🇷", "per" to "🇮🇷", "fas" to "🇮🇷", "persian" to "🇮🇷", "farsi" to "🇮🇷",
        "he" to "🇮🇱", "heb" to "🇮🇱", "hebrew" to "🇮🇱",
        "el" to "🇬🇷", "gre" to "🇬🇷", "ell" to "🇬🇷", "greek" to "🇬🇷",
        "ro" to "🇷🇴", "rum" to "🇷🇴", "ron" to "🇷🇴", "romanian" to "🇷🇴",
        "hu" to "🇭🇺", "hun" to "🇭🇺", "hungarian" to "🇭🇺",
        "cs" to "🇨🇿", "cze" to "🇨🇿", "ces" to "🇨🇿", "czech" to "🇨🇿",
        "sv" to "🇸🇪", "swe" to "🇸🇪", "swedish" to "🇸🇪",
        "no" to "🇳🇴", "nor" to "🇳🇴", "norwegian" to "🇳🇴",
        "da" to "🇩🇰", "dan" to "🇩🇰", "danish" to "🇩🇰",
        "fi" to "🇫🇮", "fin" to "🇫🇮", "finnish" to "🇫🇮",
    )

    /**
     * The flag for one language token ("Hindi", "hi", "hi-IN" all resolve), or
     * null when the token names no language with a flag — "Default",
     * "Source 1", "Multi", "All" and friends stay flagless.
     */
    fun flagFor(token: String): String? {
        // Tags arrive as "hi", "hi-IN" and "hi_IN" — the primary subtag is the
        // language; the region is not what separates two feeds of one site.
        val code = token.trim().lowercase().substringBefore('-').substringBefore('_')
        if (code.isBlank()) return null
        return byToken[code]
    }

    /**
     * The flag for a collapsed pack row: its members' flag when EVERY named
     * member resolves to the SAME one, else null (a mixed-language pack gets
     * no single flag — its members carry their own once expanded).
     */
    fun flagForPack(pack: ProviderPack): String? {
        if (!pack.isPack || pack.cross) return null
        var flag: String? = null
        for (i in pack.members.indices) {
            val f = flagFor(pack.memberLabel(i)) ?: return null
            if (flag == null) flag = f
            else if (flag != f) return null
        }
        return flag
    }
}
