package com.histoury.app.ui.screens.legal

/**
 * Who users contact about support, privacy and their personal data.
 *
 * One place, read by the Support Center's Contact Us card AND quoted in the
 * Privacy Policy and Terms, so the documents never point at an address the
 * app doesn't show (or vice versa).
 *
 * BEFORE A PUBLIC RELEASE: replace these with the team's real, monitored
 * mailbox and number. Under the Data Privacy Act the contact details of the
 * personal information controller and its Data Protection Officer must be
 * real and reachable.
 */
object LegalContact {

    /** Name the app is operated under in the legal documents. */
    const val OPERATOR = "the Histoury Development Team"

    /** General support, and where privacy / data-subject requests go. */
    const val SUPPORT_EMAIL = "support@histoury.com"

    const val SUPPORT_PHONE = "+63 912 345 678"

    /** The Data Protection Officer is reached through the same mailbox. */
    const val DPO_EMAIL = SUPPORT_EMAIL

    /** Subject line pre-filled for privacy and account requests. */
    const val PRIVACY_REQUEST_SUBJECT = "Data Privacy Request"

    /** The National Privacy Commission's website, for complaints. */
    const val NPC_WEBSITE = "www.privacy.gov.ph"
}
