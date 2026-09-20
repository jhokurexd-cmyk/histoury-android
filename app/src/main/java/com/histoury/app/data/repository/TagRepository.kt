package com.histoury.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.data.model.Tag
import kotlinx.coroutines.tasks.await

class TagRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getTags(): List<Tag> {

        return try {

            val snapshot = db
                .collection("tags")
                .whereEqualTo("status", "active")
                .get()
                .await()

            snapshot.documents.mapNotNull { document ->

                val tag = document.toObject(Tag::class.java)

                tag?.copy(
                    id = document.id
                )

            }

        } catch (e: Exception) {

            emptyList()

        }

    }

}