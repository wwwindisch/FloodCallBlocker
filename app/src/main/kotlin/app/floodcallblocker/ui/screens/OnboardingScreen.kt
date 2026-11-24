package app.floodcallblocker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.floodcallblocker.R

private const val STEPS = 4

/** First-run wizard with at most four steps (SPEC F-60). */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val prerequisites = rememberPrerequisites()
    val state = prerequisites.state

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            LinearProgressIndicator(progress = { (step + 1f) / STEPS }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            val (title, body) =
                when (step) {
                    0 -> R.string.onboarding_1_title to R.string.onboarding_1_body
                    1 -> R.string.onboarding_2_title to R.string.onboarding_2_body
                    2 -> R.string.onboarding_3_title to R.string.onboarding_3_body
                    else -> R.string.onboarding_4_title to R.string.onboarding_4_body
                }
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            when (step) {
                1 ->
                    StepAction(
                        state.screeningRole,
                        R.string.onboarding_role_done,
                        R.string.onboarding_role_action,
                        prerequisites.requestScreeningRole,
                    )
                2 ->
                    StepAction(
                        state.notifications,
                        R.string.onboarding_notif_done,
                        R.string.onboarding_notif_action,
                        prerequisites.requestNotifications,
                    )
                3 ->
                    StepAction(
                        state.contacts,
                        R.string.onboarding_contacts_done,
                        R.string.onboarding_contacts_action,
                        prerequisites.requestContacts,
                    )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) {
                TextButton(onClick = { step-- }) { Text(stringResource(R.string.action_back)) }
            } else {
                Spacer(Modifier.height(0.dp))
            }
            val optional = step >= 2
            val canContinue = step != 1 || state.screeningRole
            Row {
                if (step == 1 && !state.screeningRole) {
                    TextButton(onClick = { step++ }) { Text(stringResource(R.string.onboarding_later)) }
                }
                if (step < STEPS - 1) {
                    Button(onClick = { step++ }, enabled = canContinue || optional) {
                        Text(stringResource(R.string.action_next))
                    }
                } else {
                    Button(onClick = onFinished) { Text(stringResource(R.string.action_finish)) }
                }
            }
        }
    }
}

@Composable
private fun StepAction(
    granted: Boolean,
    doneText: Int,
    actionText: Int,
    onClick: () -> Unit,
) {
    if (granted) {
        Text(stringResource(doneText), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    } else {
        Button(onClick = onClick) { Text(stringResource(actionText)) }
    }
}
