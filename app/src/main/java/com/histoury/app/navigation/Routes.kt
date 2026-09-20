package com.histoury.app.navigation

import android.net.Uri

/**
 * Navigation Compose matches a destination by comparing the route string you
 * navigate to against the pattern the destination was registered with. That
 * comparison happens on the raw string, so any argument value containing a
 * space, a slash, a question mark or an ampersand silently produces a route
 * that matches nothing — and navigate() throws
 * IllegalArgumentException: "Navigation destination that matches route ...
 * cannot be found".
 *
 * Every id in this app is user- or admin-authored (site names, place names,
 * Firestore document ids that fall back to names), so none of them can be
 * assumed URL-safe. "Baluarte de San Diego" is exactly the shape that breaks
 * it: three spaces, and the crash lands on navigate() rather than anywhere
 * near the screen being opened, which makes it look like the destination
 * itself is broken.
 *
 * Encoding here is enough. Navigation Compose decodes path arguments on the
 * way out, so the receiving screen still reads the original value and no
 * caller or repository needs to change.
 */
private fun String.asRouteArgument(): String = Uri.encode(this)

sealed class Routes(val route: String) {

    // Decides Onboarding vs Home vs Suspended on cold launch, before
    // anything is drawn — see AuthGate.kt.
    object AuthGate : Routes("auth_gate")

    // Onboarding
    object Onboarding : Routes("onboarding")

    // Authentication
    object Login : Routes("login")
    object Register : Routes("register")
    object Suspended : Routes("suspended")

    // The Forgot Password wizard: email -> OTP -> new password, as three
    // separate screens sharing one ViewModel (scoped to ForgotPasswordFlow,
    // the nested graph's own route) rather than three flat destinations, so
    // the email and verified code survive back/forward between them without
    // being carried in the route string.
    object ForgotPasswordFlow : Routes("forgot_password_flow")
    object ForgotPassword : Routes("forgot_password")
    object OtpVerification : Routes("otp_verification")
    object ResetPassword : Routes("reset_password")

    // Legal documents: docType is "privacy" or "terms"
    object Legal : Routes("legal/{docType}") {
        fun createRoute(docType: String) = "legal/${docType.asRouteArgument()}"
    }

    // Main Screens
    object Home : Routes("home")

    // Itinerary builder and saved routes.
    object Itinerary : Routes("itinerary")

    object Menu : Routes("menu")

    // Standalone map tab: all sites at once, no siteId required
    object MapOverview : Routes("map_overview")

    object Map : Routes("map/{siteId}") {
        fun createRoute(siteId: String) = "map/${siteId.asRouteArgument()}"
    }

    object Arrival : Routes("arrival/{siteId}") {
        fun createRoute(siteId: String) = "arrival/${siteId.asRouteArgument()}"
    }

    object History : Routes("history")
    object Gallery : Routes("gallery")
    object Profile : Routes("profile")

    // Historical Site
    // showAr controls whether the AR button appears. It's true from the
    // Arrival flow (you're physically at the site), and false from the
    // Site Library (browsing info for a site you're not currently at) —
    // the AR camera only makes sense in person.
    object SiteDetails : Routes("site_details/{siteId}?showAr={showAr}") {
        fun createRoute(siteId: String, showAr: Boolean = true) =
            "site_details/${siteId.asRouteArgument()}?showAr=$showAr"
    }

    // Site Library (Menu > Site Information): every historical site, with
    // ones the person hasn't physically visited yet shown locked.
    object SiteLibrary : Routes("site_library")

    // Place Details: the showcase screen for non-historical places (cafés,
    // restaurants, hotels, souvenir shops, schools, banks). Unlike sites,
    // this is freely viewable any time — no arrival/unlock mechanic, since
    // it's just a business listing rather than gated educational content.
    object PlaceDetails : Routes("place_details/{placeId}") {
        fun createRoute(placeId: String) = "place_details/${placeId.asRouteArgument()}"
    }

    // AR. Takes the site document id: the model URL, its real-world
    // coordinates and its heading are all per-site data, and the capture
    // written to the gallery has to be attributed to a site.
    object ARExperience : Routes("ar/{siteId}") {
        fun createRoute(siteId: String) = "ar/${siteId.asRouteArgument()}"
    }

    // Rankings
    object Rankings : Routes("rankings")

    // Support
    object Support : Routes("support")

    object Downloads : Routes("downloads")

    // TEMPORARY — on-site test tool for checking whether ARCore's
    // Streetscape Geometry sees building shapes at Fort Santiago and
    // Baluarte de San Diego. Not part of the visitor-facing app. Safe to
    // delete this entry, its NavGraph registration, and the "Developer"
    // section in MenuScreen.kt once on-site testing is done.
    object StreetscapeDebug : Routes("streetscape_debug")

    // Reviews
    object Review : Routes("review/{siteId}/{visitId}") {
        fun createRoute(siteId: String, visitId: String) =
            "review/${siteId.asRouteArgument()}/${visitId.asRouteArgument()}"
    }
}
