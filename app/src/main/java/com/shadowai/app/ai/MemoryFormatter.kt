package com.shadowai.app.ai

import com.shadowai.app.db.MemoryEntity

object MemoryFormatter {
    fun formatContext(symbolic: List<MemoryEntity>, working: List<MemoryEntity>): String {
        return buildString {
            if (symbolic.isNotEmpty()) {
                append("Validated Knowledge Path (SYMBOLIC):\n")
                symbolic.forEach { append("- ${it.value}\n") }
            }

            if (working.isNotEmpty()) {
                append("\nRecent Context Fragments (WORKING):\n")
                working.forEach { append("- ${it.value}\n") }
            }
        }
    }
}
