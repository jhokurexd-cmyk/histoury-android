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
        current = info
    }

    fun clear() {
        current = null
    }
}
