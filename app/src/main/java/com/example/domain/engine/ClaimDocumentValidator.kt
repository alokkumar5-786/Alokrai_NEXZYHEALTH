package com.example.domain.engine

import com.example.model.ClaimBucketType
import com.example.model.ClaimDocumentAttachment
import com.example.model.ClaimDocumentRole
import com.example.model.ClaimErrorType
import com.example.model.ClaimReviewConstants
import java.util.Locale
import java.util.UUID

/**
 * Pure, testable document validation engine for "Review My Rejected Health Claim".
 *
 * Security & Integrity Guarantees:
 * - Enforces byte-level magic signatures for PDF (%PDF-), JPEG, PNG, and WEBP.
 * - Rejects arbitrary bytes claiming to be an image or declared with ambiguous wildcard image MIME types.
 * - Detects mismatches between declared MIME / filename extensions and actual file signatures.
 * - Reuses [AiModelConfig.isPayloadWithinSafeLimits] and [AiModelConfig.MAX_RAW_PAYLOAD_BYTES]
 *   without bypassing inline payload limits.
 * - Never silently truncates oversized pasted text.
 * - Never invents a PDF page count when [PdfPageCounter] cannot inspect pages.
 */
object ClaimDocumentValidator {

  val SUPPORTED_MIME_TYPES: Set<String> = setOf(
    "application/pdf",
    "image/jpeg",
    "image/png",
    "image/webp"
  )

  private val EXTENSION_TO_MIME: Map<String, String> = mapOf(
    "pdf" to "application/pdf",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "webp" to "image/webp"
  )

  sealed class PdfInspectionResult {
    data class Inspected(val pageCount: Int) : PdfInspectionResult()
    object NotInspected : PdfInspectionResult()
    data class InvalidOrEncrypted(val reason: String) : PdfInspectionResult()
  }

  /**
   * Testable abstraction so JVM tests can supply exact PDF page counts or encrypted-PDF errors
   * without requiring a device [android.graphics.pdf.PdfRenderer], while never inventing counts.
   */
  interface PdfPageCounter {
    fun inspect(bytes: ByteArray): PdfInspectionResult
  }

  object DefaultPdfPageCounter : PdfPageCounter {
    override fun inspect(bytes: ByteArray): PdfInspectionResult = PdfInspectionResult.NotInspected
  }

  sealed class FileValidationOutcome {
    data class Valid(val attachment: ClaimDocumentAttachment) : FileValidationOutcome()
    data class Invalid(val errorType: ClaimErrorType, val reason: String) : FileValidationOutcome()
  }

  sealed class PastedTextValidationOutcome {
    data class Valid(val normalizedText: String) : PastedTextValidationOutcome()
    data class Invalid(val errorType: ClaimErrorType, val reason: String) : PastedTextValidationOutcome()
  }

  sealed class SubmissionValidationOutcome {
    object Valid : SubmissionValidationOutcome()
    data class Invalid(val errorType: ClaimErrorType, val reason: String) : SubmissionValidationOutcome()
  }

  fun validateCandidateFile(
    bucket: ClaimBucketType,
    role: ClaimDocumentRole,
    fileName: String,
    declaredMimeType: String?,
    bytes: ByteArray,
    existingAttachments: List<ClaimDocumentAttachment> = emptyList(),
    pdfPageCounter: PdfPageCounter = DefaultPdfPageCounter,
    documentId: String = UUID.randomUUID().toString()
  ): FileValidationOutcome {
    val cleanFileName = fileName.trim().ifBlank { "document" }

    // 1. Empty / zero-byte file
    if (bytes.isEmpty()) {
      return FileValidationOutcome.Invalid(
        errorType = ClaimErrorType.UNREADABLE,
        reason = "Uploaded file '$cleanFileName' is empty (0 bytes)."
      )
    }

    val fileByteCount = bytes.size.toLong()

    // 2. Enforce bucket and global count limits
    val bucketCount = existingAttachments.count { it.bucket == bucket }
    if (bucketCount >= ClaimReviewConstants.maxFilesForBucket(bucket)) {
      return FileValidationOutcome.Invalid(
        errorType = ClaimErrorType.INVALID_FILE_TYPE,
        reason = "Maximum files reached for ${bucket.name}."
      )
    }
    if (existingAttachments.size >= ClaimReviewConstants.MAX_TOTAL_FILES) {
      return FileValidationOutcome.Invalid(
        errorType = ClaimErrorType.INVALID_FILE_TYPE,
        reason = "Maximum total file count (${ClaimReviewConstants.MAX_TOTAL_FILES}) reached."
      )
    }

    // 3. Reuse AiModelConfig size ceilings for individual and aggregate files
    if (!AiModelConfig.isPayloadWithinSafeLimits(fileByteCount)) {
      return FileValidationOutcome.Invalid(
        errorType = ClaimErrorType.FILE_TOO_LARGE,
        reason = "Individual file '$cleanFileName' exceeds the ${AiModelConfig.MAX_RAW_PAYLOAD_BYTES} byte limit."
      )
    }

    val aggregateBytes = existingAttachments.sumOf { it.byteCount } + fileByteCount
    if (!AiModelConfig.isPayloadWithinSafeLimits(aggregateBytes)) {
      return FileValidationOutcome.Invalid(
        errorType = ClaimErrorType.FILE_TOO_LARGE,
        reason = "Combined attachment size ($aggregateBytes bytes) exceeds the ${AiModelConfig.MAX_RAW_PAYLOAD_BYTES} byte safe limit."
      )
    }

    // 4. Reject ambiguous wildcard or unsupported declared MIME types immediately
    val normalizedDeclaredMime = declaredMimeType?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
    val canonicalDeclaredMime = if (normalizedDeclaredMime != null) {
      val mapped = canonicalizeSupportedMime(normalizedDeclaredMime)
      if (mapped == null) {
        return FileValidationOutcome.Invalid(
          errorType = ClaimErrorType.INVALID_FILE_TYPE,
          reason = "Unsupported or ambiguous declared MIME type '$normalizedDeclaredMime'."
        )
      }
      mapped
    } else {
      null
    }

    // 5. Reject unsupported explicit file extensions
    val extension = extractFileExtension(cleanFileName)
    val expectedMimeFromExtension = if (extension != null) {
      val extMime = EXTENSION_TO_MIME[extension]
      if (extMime == null) {
        return FileValidationOutcome.Invalid(
          errorType = ClaimErrorType.INVALID_FILE_TYPE,
          reason = "Unsupported file extension '.$extension'."
        )
      }
      extMime
    } else {
      null
    }

    // 6. Inspect real magic signature from bytes
    val signatureMime = detectMimeFromMagicBytes(bytes)
      ?: return FileValidationOutcome.Invalid(
        errorType = ClaimErrorType.INVALID_FILE_TYPE,
        reason = "File '$cleanFileName' does not match a valid PDF, JPEG, PNG, or WEBP signature."
      )

    // 7. Reject metadata vs signature mismatches
    if (canonicalDeclaredMime != null && canonicalDeclaredMime != signatureMime) {
      return FileValidationOutcome.Invalid(
        errorType = ClaimErrorType.INVALID_FILE_TYPE,
        reason = "MIME mismatch: declared '$canonicalDeclaredMime' but file signature is '$signatureMime'."
      )
    }
    if (expectedMimeFromExtension != null && expectedMimeFromExtension != signatureMime) {
      return FileValidationOutcome.Invalid(
        errorType = ClaimErrorType.INVALID_FILE_TYPE,
        reason = "Extension mismatch: '.$extension' does not match signature '$signatureMime'."
      )
    }

    // 8. Optional PDF inspection (never invent page counts)
    val inspectedPageCount: Int? = if (signatureMime == "application/pdf") {
      when (val pdfResult = pdfPageCounter.inspect(bytes)) {
        is PdfInspectionResult.Inspected -> {
          if (pdfResult.pageCount <= 0) {
            return FileValidationOutcome.Invalid(
              errorType = ClaimErrorType.UNREADABLE,
              reason = "PDF has zero readable pages."
            )
          }
          pdfResult.pageCount
        }
        is PdfInspectionResult.NotInspected -> null
        is PdfInspectionResult.InvalidOrEncrypted -> {
          return FileValidationOutcome.Invalid(
            errorType = ClaimErrorType.UNREADABLE,
            reason = pdfResult.reason
          )
        }
      }
    } else {
      null
    }

    return FileValidationOutcome.Valid(
      ClaimDocumentAttachment(
        documentId = documentId,
        bucket = bucket,
        role = role,
        originalFileName = cleanFileName,
        declaredMimeType = declaredMimeType,
        detectedMimeType = signatureMime,
        byteCount = fileByteCount,
        bytes = bytes,
        pageCount = inspectedPageCount
      )
    )
  }

  /**
   * Validates pasted text without silently truncating oversized input.
   */
  fun validatePastedText(
    rawText: String,
    allowEmpty: Boolean = false
  ): PastedTextValidationOutcome {
    if (rawText.isBlank()) {
      return if (allowEmpty) {
        PastedTextValidationOutcome.Valid("")
      } else {
        PastedTextValidationOutcome.Invalid(
          errorType = ClaimErrorType.UNREADABLE,
          reason = "Pasted text is empty."
        )
      }
    }

    if (rawText.length > ClaimReviewConstants.MAX_PASTED_TEXT_CHARS) {
      return PastedTextValidationOutcome.Invalid(
        errorType = ClaimErrorType.FILE_TOO_LARGE,
        reason = "Pasted text length (${rawText.length} chars) exceeds maximum allowed ${ClaimReviewConstants.MAX_PASTED_TEXT_CHARS} characters."
      )
    }

    return PastedTextValidationOutcome.Valid(rawText)
  }

  /**
   * Validates a complete submission batch across buckets + pasted inputs before inference.
   */
  fun validateSubmission(
    attachments: List<ClaimDocumentAttachment>,
    pastedRejectionLetterText: String,
    pastedPolicyText: String = ""
  ): SubmissionValidationOutcome {
    if (attachments.isEmpty() && pastedRejectionLetterText.isBlank() && pastedPolicyText.isBlank()) {
      return SubmissionValidationOutcome.Invalid(
        errorType = ClaimErrorType.UNREADABLE,
        reason = "No documents or text were provided."
      )
    }

    val hasRejectionAttachment = attachments.any { it.bucket == ClaimBucketType.REJECTION_LETTER }
    if (!hasRejectionAttachment && pastedRejectionLetterText.isBlank()) {
      return SubmissionValidationOutcome.Invalid(
        errorType = ClaimErrorType.NOT_A_REJECTION_LETTER,
        reason = "Mandatory insurer rejection or settlement letter is missing."
      )
    }

    if (attachments.size > ClaimReviewConstants.MAX_TOTAL_FILES) {
      return SubmissionValidationOutcome.Invalid(
        errorType = ClaimErrorType.INVALID_FILE_TYPE,
        reason = "Attachment count (${attachments.size}) exceeds max ${ClaimReviewConstants.MAX_TOTAL_FILES}."
      )
    }

    ClaimBucketType.entries.forEach { bucket ->
      val count = attachments.count { it.bucket == bucket }
      if (count > ClaimReviewConstants.maxFilesForBucket(bucket)) {
        return SubmissionValidationOutcome.Invalid(
          errorType = ClaimErrorType.INVALID_FILE_TYPE,
          reason = "Bucket ${bucket.name} has $count files, exceeding max ${ClaimReviewConstants.maxFilesForBucket(bucket)}."
        )
      }
    }

    val totalBytes = attachments.sumOf { it.byteCount }
    if (attachments.isNotEmpty() && !AiModelConfig.isPayloadWithinSafeLimits(totalBytes)) {
      return SubmissionValidationOutcome.Invalid(
        errorType = ClaimErrorType.FILE_TOO_LARGE,
        reason = "Aggregate payload size ($totalBytes bytes) exceeds ${AiModelConfig.MAX_RAW_PAYLOAD_BYTES} bytes."
      )
    }

    if (pastedRejectionLetterText.isNotBlank()) {
      val letterTextCheck = validatePastedText(pastedRejectionLetterText, allowEmpty = false)
      if (letterTextCheck is PastedTextValidationOutcome.Invalid) {
        return SubmissionValidationOutcome.Invalid(letterTextCheck.errorType, letterTextCheck.reason)
      }
    }

    if (pastedPolicyText.isNotBlank()) {
      val policyTextCheck = validatePastedText(pastedPolicyText, allowEmpty = false)
      if (policyTextCheck is PastedTextValidationOutcome.Invalid) {
        return SubmissionValidationOutcome.Invalid(policyTextCheck.errorType, policyTextCheck.reason)
      }
    }

    return SubmissionValidationOutcome.Valid
  }

  /**
   * Checks byte-level signatures for PDF (%PDF-), JPEG, PNG, and WEBP.
   */
  fun detectMimeFromMagicBytes(bytes: ByteArray): String? {
    if (isPdfSignature(bytes)) return "application/pdf"
    if (isPngSignature(bytes)) return "image/png"
    if (isJpegSignature(bytes)) return "image/jpeg"
    if (isWebpSignature(bytes)) return "image/webp"
    return null
  }

  private fun isPdfSignature(bytes: ByteArray): Boolean {
    // PDF header: "%PDF-" (0x25 0x50 0x44 0x46 0x2D) plus version chars
    if (bytes.size < 8) return false
    return bytes[0] == 0x25.toByte() &&
      bytes[1] == 0x50.toByte() &&
      bytes[2] == 0x44.toByte() &&
      bytes[3] == 0x46.toByte() &&
      bytes[4] == 0x2D.toByte()
  }

  private fun isPngSignature(bytes: ByteArray): Boolean {
    if (bytes.size < 8) return false
    return bytes[0] == 0x89.toByte() &&
      bytes[1] == 0x50.toByte() &&
      bytes[2] == 0x4E.toByte() &&
      bytes[3] == 0x47.toByte() &&
      bytes[4] == 0x0D.toByte() &&
      bytes[5] == 0x0A.toByte() &&
      bytes[6] == 0x1A.toByte() &&
      bytes[7] == 0x0A.toByte()
  }

  private fun isJpegSignature(bytes: ByteArray): Boolean {
    // JPEG SOI (FF D8) followed by a valid JPEG segment marker (FF followed by non-zero/non-FF marker)
    if (bytes.size < 4) return false
    if (bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte() || bytes[2] != 0xFF.toByte()) {
      return false
    }
    val fourth = bytes[3].toInt() and 0xFF
    return fourth != 0x00 && fourth != 0xFF
  }

  private fun isWebpSignature(bytes: ByteArray): Boolean {
    // RIFF....WEBP
    if (bytes.size < 12) return false
    return bytes[0] == 0x52.toByte() &&
      bytes[1] == 0x49.toByte() &&
      bytes[2] == 0x46.toByte() &&
      bytes[3] == 0x46.toByte() &&
      bytes[8] == 0x57.toByte() &&
      bytes[9] == 0x45.toByte() &&
      bytes[10] == 0x42.toByte() &&
      bytes[11] == 0x50.toByte()
  }

  private fun canonicalizeSupportedMime(rawLowercase: String): String? {
    val clean = rawLowercase.substringBefore(';').trim()
    return when (clean) {
      "application/pdf" -> "application/pdf"
      "image/jpeg", "image/jpg" -> "image/jpeg"
      "image/png" -> "image/png"
      "image/webp" -> "image/webp"
      else -> null // Reject image/* wildcard and unsupported MIME types explicitly
    }
  }

  private fun extractFileExtension(fileName: String): String? {
    val lastDot = fileName.lastIndexOf('.')
    if (lastDot < 0 || lastDot == fileName.length - 1) return null
    val ext = fileName.substring(lastDot + 1).trim().lowercase(Locale.ROOT)
    return ext.takeIf { it.isNotEmpty() && !it.contains('/') && !it.contains('\\') }
  }
}
