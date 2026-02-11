package com.shadowai.provideradapters

import com.google.gson.annotations.SerializedName

data class NovitaAsyncResponse(
    @SerializedName("task_id") val taskId: String?,
    val code: Int?,
    val message: String?,
    val status: String?
)
