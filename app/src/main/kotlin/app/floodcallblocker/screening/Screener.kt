package app.floodcallblocker.screening

import app.floodcallblocker.core.PhoneNormalizer
import app.floodcallblocker.core.Verdict
import app.floodcallblocker.data.Repository
import app.floodcallblocker.data.ScreeningResult

/** Resolves a raw incoming number to an entry and lets the [Repository] decide (SPEC F-10 – F-12, F-22). */
class Screener(
    private val cache: EntryCache,
    private val repository: Repository,
    private val normalizer: () -> PhoneNormalizer,
) {
    /** Returns null when the call is not subject to monitoring (unknown number, no entry, paused). */
    suspend fun screen(rawNumber: String?): ScreeningResult? {
        val e164 = normalizer().normalize(rawNumber) ?: return null // F-12: ignore unknown/suppressed
        val entry = cache.lookup(e164) ?: return null
        return repository.screen(entry).takeIf { it.verdict != Verdict.ALLOW }
    }
}
