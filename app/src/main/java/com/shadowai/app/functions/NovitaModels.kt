package com.shadowai.app.functions

data class NovitaModel(
    val name: String,
    val filename: String,
    val thumbnail: String?
)

data class NovitaResultImage(
    val url: String,
    val type: String?,
    val ttl: String?,
    val nsfwDetection: NovitaNsfwDetection?
)

data class NovitaNsfwDetection(
    val valid: Boolean?,
    val confidence: Double?
)
