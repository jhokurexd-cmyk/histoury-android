package com.histoury.app.util

import java.text.Normalizer

/**
 * Review text filter: profanity, links and contact details.
 *
 * Runs while the visitor types so the problem is shown before they submit.
 * The server runs the SAME rules in submitSiteReview
 * (functions/reviewFilter.js) and has the final say — keep the word lists
 * and rules of the two files in sync.
 *
 * How words are matched:
 *  - Leetspeak is undone first (sh1t, f4ck, @ss …) and repeated letters are
 *    squeezed (fuuuck), so simple disguises don't work.
 *  - Whole words only, never "contains", so ordinary words that include a
 *    bad one (class, assistant, putahe, shiitake, leche flan) pass.
 *  - A few roots also match as prefixes (fucking, shitty …).
 *  - Letters spaced out one by one (f u c k, f.u.c.k) are joined back up.
 *  - A few words masked with symbols (f*ck, sh#t) are caught too.
 */
object ReviewTextFilter {

    enum class Problem { PROFANITY, LINK, CONTACT }

    data class Result(val problem: Problem, val message: String)

    private val BLOCKED_WORDS = listOf(
        // English
        "fuck", "fucks", "fucker", "fuckers", "fucked", "fucking", "fuckin", "fck", "fcking", "fk",
        "motherfucker", "motherfucking", "mf", "wtf", "stfu", "gtfo",
        "shit", "shits", "shitty", "bullshit", "horseshit", "shithead", "shitshow",
        "bitch", "bitches", "bitchy", "sonofabitch",
        "bastard", "bastards", "asshole", "assholes", "arsehole", "jackass", "dumbass", "dipshit",
        "dick", "dicks", "dickhead", "pussy", "cunt", "cunts", "twat", "prick", "wanker",
        "whore", "whores", "slut", "sluts", "douche", "douchebag", "cocksucker",
        // Slurs
        "nigger", "niggers", "nigga", "niggas", "faggot", "faggots", "fag", "fags",
        "retard", "retards", "retarded", "tranny",
        // Filipino / Taglish
        "putangina", "putanginamo", "potangina", "potanginamo", "tangina", "tanginamo", "tangena",
        "puta", "pota", "putangama", "bwakanangina", "bwakananginamo", "punyeta", "putragis",
        "gago", "gagu", "ulol", "ulul", "olol", "tarantado", "tarantada", "kupal",
        "bobo", "tanga", "tangahan", "inutil", "hinayupak", "gunggong",
        "pakyu", "pakyo", "pakshet", "pakshit", "pukinangina", "pukinanginamo",
        "kantot", "kantutan", "jakol", "burat", "titi", "tite", "etits", "puke", "pekpek", "pokpok",
        "yawa", "pisti", "piste", // Bisaya
        // Demo placeholders written by the demo-review generator (demoReviews.js)
        "pr0f4nity", "f0ulw0rd", "b4dw0rd", "cr4pp1ty"
    )

    private val BLOCKED_PREFIXES = listOf(
        "fuck", "motherfuck", "shit", "bitch", "cunt", "nigg", "fagg", "putangin", "tangin"
    )

    private val ALLOWED_WORDS = setOf("shitake", "shiitake")

    private val BLOCKED_PHRASES = listOf(
        "putang ina", "tang ina", "anak ng puta", "puta ka", "gaga ka",
        "gago ka", "ulol ka", "bobo ka", "tanga ka"
    )

    private val MASKED_PATTERNS = listOf(
        Regex("""\bf[*#@%_.-]{1,3}c?k(?:ing|er|ed|s)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bf[*#@%_.-]{1,3}ck""", RegexOption.IGNORE_CASE),
        Regex("""\bsh[*#@%]{1,2}t""", RegexOption.IGNORE_CASE),
        Regex("""\bb[*#@]{1,2}tch""", RegexOption.IGNORE_CASE),
        Regex("""\bc[*#@]{1,2}nt\b""", RegexOption.IGNORE_CASE),
        Regex("""\bp[*#@]{1,2}ta\b""", RegexOption.IGNORE_CASE)
    )

    private val LEET = mapOf(
        '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't',
        '8' to 'b', '@' to 'a', '$' to 's', '!' to 'i', '|' to 'i'
    )

    // Short endings like "it", "in", "me", "co" are left out on purpose:
    // "…Santiago.It was…" is a missing space, not a link.
    private val TLDS = listOf(
        "com", "net", "org", "ph", "io", "xyz", "info", "biz", "link", "click", "top", "shop", "site",
        "online", "store", "live", "app", "dev", "gg", "gl", "ly", "tk", "ml", "ga", "cf", "gq", "cc",
        "ru", "cn", "tv", "ws", "icu", "vip", "win", "bid", "loan", "work", "fun", "test"
    ).joinToString("|")

    private val LINK_PATTERNS = listOf(
        Regex("""\bhttps?\s*:\s*//""", RegexOption.IGNORE_CASE),
        Regex("""\bwww\s*\.\s*[a-z0-9]""", RegexOption.IGNORE_CASE),
        Regex("""\b[a-z0-9][a-z0-9-]*\.(?:$TLDS)\b""", RegexOption.IGNORE_CASE),
        Regex(
            """\b[a-z0-9][a-z0-9-]*\s*(?:\(dot\)|\[dot\]|\[\.\]|\(\.\)|\s+dot\s+)\s*(?:$TLDS)\b""",
            RegexOption.IGNORE_CASE
        )
    )

    private val EMAIL_PATTERN = Regex(
        """[a-z0-9._%+-]+\s*(?:@|\(at\)|\[at\])\s*[a-z0-9-]+\s*(?:\.|\(dot\)|\s+dot\s+)\s*[a-z]{2,}""",
        RegexOption.IGNORE_CASE
    )

    // Philippine mobile numbers however they're spaced: 0917 123 4567,
    // +63 917-123-4567, 639171234567.
    private val PHONE_CANDIDATE = Regex("""\+?\d[\d\s().-]{8,18}\d""")
    private val PHONE_DIGITS = Regex("""^(?:63|0)?9\d{9}$""")

    private val TOKEN = Regex("""[a-z0-9@$!|]+""")
    private val EDGE_JUNK = Regex("""^[^a-z0-9@$]+|[^a-z0-9]+$""")
    private val NON_LETTERS = Regex("""[^a-z]""")
    private val REPEATS = Regex("""(.)\1+""")
    private val DIGITS_ONLY = Regex("""^\d+$""")

    private fun squeeze(word: String) = word.replace(REPEATS, "$1")

    private fun deLeet(word: String) = buildString(word.length) {
        word.forEach { append(LEET[it] ?: it) }
    }

    private val blockedSet = BLOCKED_WORDS.toSet()
    private val blockedSqueezed = BLOCKED_WORDS.map(::squeeze).toSet()

    const val PROFANITY_MESSAGE_PREFIX = "Your review contains language that isn't allowed"
    const val LINK_MESSAGE = "Links and website addresses aren't allowed in reviews."
    const val CONTACT_MESSAGE = "Phone numbers and email addresses aren't allowed in reviews."

    /** "fuck" -> "f**k", for the error message. */
    private fun censor(word: String): String = when {
        word.isEmpty() -> word
        word.length <= 2 -> word.first() + "*"
        else -> word.first() + "*".repeat(word.length - 2) + word.last()
    }

    /** The word as typed, if it's a bad one; else null. */
    private fun badWord(rawToken: String): String? {
        val raw = rawToken.lowercase().replace(EDGE_JUNK, "")
        if (raw.isEmpty() || DIGITS_ONLY.matches(raw)) return null
        if (raw in blockedSet) return raw

        val plain = deLeet(raw).replace(NON_LETTERS, "")
        if (plain.isEmpty() || plain in ALLOWED_WORDS || squeeze(plain) in ALLOWED_WORDS) return null

        if (plain in blockedSet || squeeze(plain) in blockedSqueezed) return raw
        if (BLOCKED_PREFIXES.any { plain.startsWith(it) || squeeze(plain).startsWith(squeeze(it)) }) return raw
        return null
    }

    private fun findProfanity(text: String): String? {

        val lower = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("""\p{M}+"""), "")

        MASKED_PATTERNS.forEach { pattern ->
            pattern.find(lower)?.let { return it.value }
        }

        val tokens = TOKEN.findAll(lower).map { it.value }.toList()
        tokens.forEach { token -> badWord(token)?.let { return it } }

        val words = tokens
            .map { deLeet(it.replace(EDGE_JUNK, "")).replace(NON_LETTERS, "") }
            .filter { it.isNotEmpty() }

        val joined = " " + words.joinToString(" ") { squeeze(it) } + " "
        BLOCKED_PHRASES.forEach { phrase ->
            val squeezedPhrase = phrase.split(" ").joinToString(" ") { squeeze(it) }
            if (joined.contains(" $squeezedPhrase ") || joined.contains(" $phrase ")) return phrase
        }

        // f u c k / f.u.c.k / f-u-c-k: runs of single letters, joined.
        var run = ""
        (words + "").forEach { w ->
            if (w.length == 1) {
                run += w
                return@forEach
            }
            if (run.length >= 3) badWord(run)?.let { return it }
            run = ""
        }
        return null
    }

    private fun hasLink(text: String) = LINK_PATTERNS.any { it.containsMatchIn(text) }

    private fun hasContact(text: String): Boolean {
        if (EMAIL_PATTERN.containsMatchIn(text)) return true
        return PHONE_CANDIDATE.findAll(text).any { PHONE_DIGITS.matches(it.value.filter(Char::isDigit)) }
    }

    /** null when the review text is fine; otherwise what's wrong and the message to show. */
    fun check(text: String): Result? {
        if (text.isBlank()) return null

        findProfanity(text)?.let { word ->
            return Result(Problem.PROFANITY, "$PROFANITY_MESSAGE_PREFIX (\"${censor(word)}\"). Please rephrase it.")
        }
        // Contact first: an email address also looks like a link.
        if (hasContact(text)) return Result(Problem.CONTACT, CONTACT_MESSAGE)
        if (hasLink(text)) return Result(Problem.LINK, LINK_MESSAGE)
        return null
    }
}
