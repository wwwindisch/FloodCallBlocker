package app.floodcallblocker.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import app.floodcallblocker.FloodApp
import app.floodcallblocker.core.BlockAction
import app.floodcallblocker.core.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Entry point of the Android telecom framework for incoming calls (SPEC F-20 – F-25). */
class FloodCallScreeningService : CallScreeningService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        val app = application as FloodApp
        val number = callDetails.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        scope.launch {
            // Fail-open: on timeout or any error the call simply passes.
            val response =
                try {
                    withTimeoutOrNull(SCREENING_TIMEOUT_MS) { app.container.screener.screen(number) }
                } catch (e: Exception) {
                    Log.e(TAG, "Screening failed, letting call through", e)
                    null
                }
            respondToCall(callDetails, buildResponse(response))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun buildResponse(result: app.floodcallblocker.data.ScreeningResult?): CallResponse {
        val builder = CallResponse.Builder()
        if (result == null || result.verdict == Verdict.ALLOW) return builder.build()
        when (result.action) {
            BlockAction.REJECT ->
                builder
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
                    .setSkipCallLog(false)

            BlockAction.SILENCE -> builder.setSilenceCall(true)
        }
        return builder.build()
    }

    private companion object {
        const val TAG = "FloodCallScreening"
        const val SCREENING_TIMEOUT_MS = 3_500L
    }
}
