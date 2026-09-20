package com.histoury.app.data.repository

/**
 * Tracks whether the person has scrolled all the way through the Terms &
 * Conditions and Privacy Policy screens during this app session.
 *
 * Registration's consent checkbox stays locked until both are true, so
 * people actually see the documents before agreeing to them — same idea
 * as SuspensionInfoHolder: this only needs to survive in-process
 * navigation between LegalScreen and RegisterScreen, not process death,
 * so a plain in-memory holder is enough.
 */
object LegalReadStatusHolder {

    var hasReadTerms: Boolean = false
        private set

    var hasReadPrivacy: Boolean = false
        private set

    fun markTermsRead() {
        hasReadTerms = true
    }

    fun markPrivacyRead() {
        hasReadPrivacy = true
    }
}
