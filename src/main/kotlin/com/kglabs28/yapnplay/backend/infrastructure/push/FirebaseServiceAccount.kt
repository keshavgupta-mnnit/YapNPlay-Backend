package com.kglabs28.yapnplay.backend.infrastructure.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The fields we need from Firebase's service-account JSON file. */
@Serializable
data class FirebaseServiceAccount(
    @SerialName("project_id") val projectId: String,
    @SerialName("client_email") val clientEmail: String,
    @SerialName("private_key") val privateKey: String,
    @SerialName("token_uri") val tokenUri: String = "https://oauth2.googleapis.com/token",
) {
    override fun toString(): String = "FirebaseServiceAccount(projectId=$projectId, clientEmail=$clientEmail, privateKey=***)"
}
