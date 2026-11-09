package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.config.BackendConstants
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The Digital Asset Links statement: this host lets the YapNPlay app
 * (signed by one of [BackendConstants.ANDROID_CERT_SHA256S]) open its links.
 */
object AssetLinks {

    val json: String = buildJsonArray {
        addJsonObject {
            putJsonArray("relation") { add(HANDLE_ALL_URLS) }
            putJsonObject("target") {
                put("namespace", ANDROID_NAMESPACE)
                put("package_name", BackendConstants.APP_PACKAGE)
                putJsonArray("sha256_cert_fingerprints") {
                    BackendConstants.ANDROID_CERT_SHA256S.forEach { add(it) }
                }
            }
        }
    }.toString()

    private const val HANDLE_ALL_URLS = "delegate_permission/common.handle_all_urls"
    private const val ANDROID_NAMESPACE = "android_app"
}
