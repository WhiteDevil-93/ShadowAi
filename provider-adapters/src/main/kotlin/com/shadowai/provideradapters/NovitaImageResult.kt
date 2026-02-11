package com.shadowai.provideradapters

data class NovitaImageResult(
    val imageUrls: List<String>,
    val imageData: List<ByteArray>,
    val modelId: String,
    val parameters: Map<String, Any>
) {
    @Suppress("unused")
    fun getFirstImageUrl(): String? = imageUrls.firstOrNull()
    @Suppress("unused")
    fun getFirstImageBytes(): ByteArray? = imageData.firstOrNull()
}
