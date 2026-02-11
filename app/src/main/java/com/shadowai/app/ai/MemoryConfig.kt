package com.shadowai.app.ai

object MemoryConfig {
    const val SYMBOLIC_LAYER = "symbolic"
    const val WORKING_LAYER = "working"
    
    const val NAME_CONFIDENCE = 0.9f
    const val INTEREST_CONFIDENCE = 0.8f
    const val FACT_CONFIDENCE = 0.7f
    
    const val LOW_CONFIDENCE_THRESHOLD = 0.3f
    
    val CONTEXT_LENGTH_RANGE = 10..200
    const val CONTEXT_LIMIT = 20
}
