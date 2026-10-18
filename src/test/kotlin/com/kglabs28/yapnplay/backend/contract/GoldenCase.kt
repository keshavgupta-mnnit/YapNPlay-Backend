package com.kglabs28.yapnplay.backend.contract

import kotlinx.serialization.KSerializer

/** One message and the exact JSON the contract says it looks like (`golden/<name>.json`). */
class GoldenCase<T>(
    val name: String,
    val serializer: KSerializer<T>,
    val sample: T,
)
