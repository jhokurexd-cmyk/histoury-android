package com.histoury.app.data.model

data class SiteContent(

    val siteId: String = "",

    val overview: String = "",

    val timeline: List<TimelineEntry> = emptyList(),

    val stories: List<StoryEntry> = emptyList(),

    val sources: List<SourceEntry> = emptyList(),

    /**
     * A single pull-quote, shown above the overview.
     *
     * Deliberately one line of text rather than a list. Its job is to give a
     * visitor standing in front of the building a reason to keep reading —
     * a question or an image, not a second paragraph. More than one would be
     * two things competing for the same glance.
     */
    val quote: String = "",


    /**
     * Narration for each section, keyed the way the Cloud Function writes it:
     * "overview", and "story_0", "story_1" and so on.
     *
     * A map rather than named fields because the number of stories is not
     * fixed — a site can have one or six, and adding a field per story would
     * mean changing the model every time an admin writes another one.
     *
     * Empty means no narration has been generated yet, which is the normal
     * state for a site whose content was written before the voiceover
     * feature existed. The app treats it as "no audio", never as an error.
     */
    val audioUrls: Map<String, String> = emptyMap()

)

data class TimelineEntry(

    val year: String = "",

    val event: String = ""

)

data class StoryEntry(

    val title: String = "",

    val content: String = ""

)

data class SourceEntry(

    val title: String = "",

    val url: String = ""

)
