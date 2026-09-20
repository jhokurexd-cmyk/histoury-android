package com.histoury.app.ui.screens.legal

/**
 * Legal document content, structured as sections for clean rendering.
 *
 * Text mirrors the team's approved drafts (July 9, 2026) with three
 * adjustments for Philippine-law alignment:
 *  1. User Rights explicitly cites RA 10173 (Data Privacy Act of 2012)
 *     and the right to complain to the National Privacy Commission.
 *  2. Children's Privacy references parental consent for minors under
 *     Philippine law alongside the under-13 convention.
 *  3. Contact points to the in-app Support & Feedback section.
 *
 * Revised September 20, 2026 to match what the app actually collects,
 * which had drifted from the July 9 draft:
 *  4. Location Information now discloses BACKGROUND location (the app
 *     holds ACCESS_BACKGROUND_LOCATION and delivers arrival notifications
 *     via geofencing while closed — see GeofenceBroadcastReceiver and
 *     BootReceiver), not just foreground GPS use as the old draft said.
 *  5. Information We Collect now lists photos (AR captures and profile
 *     pictures, both uploaded to Firebase Storage — see
 *     ArCaptureRepository) and camera access, which the old draft never
 *     mentioned at all.
 *  6. Data Sharing now names the specific third-party processors in use
 *     (Google Maps / ARCore, Open-Meteo for weather) instead of only the
 *     generic "Firebase services".
 */

data class LegalSection(
    val heading: String,
    val body: String
)

data class LegalDocument(
    val title: String,
    val effectiveDate: String,
    val intro: String,
    val sections: List<LegalSection>,
    val closing: String
)

object LegalContent {

    const val TYPE_PRIVACY = "privacy"
    const val TYPE_TERMS = "terms"

    fun documentFor(type: String): LegalDocument {
        return if (type == TYPE_TERMS) termsAndConditions else privacyPolicy
    }

    val privacyPolicy = LegalDocument(
        title = "Privacy Policy",
        effectiveDate = "September 20, 2026",
        intro = "Welcome to Histoury. Your privacy is important to us. This Privacy Policy explains how Histoury collects, uses, stores, and protects your information when you use our mobile application.",
        sections = listOf(
            LegalSection(
                heading = "1. About Histoury",
                body = "Histoury is a location-based mobile application developed as a Bachelor of Science in Information Technology (BSIT) capstone project. The application enhances visitors' experiences in Intramuros through historical information, GPS geofencing, augmented reality (AR), and itinerary recommendations."
            ),
            LegalSection(
                heading = "2. Information We Collect",
                body = "Depending on how you use the application, we may collect the following information:\n\nPersonal Information\n• Name\n• Email address\n• User account information\n• Profile photo, if you choose to upload one\n\nLocation Information\n• Your device's precise (GPS) location while using the application, to enable geofencing and location-based historical content.\n• Your device's location in the background — even while the app is closed or not in active use — so arrival notifications for nearby heritage sites can still be delivered. You can disable this at any time in your device's app permission settings; doing so means geofencing and arrival notifications will no longer work.\n\nCamera and Photos\n• Camera access, used only while you are actively using the Augmented Reality (AR) feature.\n• Photos you capture through the AR feature, saved to your personal in-app gallery.\n\nUsage Information\n• Visited heritage sites\n• AR feature activations\n• Ratings and feedback\n• App usage statistics"
            ),
            LegalSection(
                heading = "3. How We Use Your Information",
                body = "Your information is used to:\n• Authenticate your account.\n• Provide location-based historical content.\n• Send arrival notifications when you are near a historical site, including while the app is running in the background.\n• Recommend nearby attractions and itineraries.\n• Store and display your profile photo and AR photo captures within the app.\n• Improve application performance and user experience.\n• Maintain application security.\n• Analyze anonymous usage trends for academic research and system improvement."
            ),
            LegalSection(
                heading = "4. Data Storage",
                body = "User information is securely stored using Firebase services. Reasonable administrative and technical safeguards are implemented to help protect your data from unauthorized access, disclosure, alteration, or destruction."
            ),
            LegalSection(
                heading = "5. Data Sharing",
                body = "Histoury does not sell or rent your personal information.\n\nInformation may only be shared:\n• When required by applicable law.\n• With trusted service providers necessary to operate the application, namely:\n  – Firebase (Google) — account authentication, database, photo/file storage, and the serverless functions that send one-time email verification codes.\n  – Google Maps and ARCore/Google Play Services — maps, directions, and augmented reality positioning.\n  – Open-Meteo — current weather conditions for Intramuros. This request uses a fixed reference point for the district, not your personal location."
            ),
            LegalSection(
                heading = "6. User Rights",
                body = "In accordance with the Data Privacy Act of 2012 (Republic Act No. 10173) and its Implementing Rules and Regulations, users have the right to:\n• Be informed about how their personal data is collected and processed.\n• Access their personal information.\n• Correct inaccurate or outdated information.\n• Object to the processing of their personal data.\n• Delete their account and request erasure or blocking of their personal data.\n• Withdraw consent where applicable.\n• Be indemnified for damages resulting from misuse of their personal data.\n• Lodge a complaint with the National Privacy Commission (NPC).\n\nRequests may be submitted through the Support & Feedback section of the application."
            ),
            LegalSection(
                heading = "7. Data Retention",
                body = "Personal information will only be retained for as long as necessary to provide the application's services, support the academic project, or comply with applicable legal obligations."
            ),
            LegalSection(
                heading = "8. Children's Privacy",
                body = "Histoury is not specifically intended for children under 13 years of age. We do not knowingly collect personal information from children without appropriate consent. Consistent with Philippine law, the personal data of minors (persons under 18 years of age) is processed only with the consent of a parent or legal guardian where such consent is required."
            ),
            LegalSection(
                heading = "9. Changes to this Privacy Policy",
                body = "This Privacy Policy may be updated as the application evolves. Any significant changes will be reflected by updating the Effective Date."
            ),
            LegalSection(
                heading = "10. Contact Information",
                body = "For questions regarding this Privacy Policy or your personal data, please contact the Histoury Development Team through the Support & Feedback section of the application."
            )
        ),
        closing = "By using Histoury, you acknowledge that you have read and understood this Privacy Policy and consent to the collection and use of your information as described above."
    )

    val termsAndConditions = LegalDocument(
        title = "Terms and Conditions",
        effectiveDate = "September 20, 2026",
        intro = "Welcome to Histoury. By creating an account or using the application, you agree to the following Terms and Conditions.",
        sections = listOf(
            LegalSection(
                heading = "1. Acceptance of Terms",
                body = "By accessing or using Histoury, you agree to comply with these Terms and Conditions. If you do not agree, please discontinue use of the application."
            ),
            LegalSection(
                heading = "2. Purpose of the Application",
                body = "Histoury is an educational and tourism-support application developed as a BSIT capstone project. The application provides historical information, location-based services, augmented reality experiences, and itinerary recommendations for visitors to Intramuros."
            ),
            LegalSection(
                heading = "3. User Responsibilities",
                body = "Users agree to:\n• Provide accurate registration information.\n• Keep their login credentials secure.\n• Use the application responsibly.\n• Avoid attempting to disrupt, damage, or misuse the application or its services."
            ),
            LegalSection(
                heading = "4. Account Security",
                body = "Users are responsible for maintaining the confidentiality of their accounts. Histoury is not responsible for unauthorized access resulting from a user's failure to protect their login credentials."
            ),
            LegalSection(
                heading = "5. Location Services",
                body = "Some features require GPS location services, including access to your location in the background so arrival notifications can be delivered when you are near a historical site even while the app is closed. Users may disable location permissions (foreground or background) at any time in their device settings; however, certain features such as geofencing, arrival notifications, and nearby historical content may not function correctly without them."
            ),
            LegalSection(
                heading = "6. Intellectual Property",
                body = "All application designs, historical content, graphics, logos, software components, and augmented reality materials are owned by or used with permission by the Histoury Development Team or their respective copyright holders.\n\nUsers may not reproduce, modify, distribute, or commercially use any part of the application without permission."
            ),
            LegalSection(
                heading = "7. User Feedback",
                body = "Feedback, ratings, and suggestions submitted through Histoury may be used to improve the application. Users should avoid submitting unlawful, offensive, or misleading content."
            ),
            LegalSection(
                heading = "8. Disclaimer",
                body = "While Histoury strives to provide accurate historical information, some content is intended for educational purposes and may be updated as new verified information becomes available.\n\nThe application is provided on an \u201cas is\u201d and \u201cas available\u201d basis without warranties of uninterrupted or error-free operation."
            ),
            LegalSection(
                heading = "9. Limitation of Liability",
                body = "The Histoury Development Team shall not be liable for:\n• GPS inaccuracies.\n• Temporary service interruptions.\n• Internet connectivity issues.\n• Device compatibility limitations.\n• Any indirect or consequential damages arising from the use of the application."
            ),
            LegalSection(
                heading = "10. Suspension or Termination",
                body = "Histoury reserves the right to suspend or terminate accounts that violate these Terms and Conditions or misuse the application."
            ),
            LegalSection(
                heading = "11. Changes to the Terms",
                body = "These Terms and Conditions may be updated from time to time. Continued use of the application after updates constitutes acceptance of the revised Terms."
            ),
            LegalSection(
                heading = "12. Governing Law",
                body = "These Terms and Conditions shall be interpreted in accordance with the laws of the Republic of the Philippines."
            )
        ),
        closing = "By using Histoury, you acknowledge that you have read, understood, and agreed to these Terms and Conditions."
    )
}
