package app.floodcallblocker.core

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/** A successfully recognised phone number. */
data class ParsedNumber(
    /** E.164, e.g. `+4937521437183`. */
    val e164: String,
    /** International format for display, e.g. `+49 37521 437183`. */
    val formatted: String,
    /** ISO region of the number, e.g. `DE`, or null when unknown. */
    val region: String?,
) {
    /** Localised country name for display, or null. */
    fun countryName(locale: Locale = Locale.getDefault()): String? =
        region?.let {
            Locale
                .Builder()
                .setRegion(it)
                .build()
                .getDisplayCountry(locale)
                .ifBlank { null }
        }
}

/** Normalises phone numbers to E.164 using libphonenumber. */
class PhoneNormalizer(
    private val defaultRegion: String = "DE",
) {
    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /** Returns the parsed number or null for blank, unknown, suppressed or invalid input. */
    fun parse(raw: String?): ParsedNumber? {
        if (raw.isNullOrBlank()) return null
        val cleaned = cleanup(raw)
        if (cleaned.isEmpty()) return null
        val number =
            try {
                util.parse(cleaned, defaultRegion.uppercase(Locale.ROOT))
            } catch (_: NumberParseException) {
                return null
            }
        if (!util.isValidNumber(number)) return null
        return ParsedNumber(
            e164 = util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164),
            formatted = util.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL),
            region = util.getRegionCodeForNumber(number),
        )
    }

    /** Shortcut returning only the E.164 string. */
    fun normalize(raw: String?): String? = parse(raw)?.e164

    private fun cleanup(raw: String): String {
        var s = raw.trim()
        // "00" is the international prefix in most countries; libphonenumber needs "+".
        // Handled after dropping the "(0)" convention below.
        s = TRUNK_IN_PARENS.replace(s, "")
        s = s.replace(Regex("[^+\\d]"), "")
        if (s.startsWith("00") && !s.startsWith("+")) s = "+" + s.removePrefix("00")
        return s
    }

    companion object {
        /** `(0)` after a country code, as in "+49 (0) 37521-437183". */
        private val TRUNK_IN_PARENS = Regex("\\(\\s*0\\s*\\)")
    }
}
