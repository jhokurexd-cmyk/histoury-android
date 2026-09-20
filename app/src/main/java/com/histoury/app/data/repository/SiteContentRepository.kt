package com.histoury.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.data.model.SiteContent
import com.histoury.app.data.model.SourceEntry
import com.histoury.app.data.model.StoryEntry
import com.histoury.app.data.model.TimelineEntry
import kotlinx.coroutines.tasks.await

class SiteContentRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getSiteContent(siteId: String): SiteContent? {

        if (siteId.isBlank()) {
            return null
        }

        return try {

            // The geofences collection uses the site's slug as its own
            // document ID, so we try that convention first here too.
            var document = db
                .collection("site_content")
                .document(siteId)
                .get()
                .await()

            if (!document.exists()) {

                val snapshot = db
                    .collection("site_content")
                    .whereEqualTo("siteId", siteId)
                    .limit(1)
                    .get()
                    .await()

                document = snapshot.documents.firstOrNull() ?: return null
            }

            parseSiteContent(siteId, document)

        } catch (e: Exception) {
            null
        }
    }

    private fun parseSiteContent(siteId: String, document: DocumentSnapshot): SiteContent {

        return SiteContent(
            siteId = siteId,
            overview = document.getString("overview") ?: "",
            timeline = parseTimeline(document.get("timeline")),
            stories = parseStories(document.get("stories")),
            sources = parseSources(document.get("sources")),
            quote = document.getString("quote") ?: "",
            audioUrls = parseAudioUrls(document.get("audioUrls"))
        )
    }

    /**
     * Firestore hands nested maps back as Map<*, *>, so every key and value
     * is checked rather than cast. A malformed entry — written by hand in
     * the console, say — drops out instead of throwing and costing the
     * visitor the whole content document.
     */
    private fun parseAudioUrls(raw: Any?): Map<String, String> {

        val map = raw as? Map<*, *> ?: return emptyMap()

        return map.entries.mapNotNull { (key, value) ->
            val section = key as? String ?: return@mapNotNull null
            val url = value as? String ?: return@mapNotNull null
            if (url.isBlank()) null else section to url
        }.toMap()
    }

    private fun parseTimeline(raw: Any?): List<TimelineEntry> {

        val rawList = raw as? List<*> ?: return emptyList()

        return rawList.mapNotNull { item ->

            val map = item as? Map<*, *> ?: return@mapNotNull null

            TimelineEntry(
                year = (map["year"] ?: map["date"]) as? String ?: "",
                event = (map["event"] ?: map["description"] ?: map["title"])
                    as? String ?: ""
            )
        }
    }

    private fun parseStories(raw: Any?): List<StoryEntry> {

        val rawList = raw as? List<*> ?: return emptyList()

        return rawList.mapNotNull { item ->

            val map = item as? Map<*, *> ?: return@mapNotNull null

            StoryEntry(
                title = map["title"] as? String ?: "",
                content = (map["content"] ?: map["body"] ?: map["text"])
                    as? String ?: ""
            )
        }
    }

    private fun parseSources(raw: Any?): List<SourceEntry> {

        val rawList = raw as? List<*> ?: return emptyList()

        return rawList.mapNotNull { item ->

            val map = item as? Map<*, *> ?: return@mapNotNull null

            SourceEntry(
                title = (map["title"] ?: map["name"]) as? String ?: "",
                url = map["url"] as? String ?: ""
            )
        }
    }
}
