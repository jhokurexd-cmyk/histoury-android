package com.histoury.app.ui.screens.legal

/**
 * The app's legal documents: Privacy Policy, Terms and Conditions, and
 * Legal Notices & Credits.
 *
 * Revised October 11, 2026 to match the app as it now works and to follow
 * Philippine law more closely:
 *  - Privacy Policy written to the Data Privacy Act of 2012 (RA 10173) and
 *    its IRR: who the controller and DPO are, each kind of data with its
 *    purpose and basis, recipients and cross-border transfers, retention
 *    per kind of data, the full list of data-subject rights (including
 *    portability), breach notification, minors, and complaints to the NPC.
 *  - Reflects current features: AR photos kept on the phone only (not
 *    uploaded), in-app arrival detection, background narration, route
 *    requests to OpenStreetMap-based routing services, commute estimates,
 *    hotel booking links (referral only), reviews and moderation,
 *    suspensions and appeals, itineraries auto-deleted after completion.
 *  - Removes claims that were no longer true (photos uploaded to Firebase
 *    Storage, "app usage statistics" — there is no analytics SDK).
 *  - Terms cover reviews, AR and road safety (RA 10913), conduct at
 *    heritage sites (RA 10066), third-party links and bookings, and
 *    account deletion requests.
 *
 * Contact details come from [LegalContact], which the Support Center shows
 * too. Keep these documents in step with the app: when a feature changes
 * what is collected or shared, update the matching section and the date.
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
    const val TYPE_NOTICES = "notices"

    private const val EFFECTIVE = "October 11, 2026"

    fun documentFor(type: String): LegalDocument = when (type) {
        TYPE_TERMS -> termsAndConditions
        TYPE_NOTICES -> legalNotices
        else -> privacyPolicy
    }

    // =====================================================================
    // Privacy Policy
    // =====================================================================

    val privacyPolicy = LegalDocument(
        title = "Privacy Policy",
        effectiveDate = EFFECTIVE,
        intro = "Histoury respects your privacy. This Privacy Policy explains what personal information the Histoury mobile application collects, why, how it is used, stored and shared, how long it is kept, and the rights you have over it under the Data Privacy Act of 2012 (Republic Act No. 10173), its Implementing Rules and Regulations, and the issuances of the National Privacy Commission (NPC).",
        sections = listOf(
            LegalSection(
                heading = "1. Who We Are",
                body = """
Histoury is a location-based heritage tourism application for Intramuros, Manila, created as a Bachelor of Science in Information Technology (BSIT) capstone project. It offers historical content, maps and navigation, itinerary planning, arrival detection, narration, reviews and augmented reality (AR) reconstructions.

The personal information controller is ${LegalContact.OPERATOR} ("we", "us"). Our Data Protection Officer (DPO) can be reached at ${LegalContact.DPO_EMAIL}.
""".trimIndent()
            ),
            LegalSection(
                heading = "2. Information We Collect",
                body = """
We collect only what the features you use need.

Account information (when you register)
• First and last name, and email address.
• Your password, which is handled by Firebase Authentication. We never see or store it in readable form.
• A one-time verification code sent to your email, kept in hashed form only until it is used or expires (10 minutes).
• Profile photo, only if you choose to add one.
• Account status, such as whether an account is suspended, and any appeal you submit.

Location information
• Your device's precise or approximate location, while you use the app: to show where you are on the map, give directions and turn-by-turn navigation, show distances, detect when you arrive at a site, and confirm you are at a site before AR opens.
• Your location in the background, only if you allow "Allow all the time": so arrival notifications still work when the app is closed (geofencing through Google Play services) and so navigation continues if you switch apps during a trip.
• Your raw GPS location is not stored on our servers. What is recorded is the site you arrived at and when (your visit history).

Content and activity you create
• Visit history: which heritage sites you visited and when.
• Itineraries: the stops, start time, travel mode and name of trips you plan.
• Favorites you save.
• Reviews and ratings you post, helpful votes, and reports you make about reviews.
• Feedback and messages you send through the Support Center.

Camera and photos
• Camera access, used only while the AR experience is open, to show and place the 3D reconstruction. Camera frames are processed on your device and are not recorded or uploaded.
• Photos you capture in AR are saved only on your phone, in the app's private storage and in a separate folder for each account. They are not uploaded to our servers. Photos you choose to "Save to device" are copied to your phone's own photo gallery.

Device and technical information
• Basic technical information our service providers process to deliver the app, such as your IP address when the app contacts our servers, map servers and routing services, and the app's settings stored on your phone (for example, appearance and drive options).
• Content you download for offline use (site text, narration audio, AR models) is stored on your phone.

We do not use advertising, we do not track you across other apps, and we do not sell your personal information.
""".trimIndent()
            ),
            LegalSection(
                heading = "3. How and Why We Use Your Information",
                body = """
• To create and secure your account, verify your email, and let you sign in. Basis: necessary to provide the service you asked for, and your consent.
• To provide location-based features: maps, directions, navigation, distances, arrival detection and notifications, and the AR at-site check. Basis: your consent (the location permission), which you can withdraw at any time.
• To save your visit history, itineraries, favorites and gallery, and to recommend nearby places that match your interests. Basis: necessary to provide the service.
• To publish your reviews and ratings, show site averages and rankings, and keep reviews safe through automatic text filtering, reports and moderation. Basis: necessary to provide the service and our legitimate interest in keeping the community safe.
• To respond to your feedback and support requests. Basis: your consent and our legitimate interest in improving the app.
• To enforce our Terms, prevent abuse (for example, rate limits and suspensions) and protect users. Basis: legitimate interest and, where applicable, legal obligation.
• To comply with law and lawful requests from authorities. Basis: legal obligation.

Reviews are checked automatically for profanity, links and contact details before they are posted. Decisions to suspend an account are made by a person, and you may appeal them in the app.
""".trimIndent()
            ),
            LegalSection(
                heading = "4. What Others Can See",
                body = """
• Your reviews are public to other Histoury users, shown with your name as it appears on your profile, your rating, the review text and its date.
• Everything else, including your email, location, visit history, itineraries, favorites, photos and feedback, is private to you and to authorized Histoury administrators who need it to run and moderate the service.
""".trimIndent()
            ),
            LegalSection(
                heading = "5. Sharing and Service Providers",
                body = """
We share personal information only with service providers that operate the app on our behalf, under their own security and privacy commitments, and only as needed:

• Google Firebase: account sign-in, the database, file storage for profile photos, and the server functions that run the app (including sending verification emails).
• Google Play services: device location and geofencing for arrival detection.
• Google Play Services for AR (ARCore): powers the AR experience. ARCore is provided by Google LLC and governed by the Google Privacy Policy.
• OpenFreeMap / OpenMapTiles: map images. Your phone downloads map tiles for the area you are viewing.
• openrouteservice (HeiGIT) and Stadia Maps: route calculation. When you ask for directions, the start and destination coordinates and travel mode are sent through our server to these services. No name or account details are sent.
• Open-Meteo: current weather, using a fixed reference point for Intramuros, not your location.

We may also disclose information when required by law, a court order, or a lawful request of a government authority, or to protect the rights, safety or property of users and the public.

Links you open from the app (a hotel's booking page, a place's website or Facebook page, Google Maps reviews, or source articles) take you to third-party services. Their own terms and privacy policies apply; we do not control them and share no account information with them.
""".trimIndent()
            ),
            LegalSection(
                heading = "6. Where Your Information Is Stored",
                body = """
Account and app data are stored on Google Cloud / Firebase servers, which may be located outside the Philippines (for example, in Singapore or the United States). Routing services may process coordinates in other countries (for example, Germany or the United States).

When information is transferred outside the Philippines we remain responsible for it, as required by the Data Privacy Act, and we use providers that apply security measures comparable to those required under Philippine law.

AR photos, offline downloads and narration audio stay on your phone.
""".trimIndent()
            ),
            LegalSection(
                heading = "7. How Long We Keep It",
                body = """
• Account and profile: while your account exists. When an account is deleted, its sign-in, profile, profile photo and favorites are removed; its reviews, helpful votes, visit history and itineraries are deleted; and reports, feedback and appeals it submitted are kept only with the link to the account removed (anonymized), for moderation and service records.
• Itineraries: until you delete them. Completed trips are deleted automatically 48 hours after completion.
• Email verification codes: until used or expired (10 minutes).
• Route requests: not stored; a short-lived temporary copy may be kept in server memory to answer repeated requests faster.
• Feedback and support messages: for as long as needed to address them and improve the app.
• AR photos and offline content on your phone: until you delete them, sign out and clear the app's data, or uninstall the app.

We may keep information longer only where the law requires it or to establish, exercise or defend legal claims.
""".trimIndent()
            ),
            LegalSection(
                heading = "8. How We Protect It",
                body = """
We use organizational, physical and technical security measures appropriate to the information, including encrypted connections (HTTPS), access rules that limit each user to their own data, restricted administrator access, hashed verification codes, rate limits against abuse, and app-private storage on your phone.

No system is completely secure. If a personal data breach occurs that is likely to give rise to a real risk of serious harm, we will notify the National Privacy Commission and affected users within the period required by NPC rules (72 hours from knowledge of the breach).
""".trimIndent()
            ),
            LegalSection(
                heading = "9. Your Rights",
                body = """
Under the Data Privacy Act you have the right to:
• Be informed of how your personal data is collected and processed.
• Access your personal data, and obtain a copy of it.
• Object to processing, including processing based on legitimate interest.
• Correct inaccurate or outdated data. You can edit your name, email, password and profile photo yourself in the app.
• Have your data erased or blocked, including deleting your account.
• Data portability: receive your data in an electronic, commonly used format.
• Withdraw your consent at any time, for example by turning off location, camera or notification permissions in your phone's settings. Withdrawing consent does not affect processing done before, but features that need it will stop working.
• Be indemnified for damages sustained due to inaccurate, incomplete, outdated, false, unlawfully obtained or unauthorized use of personal data.
• File a complaint with the National Privacy Commission (${LegalContact.NPC_WEBSITE}).

To exercise these rights, email our DPO at ${LegalContact.DPO_EMAIL} with the subject "${LegalContact.PRIVACY_REQUEST_SUBJECT}", or use "Privacy & data requests" in the Support Center. We may ask you to confirm your identity first, and we will respond within a reasonable time as required by law.
""".trimIndent()
            ),
            LegalSection(
                heading = "10. Permissions You Control",
                body = """
• Location (while using the app): maps, directions, distances, arrival detection and AR.
• Location (all the time, optional): arrival notifications while the app is closed.
• Camera: AR experience only.
• Notifications: arrival alerts, navigation and narration controls.

You can change any of these at any time in your phone's settings. Histoury keeps working without them, but the features that rely on them will not.
""".trimIndent()
            ),
            LegalSection(
                heading = "11. Children and Minors",
                body = """
Histoury is meant for general audiences and is not directed at children under 13. Users under 18 years of age should use Histoury only with the consent and supervision of a parent or legal guardian, who may contact us to access, correct or delete the minor's information. If we learn that we hold a minor's information without the required consent, we will delete it.
""".trimIndent()
            ),
            LegalSection(
                heading = "12. Changes to This Policy",
                body = """
We may update this Privacy Policy when the app or the law changes. The current version is always available in the app (Menu > Privacy Policy) with its effective date. For significant changes, we will make reasonable efforts to let you know before they take effect.
""".trimIndent()
            ),
            LegalSection(
                heading = "13. Contact Us",
                body = """
Data Protection Officer, ${LegalContact.OPERATOR}
Email: ${LegalContact.DPO_EMAIL}
Phone: ${LegalContact.SUPPORT_PHONE}

You can also reach us through Menu > Support Center.
""".trimIndent()
            )
        ),
        closing = "By using Histoury, you acknowledge that you have read and understood this Privacy Policy and consent to the processing of your personal information as described, where consent is the basis for it. You may withdraw your consent at any time as described in Section 9."
    )

    // =====================================================================
    // Terms and Conditions
    // =====================================================================

    val termsAndConditions = LegalDocument(
        title = "Terms and Conditions",
        effectiveDate = EFFECTIVE,
        intro = "These Terms and Conditions (\"Terms\") govern your use of the Histoury mobile application. By creating an account or using Histoury, you agree to these Terms and to our Privacy Policy. Under the Electronic Commerce Act (Republic Act No. 8792), your electronic acceptance has the same effect as a signed agreement. If you do not agree, please do not use the app.",
        sections = listOf(
            LegalSection(
                heading = "1. About Histoury",
                body = """
Histoury is a heritage tourism and education application for Intramuros, Manila, developed as a BSIT capstone project by ${LegalContact.OPERATOR}. It provides historical information, maps and navigation, itinerary planning, arrival detection, narration, reviews and augmented reality (AR) reconstructions.

Histoury is an independent project. It is not operated by, affiliated with, or endorsed by the Intramuros Administration or the owners of the heritage sites and businesses it features, unless stated otherwise.
""".trimIndent()
            ),
            LegalSection(
                heading = "2. Eligibility and Your Account",
                body = """
• Provide accurate information when you register and keep it up to date.
• Keep your password secure and do not share your account. You are responsible for activity under your account.
• If you are under 18, use Histoury only with the consent and supervision of a parent or legal guardian.
• Tell us promptly at ${LegalContact.SUPPORT_EMAIL} if you believe your account has been used without your permission.
""".trimIndent()
            ),
            LegalSection(
                heading = "3. Acceptable Use",
                body = """
You agree not to:
• Break any Philippine law, including the Cybercrime Prevention Act of 2012 (RA 10175), the Safe Spaces Act (RA 11313) and the Data Privacy Act (RA 10173).
• Attempt to hack, overload, scrape, reverse-engineer or disrupt the app, its servers or its services, or get around its limits and security.
• Impersonate others, create accounts in bulk, or use automated tools to use the app.
• Upload or post anything unlawful, defamatory, obscene, hateful, harassing, misleading, or that infringes someone else's rights.
• Use the app to collect information about other users.
""".trimIndent()
            ),
            LegalSection(
                heading = "4. Reviews and Other Content You Post",
                body = """
• Reviews must be honest and based on your own visit. Do not post spam, advertising, links, contact details, personal information of others, or content that is false, defamatory, offensive or abusive.
• Reviews are public to other users, shown with your name, rating and date. Reviews are checked automatically before posting and may be reported by other users and removed by moderators.
• You keep ownership of what you post. You grant us a non-exclusive, royalty-free, worldwide licence to display, reproduce and distribute it within Histoury and its promotion, for as long as it remains in the app.
• You are responsible for what you post. Online libel and other unlawful content may carry liability under Philippine law.
""".trimIndent()
            ),
            LegalSection(
                heading = "5. Location, Navigation and Road Safety",
                body = """
• Directions, distances, travel times, commute (train, bus and jeepney) suggestions, and arrival detection are estimates based on GPS and public map data (OpenStreetMap). They may be inaccurate, incomplete or out of date. Commute times are not schedules, and fares are not shown.
• Always obey traffic laws, signs and the instructions of authorities, and use your own judgment. Your safety comes first.
• Do not use the app while driving or riding. Under the Anti-Distracted Driving Act (RA 10913), drivers may not use a mobile device while the vehicle is in motion. Set your route before you go, or let a passenger operate the app.
• Some features need location, including in the background for arrival notifications. You can turn these permissions off at any time in your phone's settings; related features will then not work.
""".trimIndent()
            ),
            LegalSection(
                heading = "6. AR, Photos and Conduct at Heritage Sites",
                body = """
• Stay aware of your surroundings while using the camera or AR. Do not walk into traffic, restricted areas or unsafe places while looking at your screen.
• Follow the rules of each site and of the Intramuros Administration, including opening hours, entrance fees, and restrictions on photography or access. Respect heritage structures, which are protected under the National Cultural Heritage Act (RA 10066).
• Respect other people's privacy when capturing photos. Do not photograph or share images of people in ways that violate their privacy or the law, such as the Anti-Photo and Video Voyeurism Act (RA 9995).
• AR reconstructions are artistic and educational interpretations and may not be exact historical representations.
• AR photos are stored on your phone only. Save the ones you want to keep to your phone's gallery; uninstalling the app or clearing its data deletes them.
""".trimIndent()
            ),
            LegalSection(
                heading = "7. Places, Hotels and Third-Party Links",
                body = """
• Information about restaurants, cafés, parks, hotels, shops and other places (such as opening hours, fees, and contact details) is provided for convenience and may change. Please confirm details with the establishment.
• Histoury does not sell goods or services, take reservations, or process payments. "Book or check rates" and other links open the establishment's or a third party's own website or app; any booking or purchase you make there is solely between you and that party, under their terms.
• We are not responsible for third-party websites, apps, services or their content.
""".trimIndent()
            ),
            LegalSection(
                heading = "8. Intellectual Property",
                body = """
The app's design, software, text, narration, AR models, graphics and the Histoury name and logo belong to ${LegalContact.OPERATOR} or are used with permission or under licence, and are protected by the Intellectual Property Code of the Philippines (RA 8293). Map data and other third-party materials belong to their respective owners (see Legal Notices & Credits).

You may use the app and its content for personal, non-commercial purposes. You may not copy, modify, distribute, sell or create derivative works from it without our written permission, except as the law allows.
""".trimIndent()
            ),
            LegalSection(
                heading = "9. Historical Content Disclaimer",
                body = """
We aim to present accurate, well-sourced history and cite our sources in each site's Sources tab. Historical interpretations can differ and new research may change them. Content is provided for educational purposes and may be corrected or updated. If you find an error, please tell us through the Support Center.
""".trimIndent()
            ),
            LegalSection(
                heading = "10. Availability and Changes to the App",
                body = """
Histoury is provided "as is" and "as available". Features may change, be added or be removed, and the app may be unavailable at times for maintenance, connection problems, or reasons outside our control. Some features need an internet connection or services from third parties.
""".trimIndent()
            ),
            LegalSection(
                heading = "11. Limitation of Liability",
                body = """
To the extent permitted by Philippine law, ${LegalContact.OPERATOR} is not liable for indirect, incidental or consequential damages, or for loss or harm arising from GPS or map inaccuracies, commute or travel-time estimates, service interruptions, third-party services or links, device limitations, or your use of the app in breach of these Terms.

Nothing in these Terms excludes liability that cannot be excluded under Philippine law, such as liability for fraud or gross negligence, or your rights as a consumer or data subject.
""".trimIndent()
            ),
            LegalSection(
                heading = "12. Suspension, Termination and Account Deletion",
                body = """
• We may suspend or terminate accounts that violate these Terms or misuse the app. Where an account is suspended, you will see the reason in the app and can submit an appeal there.
• You may stop using Histoury at any time and request deletion of your account and data by emailing ${LegalContact.SUPPORT_EMAIL} with the subject "${LegalContact.PRIVACY_REQUEST_SUBJECT}". What is deleted and kept is described in the Privacy Policy.
""".trimIndent()
            ),
            LegalSection(
                heading = "13. Changes to These Terms",
                body = """
We may update these Terms. The current version is always available in the app (Menu > Terms & Conditions) with its effective date. For significant changes, we will make reasonable efforts to let you know before they take effect. If you keep using Histoury after changes take effect, you accept the updated Terms.
""".trimIndent()
            ),
            LegalSection(
                heading = "14. Governing Law and Disputes",
                body = """
These Terms are governed by the laws of the Republic of the Philippines. Please contact us first so we can try to resolve any concern informally. Disputes that cannot be resolved this way shall be brought before the proper courts of the Philippines.
""".trimIndent()
            ),
            LegalSection(
                heading = "15. Contact",
                body = """
${LegalContact.OPERATOR.replaceFirstChar { it.uppercase() }}
Email: ${LegalContact.SUPPORT_EMAIL}
Phone: ${LegalContact.SUPPORT_PHONE}
""".trimIndent()
            )
        ),
        closing = "By creating an account or using Histoury, you confirm that you have read, understood and agree to these Terms and Conditions and our Privacy Policy."
    )

    // =====================================================================
    // Legal Notices & Credits
    // =====================================================================

    val legalNotices = LegalDocument(
        title = "Legal Notices & Credits",
        effectiveDate = EFFECTIVE,
        intro = "Histoury is built with the help of open data, open-source software and third-party services. Their licences require the credits below.",
        sections = listOf(
            LegalSection(
                heading = "Map Data",
                body = """
© OpenStreetMap contributors. Map data, including streets and public transport routes, is available under the Open Database License (ODbL) at openstreetmap.org/copyright.

Map tiles: OpenFreeMap and © OpenMapTiles.
""".trimIndent()
            ),
            LegalSection(
                heading = "Directions and Commute",
                body = """
Routes: © openrouteservice.org by HeiGIT, and Stadia Maps (Valhalla), using OpenStreetMap data.

Commute suggestions use train, bus and jeepney routes mapped in OpenStreetMap. Ride and waiting times are estimates, not official schedules. Not every route is mapped.
""".trimIndent()
            ),
            LegalSection(
                heading = "Weather",
                body = "Weather data by Open-Meteo.com, licensed under CC BY 4.0."
            ),
            LegalSection(
                heading = "Augmented Reality",
                body = "AR features run on Google Play Services for AR (ARCore), provided by Google LLC and governed by the Google Privacy Policy and Google's terms."
            ),
            LegalSection(
                heading = "Historical Content",
                body = """
Historical text and narration were written for Histoury from the references listed in each site's Sources tab. Images and other materials are used with permission or under their respective licences.

The heritage sites featured in Histoury remain the property of their respective owners and administrators. Histoury is an independent project and is not affiliated with or endorsed by them unless stated.
""".trimIndent()
            ),
            LegalSection(
                heading = "Trademarks",
                body = "Google, Android, Firebase, ARCore and Google Maps are trademarks of Google LLC. Names, logos and trademarks of establishments shown in the app belong to their respective owners and are used only to identify them."
            ),
            LegalSection(
                heading = "Open-Source Software",
                body = "Histoury uses open-source software, including Kotlin, Android Jetpack (Jetpack Compose), MapLibre Native (BSD 2-Clause License), Coil, and the Firebase SDKs, used under their respective licences (mainly the Apache License 2.0). Their copyright notices remain with their authors."
            ),
            LegalSection(
                heading = "Applicable Laws",
                body = """
Histoury is designed to follow the laws of the Philippines, including:
• Data Privacy Act of 2012 (RA 10173)
• Electronic Commerce Act of 2000 (RA 8792)
• Cybercrime Prevention Act of 2012 (RA 10175)
• Intellectual Property Code (RA 8293)
• National Cultural Heritage Act of 2009 (RA 10066)
• Anti-Distracted Driving Act (RA 10913)
""".trimIndent()
            )
        ),
        closing = "Questions about these notices? Contact us at ${LegalContact.SUPPORT_EMAIL}."
    )
}
