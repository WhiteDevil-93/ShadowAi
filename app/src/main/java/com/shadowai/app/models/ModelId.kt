package com.shadowai.app.models

@JvmInline
value class ModelId(val id: String) {
    override fun toString(): String = id
}
