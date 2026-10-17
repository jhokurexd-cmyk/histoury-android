package com.histoury.app.util

import java.text.Normalizer
import java.util.Locale

/**
 * How the app's search boxes decide what matches and in what order.
 *
 * The old rule was "the text appears anywhere in the name, location or
 * description". With full site histories in the descriptions, a single
 * letter matched every site ("f" → all 19), and "fo" matched any site
 * whose history used the word "for". Results also kept their default
 * order, so the site you were typing could sit below unrelated ones.
 *
 * Now, like a phone's contacts search:
 *  - each word typed has to match the START of a word ("fo" → Fort
 *    Santiago, not "for" in a paragraph; "san" → Fort Santiago);
 *  - every word typed must match ("fort san" narrows, not widens);
 *  - descriptions only count for words of 4+ letters, so a history is
 *    searchable ("spanish", "rizal") without single letters matching it;
 *  - accents and capitals don't matter ("jose" finds "José");
 *  - best matches first: name starts with the text, then a word in the
 *    name does, then the location, then the description.
 */
object SearchMatcher {

    private const val NAME_STARTS = 100
    private const val NAME_WORDS = 80
    private const val NAME_CONTAINS = 60
    private const val NAME_OR_LOCATION_WORDS = 40
    private const val DESCRIPTION_WORDS = 20

    /** Shortest typed word that may match inside a name or a description. */
    private const val MIN_LOOSE_MATCH_LENGTH = 3
    private const val MIN_DESCRIPTION_WORD_LENGTH = 4

    private val diacritics = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase(Locale.ROOT)
            .trim()

    private fun words(text: String): List<String> =
        normalize(text).split(separators).filter { it.isNotEmpty() }

    /**
     * How well [query] matches; 0 means not at all. A blank query matches
     * everything equally.
     */
    fun score(query: String, name: String, location: String = "", description: String = ""): Int {

        val whole = normalize(query)
        if (whole.isEmpty()) return 1
        val tokens = words(query)
        if (tokens.isEmpty()) return 0

        val normalizedName = normalize(name)
        val nameWords = words(name)

        if (normalizedName.startsWith(whole)) return NAME_STARTS
        if (tokens.all { token -> nameWords.any { it.startsWith(token) } }) return NAME_WORDS
        if (whole.length >= MIN_LOOSE_MATCH_LENGTH && normalizedName.contains(whole)) return NAME_CONTAINS

        val locationWords = words(location)
        fun inNameOrLocation(token: String) =
            nameWords.any { it.startsWith(token) } || locationWords.any { it.startsWith(token) }

        if (tokens.all(::inNameOrLocation)) return NAME_OR_LOCATION_WORDS

        val descriptionWords by lazy { words(description) }
        val allFound = tokens.all { token ->
            inNameOrLocation(token) ||
                (token.length >= MIN_DESCRIPTION_WORD_LENGTH && descriptionWords.any { it.startsWith(token) })
        }
        return if (allFound) DESCRIPTION_WORDS else 0
    }

    /**
     * [items] that match [query], best first (ties keep their name order).
     * A blank query returns [items] unchanged, in their usual order.
     */
    fun <T> rank(
        items: List<T>,
        query: String,
        name: (T) -> String,
        location: (T) -> String = { "" },
        description: (T) -> String = { "" }
    ): List<T> {
        if (query.isBlank()) return items
        return items
            .map { it to score(query, name(it), location(it), description(it)) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenBy { normalize(name(it.first)) })
            .map { it.first }
    }
}
