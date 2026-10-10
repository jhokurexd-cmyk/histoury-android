package com.histoury.app.data.repository

/**
 * Carries the [AccountStatus.Suspended] details from wherever a suspension
 * is detected (LoginViewModel, AuthGate, or MainActivity's live
 * listener) to SuspendedScreen.
 *
 * A plain object instead of nav arguments because the note is free text
 * from an admin and could contain characters that are awkward to encode
 * safely into a route string. This only needs to survive the single
 * navigation call that immediately follows setting it, not process death,
 * so an in-memory holder is enough — same category of tradeoff as
 * ArrivalEventBus elsewhere in the app.
 */
object SuspensionInfoHolder {

    var current: AccountStatus.Suspended? = null
        private set

    fun set(info: AccountStatus.Suspended) {
        // Two detectors can race (MainActivity's live listener and
        // LoginViewModel): whichever signs out second can't read a token
        // anymore, so don't let it wipe the one the first one captured.
        val previous = current
        current = if (
            info.appealToken == null &&
            previous?.uid == info.uid &&
            previous.appealToken != null
        ) {
            info.copy(appealToken = previous.appealToken)
        } else {
            info
        }
    }

    fun clear() {
        current = null
    }
}
