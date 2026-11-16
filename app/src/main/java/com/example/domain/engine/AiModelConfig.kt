package com.example.domain.engine

/**
 * Decoupled configuration for Firebase AI Logic Gemini models and payload size constraints.
 */
object AiModelConfig {

  /**
   * Primary model for multimodal document classification and structured information extraction.
   * Uses Gemini 3.8 Flash by default via Firebase AI Logic.
   */
  var extractionModel: String = "gemini-3.8-flash"

  /**
   * Primary model for category-specific plain-language medical report explanations.
   * Uses Gemini 3.8 Flash by default via Firebase AI Logic.
   */
  var explanationModel: String = "gemini-3.8-flash"

  /**
   * High-volume or lightweight fallback model.
   */
  const val FAST_EXTRACTION_MODEL = "gemini-3.5-flash-lite"

  /**
   * Maximum coroutine timeout for AI inference operations.
   */
  const val INFERENCE_TIMEOUT_MS = 60_000L

  /**
   * Firebase AI Logic inline request payload ceiling: 20 MB total limit.
   * Base64 encoding inflates binary byte streams by ~33.3% (4/3 factor).
   * 14 MB unencoded * 1.333 = ~18.66 MB Base64 payload, safely below the 20 MB hard ceiling.
   */
  const val MAX_RAW_PAYLOAD_BYTES = 14L * 1024L * 1024L // 14 MB unencoded ceiling
  const val FIREBASE_AI_INLINE_LIMIT_BYTES = 20L * 1024L * 1024L // 20 MB hard ceiling

  /**
   * Maximum pages processed in a single multimodal batch window.
   */
  const val MAX_PAGES_PER_BATCH = 3

  /**
   * Calculates estimated Base64 request size from raw byte count.
   */
  fun calculateBase64PayloadSize(rawBytes: Long): Long {
    if (rawBytes <= 0) return 0L
    return ((rawBytes + 2) / 3) * 4
  }

  /**
   * Validates if a document's byte size will safely fit inside the Firebase AI Logic request limit.
   */
  fun isPayloadWithinSafeLimits(rawBytes: Long): Boolean {
    val estimatedBase64 = calculateBase64PayloadSize(rawBytes)
    return rawBytes <= MAX_RAW_PAYLOAD_BYTES && estimatedBase64 < FIREBASE_AI_INLINE_LIMIT_BYTES
  }
}
