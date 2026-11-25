package com.example.domain.engine

import com.example.model.ClaimBucketType
import com.example.model.ClaimClauseComparison
import com.example.model.ClaimComparisonResult
import com.example.model.ClaimDocumentMetadata
import com.example.model.ClaimDocumentRole
import com.example.model.ClaimErrorType
import com.example.model.ClaimEvidenceFinding
import com.example.model.ClaimExtractionFact
import com.example.model.ClaimReviewCopy
import com.example.model.ClaimReviewStrings
import com.example.model.ClaimSourceReference
import com.example.model.ComparisonOutcome
import com.example.model.DecisionType
import com.example.model.InsurerDecisionDetails
import com.example.model.InsurerStatedReason
import com.example.model.PolicyClauseRecord
import com.example.model.PolicyEvidenceSummary
import com.example.model.QuoteVerificationStatus
import com.example.model.SupportingClaimEvidenceSummary
import com.example.model.SupportedExplanationLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Pure, testable JSON parser, source-reference integrity validator, conservative outcome safeguard,
 * and multilingual legal/advisory safety filter for "Review My Rejected Health Claim".
 *
 * Design Invariants:
 * - Strictly rejects malformed JSON, missing structure, invalid enums, invalid bucket names,
 *   fabricated documentIds, fabricated/mismatched filenames, and out-of-range PDF page numbers.
 * - Never invents PDF page numbers for images or pasted text.
 * - Starts every AI quote as [QuoteVerificationStatus.AI_EXTRACTED_UNVERIFIED], upgrades to
 *   [QuoteVerificationStatus.PAGE_NUMBER_RANGE_CHECKED] only when a PDF's page count was inspected,
 *   and sets [QuoteVerificationStatus.EXACT_TEXT_MATCH] ONLY when independently matched against
 *   supplied extracted source text.
 * - Preserves original source quotations verbatim without translating or altering numbers/dates.
 * - Enforces conservative outcome downgrades when mandatory policy evidence is missing or conflicting,
 *   while never promoting an uncertain outcome upward.
 */
object ClaimReviewParser {

  sealed class ClaimParseOutcome {
    data class Success(val result: ClaimComparisonResult) : ClaimParseOutcome()
    data class Error(val errorType: ClaimErrorType, val message: String) : ClaimParseOutcome()
  }

  private const val WORD_CLASS = "\\p{L}\\p{N}\\p{M}"
  private const val B_START = "(?<![$WORD_CLASS])"
  private const val B_END = "(?![$WORD_CLASS])"

  private fun nfc(text: String): String =
    if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) {
      text
    } else {
      Normalizer.normalize(text, Normalizer.Form.NFC)
    }

  private fun uRegex(pattern: String): Regex =
    Regex("$B_START(?:${nfc(pattern)})$B_END", RegexOption.IGNORE_CASE)

  /**
   * Non-exhaustive unsafe legal / settlement / concealment / guaranteed-payout patterns
   * across English, Hindi (Devanagari), Bengali, and Marathi (Devanagari).
   */
  private val POLICY_DOCUMENT_ROLES: Set<ClaimDocumentRole> = setOf(
    ClaimDocumentRole.POLICY_SCHEDULE,
    ClaimDocumentRole.BASE_POLICY_WORDING,
    ClaimDocumentRole.ENDORSEMENT_OR_RIDER,
    ClaimDocumentRole.OTHER
  )

  private val UNSAFE_ADVICE_PATTERNS: List<Regex> = listOf(
    // English legal directives, fraud accusations, misrepresentation, settlement orders, guaranteed payouts
    uRegex("(?:(?:you\\s+(?:should|must|can|need\\s+to)|go\\s+ahead\\s+and|immediately)\\s+)?(?:file\\s+a\\s+lawsuit|sue\\s+(?:the\\s+)?(?:insurer|company|tpa)|take\\s+(?:them|the\\s+insurer)\\s+to\\s+court|approach\\s+consumer\\s+court\\s+and\\s+sue)"),
    uRegex("(?:accuse|charge)\\s+(?:the\\s+)?(?:insurer|company|tpa)\\s+of\\s+(?:fraud|cheating|criminal)"),
    uRegex("(?:this\\s+(?:rejection|denial|deduction)\\s+is\\s+(?:illegal|unlawful|fraudulent)|insurer\\s+committed\\s+fraud)"),
    uRegex("(?:hide|conceal|omit|do\\s+not\\s+mention|don't\\s+disclose|lie\\s+about|fabricate|alter)\\s+(?:your\\s+)?(?:medical\\s+history|pre[-\\s]?existing|diabetes|hypertension|condition|facts|records|bills)"),
    uRegex("(?:you\\s+(?:should|must|need\\s+to)|do\\s+not|don't)\\s+(?:accept|reject|decline|refuse|sign)\\s+(?:this|the)\\s+settlement"),
    uRegex("(?:guaranteed\\s+(?:payout|claim\\s+(?:approval|settlement))|claim\\s+will\\s+(?:definitely|certainly|100%)\\s+be\\s+(?:approved|paid)|you\\s+will\\s+definitely\\s+win)"),

    // Hindi (Devanagari)
    uRegex("(?:बीमा\\s+कंपनी|इंश्योरेंस\\s+कंपनी|TPA)\\s+पर\\s+(?:मुकदमा\\s+(?:दायर\\s+)?करें|केस\\s+करें|केस\\s+ठोकें)"),
    uRegex("(?:अदालत|कंज्यूमर\\s+कोर्ट)\\s+में\\s+मुकदमा\\s+दायर\\s+करें"),
    uRegex("(?:बीमा\\s+कंपनी\\s+पर|उन\\s+पर)\\s+धोखाधड़ी\\s+का\\s+आरोप\\s+लगाएं"),
    uRegex("(?:पुरानी\\s+बीमारी|डायबिटीज|मेडिकल\\s+इतिहास|तथ्य|जानकारी)\\s+(?:छिपाएं|छुपाएं|मत\\s+बताएं)|गलत\\s+जानकारी\\s+दें"),
    uRegex("(?:सेटलमेंट|निपटारा)\\s+(?:स्वीकार\\s+करें|मंजूर\\s+करें|ठुकरा\\s+दें|अस्वीकार\\s+करें)"),
    uRegex("(?:क्लेम\\s+(?:पक्का|निश्चित|जरूर)\\s+(?:पास|मंजूर)\\s+होगा|पूरा\\s+पैसा\\s+(?:पक्का|जरूर|गारंटी\\s+से)\\s+मिलेगा|गारंटीड\\s+पेआउट)"),

    // Bengali (Bengali script)
    uRegex("(?:বীমা\\s+কোম্পানির\\s+বিরুদ্ধে|তাদের\\s+বিরুদ্ধে)\\s+(?:আইনি\\s+মামলা\\s+করুন|আদালতে\\s+মামলা\\s+করুন|মামলা\\s+ঠুকুন)"),
    uRegex("(?:বীমা\\s+কোম্পানির\\s+বিরুদ্ধে|তাদের\\s+বিরুদ্ধে)\\s+প্রতারণার\\s+অভিযোগ\\s+করুন"),
    uRegex("(?:আগের\\s+অসুস্থতা|ডায়াবেটিস|চিকিৎসার\\s+ইতিহাস|তথ্য)\\s+(?:লুকান|গোপন\\s+করুন|বলবেন\\s+না)|মিথ্যা\\s+তথ্য\\s+দিন"),
    uRegex("(?:এই\\s+)?(?:মীমাংসা|সেটেলমেন্ট)\\s+(?:মেনে\\s+নিন|গ্রহণ\\s+করুন|প্রত্যাখ্যান\\s+করুন|বাতিল\\s+করুন)"),
    uRegex("(?:ক্লেম\\s+(?:অবশ্যই|নিশ্চিত|পাকা)\\s+(?:পাস|অনুমোদিত)\\s+হবে|নিশ্চিত\\s+টাকা\\s+পাবেন|গ্যারান্টিড\\s+পেআউট)"),

    // Marathi (Devanagari)
    uRegex("(?:विमा\\s+कंपनीवर|त्यांच्यावर)\\s+(?:खटला\\s+दाखल\\s+करा|कोर्टात\\s+दावा\\s+ठोका|कायदेशीर\\s+खटला\\s+भरा)"),
    uRegex("(?:विमा\\s+कंपनीवर|त्यांच्यावर)\\s+फसवणुकीचा\\s+आरोप\\s+करा"),
    uRegex("(?:जुना\\s+आजार|मधुमेह|वैद्यकीय\\s+इतिहास|तथ्ये|माहिती)\\s+(?:लपवा|सांगू\\s+नका)|खोटी\\s+माहिती\\s+द्या"),
    uRegex("(?:हा\\s+)?(?:तडजोड|सेटलमेंट)\\s+(?:प्रस्ताव\\s+)?(?:स्वीकारा|मान्य\\s+करा|नाकारा|फेटाळा)"),
    uRegex("(?:क्लेम\\s+(?:नक्की|खात्रीने|निश्चित)\\s+मंजूर\\s+होईल|हमीने\\s+पैसे\\s+मिळतील|गॅरंटीड\\s+पेआउट)")
  )

  fun containsUnsafeAdvice(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val normalized = nfc(text)
    return UNSAFE_ADVICE_PATTERNS.any { it.containsMatchIn(normalized) }
  }

  private fun sanitizeTextOrNull(text: String?): String? {
    val trimmed = text?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
      ?: return null
    return if (containsUnsafeAdvice(trimmed)) null else trimmed
  }

  private fun sanitizeTextOrFallback(text: String?, fallback: String): String =
    sanitizeTextOrNull(text) ?: fallback

  fun parseResponse(
    rawJson: String,
    languageCode: String?,
    submittedDocuments: List<ClaimDocumentMetadata>,
    hasRejectionLetterInput: Boolean = submittedDocuments.any { it.bucket == ClaimBucketType.REJECTION_LETTER },
    verifiedExtractedTextsByDocumentId: Map<String, String> = emptyMap()
  ): ClaimParseOutcome {
    val copy = ClaimReviewCopy.forLanguage(languageCode)

    // Safeguard 1: Mandatory rejection letter must be present
    val hasRejectionInMetadata = submittedDocuments.any { it.bucket == ClaimBucketType.REJECTION_LETTER }
    if (!hasRejectionLetterInput || !hasRejectionInMetadata) {
      return ClaimParseOutcome.Error(
        errorType = ClaimErrorType.NOT_A_REJECTION_LETTER,
        message = copy.missingRejectionLetterMessage
      )
    }

    val cleaned = stripCodeFences(rawJson)
    if (cleaned.isBlank() || !cleaned.startsWith("{") || !cleaned.endsWith("}")) {
      return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
    }

    // Strict JSON must be a single JSON object without trailing stray structure
    val root = try {
      JSONObject(cleaned)
    } catch (_: Exception) {
      return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
    }

    // Handle explicit domain error sentinels
    if (root.has("error") && !root.isNull("error")) {
      val errCode = root.optString("error", "").trim().lowercase()
      if (errCode.isNotEmpty()) {
        return when (errCode) {
          "not_a_rejection_letter" -> ClaimParseOutcome.Error(
            ClaimErrorType.NOT_A_REJECTION_LETTER,
            copy.notARejectionLetterMessage
          )
          "unreadable" -> ClaimParseOutcome.Error(
            ClaimErrorType.UNREADABLE,
            copy.unreadableDocumentMessage
          )
          else -> ClaimParseOutcome.Error(
            ClaimErrorType.INVALID_RESPONSE,
            copy.invalidResponseMessage
          )
        }
      }
    }

    // Strict required top-level structure check
    val requiredKeys = listOf("decision_type", "comparison_outcome", "safe_summary", "insurer_decision")
    if (requiredKeys.any { !root.has(it) || root.isNull(it) }) {
      return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
    }

    val decisionObj = root.optJSONObject("insurer_decision")
      ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val decisionType = DecisionType.fromWireValue(root.optString("decision_type", ""))
      ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val aiOutcome = ComparisonOutcome.fromWireValue(root.optString("comparison_outcome", ""))
      ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val docsById = submittedDocuments.associateBy { it.documentId }
    val collectedRefs = mutableListOf<ClaimSourceReference>()

    // Parse insurer stated reasons (strictly validating every ClaimSourceReference)
    val reasonsArr = decisionObj.optJSONArray("stated_reasons")
      ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
    if (reasonsArr.length() == 0) {
      return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
    }

    val statedReasons = mutableListOf<InsurerStatedReason>()
    for (i in 0 until reasonsArr.length()) {
      val itemObj = reasonsArr.optJSONObject(i)
        ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
      val reasonText = itemObj.optNonBlankString("allegation_summary")
        ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
      val sourceRefObj = itemObj.optJSONObject("source_reference")
        ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
      val sourceRef = validateAndBuildSourceReference(
        refObj = sourceRefObj,
        docsById = docsById,
        verifiedExtractedTextsByDocumentId = verifiedExtractedTextsByDocumentId,
        expectedBuckets = setOf(ClaimBucketType.REJECTION_LETTER)
      ) ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

      collectedRefs.add(sourceRef)
      statedReasons.add(
        InsurerStatedReason(
          allegationSummaryPlainLanguage = sanitizeTextOrFallback(reasonText, copy.safeSummaryFallback),
          citedClauseLabel = sanitizeTextOrNull(itemObj.optNonBlankString("cited_clause_label")),
          sourceReference = sourceRef
        )
      )
    }

    val insurerDecision = InsurerDecisionDetails(
      decisionType = decisionType,
      claimReferenceNumber = sanitizeTextOrNull(decisionObj.optNonBlankString("claim_reference_number")),
      insurerName = sanitizeTextOrNull(decisionObj.optNonBlankString("insurer_name")),
      tpaName = sanitizeTextOrNull(decisionObj.optNonBlankString("tpa_name")),
      decisionDate = sanitizeTextOrNull(decisionObj.optNonBlankString("decision_date")),
      hospitalizationDates = sanitizeTextOrNull(decisionObj.optNonBlankString("hospitalization_dates")),
      claimedAmount = sanitizeTextOrNull(decisionObj.optNonBlankString("claimed_amount")),
      approvedAmount = sanitizeTextOrNull(decisionObj.optNonBlankString("approved_amount")),
      deductedAmount = sanitizeTextOrNull(decisionObj.optNonBlankString("deducted_amount")),
      statedReasons = statedReasons
    )

    // Parse Policy Evidence (must originate strictly from POLICY_DOCS)
    val policyObj = root.optJSONObject("policy_evidence") ?: JSONObject()
    val policyBuckets = setOf(ClaimBucketType.POLICY_DOCS)
    val policyScheduleFacts = parseFactsArray(
      arr = policyObj.optJSONArray("policy_schedule_facts"),
      docsById = docsById,
      verifiedTexts = verifiedExtractedTextsByDocumentId,
      collectedRefs = collectedRefs,
      isAllegation = false,
      copy = copy,
      expectedBuckets = policyBuckets
    ) ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val basePolicyClauses = parseClausesArray(
      arr = policyObj.optJSONArray("base_policy_clauses"),
      defaultRole = ClaimDocumentRole.BASE_POLICY_WORDING,
      docsById = docsById,
      verifiedTexts = verifiedExtractedTextsByDocumentId,
      collectedRefs = collectedRefs,
      copy = copy,
      expectedBuckets = policyBuckets
    ) ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val endorsementsAndRiders = parseClausesArray(
      arr = policyObj.optJSONArray("endorsements_and_riders"),
      defaultRole = ClaimDocumentRole.ENDORSEMENT_OR_RIDER,
      docsById = docsById,
      verifiedTexts = verifiedExtractedTextsByDocumentId,
      collectedRefs = collectedRefs,
      copy = copy,
      expectedBuckets = policyBuckets
    ) ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val waitingPeriods = parseClausesArray(
      arr = policyObj.optJSONArray("waiting_periods"),
      defaultRole = ClaimDocumentRole.BASE_POLICY_WORDING,
      docsById = docsById,
      verifiedTexts = verifiedExtractedTextsByDocumentId,
      collectedRefs = collectedRefs,
      copy = copy,
      expectedBuckets = policyBuckets
    ) ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val exclusions = parseClausesArray(
      arr = policyObj.optJSONArray("exclusions"),
      defaultRole = ClaimDocumentRole.BASE_POLICY_WORDING,
      docsById = docsById,
      verifiedTexts = verifiedExtractedTextsByDocumentId,
      collectedRefs = collectedRefs,
      copy = copy,
      expectedBuckets = policyBuckets
    ) ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val exceptionsToExclusions = parseClausesArray(
      arr = policyObj.optJSONArray("exceptions_to_exclusions"),
      defaultRole = ClaimDocumentRole.BASE_POLICY_WORDING,
      docsById = docsById,
      verifiedTexts = verifiedExtractedTextsByDocumentId,
      collectedRefs = collectedRefs,
      copy = copy,
      expectedBuckets = policyBuckets
    ) ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val hasConflictFlag = policyObj.optBoolean("has_schedule_or_endorsement_conflict", false)
    val conflictNote = sanitizeTextOrNull(policyObj.optNonBlankString("schedule_or_endorsement_conflict_note"))

    val policyEvidence = PolicyEvidenceSummary(
      policyScheduleFacts = policyScheduleFacts,
      basePolicyClauses = basePolicyClauses,
      endorsementsAndRiders = endorsementsAndRiders,
      waitingPeriods = waitingPeriods,
      exclusions = exclusions,
      exceptionsToExclusions = exceptionsToExclusions,
      coPayment = sanitizeTextOrNull(policyObj.optNonBlankString("co_payment")),
      deductible = sanitizeTextOrNull(policyObj.optNonBlankString("deductible")),
      roomRentLimit = sanitizeTextOrNull(policyObj.optNonBlankString("room_rent_limit")),
      subLimits = sanitizeTextOrNull(policyObj.optNonBlankString("sub_limits")),
      claimNotificationConditions = sanitizeTextOrNull(policyObj.optNonBlankString("claim_notification_conditions")),
      hasScheduleOrEndorsementConflict = hasConflictFlag,
      scheduleOrEndorsementConflictNote = conflictNote
    )

    // Parse Supporting Evidence (must originate strictly from SUPPORTING_CLAIM_DOCS)
    val supportObj = root.optJSONObject("supporting_evidence") ?: JSONObject()
    val supportingBuckets = setOf(ClaimBucketType.SUPPORTING_CLAIM_DOCS)
    val supportingEvidence = SupportingClaimEvidenceSummary(
      claimFormFacts = parseFactsArray(supportObj.optJSONArray("claim_form_facts"), docsById, verifiedExtractedTextsByDocumentId, collectedRefs, false, copy, supportingBuckets)
        ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage),
      dischargeSummaryFacts = parseFactsArray(supportObj.optJSONArray("discharge_summary_facts"), docsById, verifiedExtractedTextsByDocumentId, collectedRefs, false, copy, supportingBuckets)
        ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage),
      hospitalBillFacts = parseFactsArray(supportObj.optJSONArray("hospital_bill_facts"), docsById, verifiedExtractedTextsByDocumentId, collectedRefs, false, copy, supportingBuckets)
        ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage),
      medicalRecordFacts = parseFactsArray(supportObj.optJSONArray("medical_record_facts"), docsById, verifiedExtractedTextsByDocumentId, collectedRefs, false, copy, supportingBuckets)
        ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage),
      investigationReportFacts = parseFactsArray(supportObj.optJSONArray("investigation_report_facts"), docsById, verifiedExtractedTextsByDocumentId, collectedRefs, false, copy, supportingBuckets)
        ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage),
      insurerCorrespondenceFacts = parseFactsArray(supportObj.optJSONArray("insurer_correspondence_facts"), docsById, verifiedExtractedTextsByDocumentId, collectedRefs, true, copy, supportingBuckets)
        ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
    )

    // Parse Clause Comparisons
    val comparisonsArr = root.optJSONArray("clause_comparisons")
    val clauseComparisons = mutableListOf<ClaimClauseComparison>()
    if (comparisonsArr != null) {
      for (i in 0 until comparisonsArr.length()) {
        val cObj = comparisonsArr.optJSONObject(i)
          ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
        val insurerCitation = cObj.optNonBlankString("insurer_cited_clause_or_allegation")
          ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
        val insurerRef = cObj.optJSONObject("insurer_source_reference")?.let {
          validateAndBuildSourceReference(it, docsById, verifiedExtractedTextsByDocumentId, setOf(ClaimBucketType.REJECTION_LETTER))
            ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
        }
        if (insurerRef != null) collectedRefs.add(insurerRef)

        val matchingClause = cObj.optJSONObject("matching_policy_clause")?.let { clauseObj ->
          parseSingleClause(clauseObj, ClaimDocumentRole.BASE_POLICY_WORDING, docsById, verifiedExtractedTextsByDocumentId, collectedRefs, copy, policyBuckets)
            ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)
        }

        val noteRaw = cObj.optNonBlankString("plain_language_comparison") ?: copy.notMentionedFallback
        val safeNote = sanitizeTextOrFallback(noteRaw, copy.safeSummaryFallback)
        val unverifiedOrMissing = matchingClause == null ||
          (matchingClause.sourceReference.quoteVerificationStatus != QuoteVerificationStatus.EXACT_TEXT_MATCH)

        clauseComparisons.add(
          ClaimClauseComparison(
            insurerCitedClauseOrAllegation = sanitizeTextOrFallback(insurerCitation, copy.notMentionedFallback),
            insurerSourceReference = insurerRef,
            matchingPolicyClause = matchingClause,
            plainLanguageComparison = safeNote,
            hasMissingOrUnverifiedCitation = unverifiedOrMissing
          )
        )
      }
    }

    val supportingItems = parseEvidenceFindings(
      arr = root.optJSONArray("supporting_evidence_items"),
      docsById = docsById,
      verifiedTexts = verifiedExtractedTextsByDocumentId,
      collectedRefs = collectedRefs,
      fallback = copy.safeSummaryFallback
    ) ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val contradictoryItems = parseEvidenceFindings(
      arr = root.optJSONArray("contradictory_evidence_items"),
      docsById = docsById,
      verifiedTexts = verifiedExtractedTextsByDocumentId,
      collectedRefs = collectedRefs,
      fallback = copy.safeSummaryFallback
    ) ?: return ClaimParseOutcome.Error(ClaimErrorType.INVALID_RESPONSE, copy.invalidResponseMessage)

    val missingEvidenceItems = parseSafeStringList(root.optJSONArray("missing_evidence_items"))
    val questions = parseSafeStringList(root.optJSONArray("questions_to_ask_insurer"))
    val docsToRequest = parseSafeStringList(root.optJSONArray("documents_to_request_or_provide"))

    // Conservative outcome safeguards:
    // - Downgrade overconfident AI conclusions when required policy evidence is absent, conflicting, or unverified.
    // - PAGE_NUMBER_RANGE_CHECKED never establishes quotation accuracy; only EXACT_TEXT_MATCH verifies wording.
    // - Never promote an uncertain outcome upward.
    val hasPolicyDocsInSubmission = submittedDocuments.any { it.bucket == ClaimBucketType.POLICY_DOCS }
    val allPolicyClauses = basePolicyClauses +
      endorsementsAndRiders +
      waitingPeriods +
      exclusions +
      exceptionsToExclusions +
      clauseComparisons.mapNotNull { it.matchingPolicyClause }
    val hasAnyPolicyClauseEvidence = allPolicyClauses.isNotEmpty()
    val hasVerifiedPolicyClause = hasAnyPolicyClauseEvidence && allPolicyClauses.any {
      it.sourceReference.quoteVerificationStatus == QuoteVerificationStatus.EXACT_TEXT_MATCH
    }
    val hasUnverifiedMatchingComparison = clauseComparisons.any {
      it.matchingPolicyClause != null &&
        it.matchingPolicyClause.sourceReference.quoteVerificationStatus != QuoteVerificationStatus.EXACT_TEXT_MATCH
    }

    var finalOutcome = aiOutcome
    var downgradedByGuard = false

    if (!hasPolicyDocsInSubmission) {
      if (finalOutcome != ComparisonOutcome.INSUFFICIENT_INFORMATION) {
        finalOutcome = ComparisonOutcome.INSUFFICIENT_INFORMATION
        downgradedByGuard = true
      }
    } else if (hasConflictFlag && (finalOutcome == ComparisonOutcome.DOCUMENT_SUPPORTED || finalOutcome == ComparisonOutcome.POSSIBLE_MISMATCH)) {
      finalOutcome = ComparisonOutcome.DOCUMENT_CONFLICT
      downgradedByGuard = true
    } else if ((finalOutcome == ComparisonOutcome.DOCUMENT_SUPPORTED || finalOutcome == ComparisonOutcome.POSSIBLE_MISMATCH) &&
      (!hasVerifiedPolicyClause || hasUnverifiedMatchingComparison)
    ) {
      finalOutcome = ComparisonOutcome.INSUFFICIENT_INFORMATION
      downgradedByGuard = true
    }

    val aiSummaryRaw = root.optNonBlankString("safe_summary")
    val aiRationaleRaw = root.optNonBlankString("outcome_rationale")

    val safeSummary = sanitizeTextOrFallback(aiSummaryRaw, copy.safeSummaryFallback)
    val defaultRationaleForFinalOutcome = defaultRationaleForOutcome(finalOutcome, copy)
    val outcomeRationale = if (downgradedByGuard) {
      defaultRationaleForFinalOutcome
    } else {
      sanitizeTextOrFallback(aiRationaleRaw, defaultRationaleForFinalOutcome)
    }

    return ClaimParseOutcome.Success(
      ClaimComparisonResult(
        languageCode = copy.languageCode,
        canonicalLanguageName = copy.canonicalLanguageName,
        insurerDecision = insurerDecision,
        policyEvidence = policyEvidence,
        supportingEvidence = supportingEvidence,
        comparisonOutcome = finalOutcome,
        outcomeRationale = outcomeRationale,
        safeSummary = safeSummary,
        clauseComparisons = clauseComparisons,
        supportingEvidenceItems = supportingItems,
        contradictoryEvidenceItems = contradictoryItems,
        missingEvidenceItems = missingEvidenceItems,
        questionsToAskInsurer = questions,
        documentsToRequestOrProvide = docsToRequest,
        sourceReferences = collectedRefs.distinct(),
        hasPolicyDocumentsProvided = hasPolicyDocsInSubmission,
        outcomeDowngradedBySafetyGuard = downgradedByGuard,
        disclaimer = copy.disclaimer,
        nonLegalAdviceWarning = copy.nonLegalAdviceWarning,
        linguisticFilteringLimitationNote = copy.linguisticFilteringLimitationNote
      )
    )
  }

  private fun defaultRationaleForOutcome(outcome: ComparisonOutcome, copy: ClaimReviewStrings): String =
    when (outcome) {
      ComparisonOutcome.DOCUMENT_SUPPORTED -> copy.documentSupportedExplanation
      ComparisonOutcome.POSSIBLE_MISMATCH -> copy.possibleMismatchExplanation
      ComparisonOutcome.INSUFFICIENT_INFORMATION -> copy.insufficientInformationExplanation
      ComparisonOutcome.DOCUMENT_CONFLICT -> copy.documentConflictExplanation
    }

  /**
   * Validates every AI source citation strictly against submitted [ClaimDocumentMetadata]:
   * - documentId must belong to a submitted document in the current session.
   * - bucket and originalFileName must match that document exactly.
   * - images & pasted text must have null pageNumber (rejects any invented page number).
   * - PDFs reject pageNumber <= 0, and if pageCount was inspected, reject pageNumber > pageCount.
   * - Verification status starts at AI_EXTRACTED_UNVERIFIED; upgrades to PAGE_NUMBER_RANGE_CHECKED
   *   only after checking PDF pageCount bounds; upgrades to EXACT_TEXT_MATCH ONLY if verbatim quote
   *   matches independently extracted text in [verifiedExtractedTextsByDocumentId].
   */
  private fun validateAndBuildSourceReference(
    refObj: JSONObject,
    docsById: Map<String, ClaimDocumentMetadata>,
    verifiedExtractedTextsByDocumentId: Map<String, String>,
    expectedBuckets: Set<ClaimBucketType>? = null
  ): ClaimSourceReference? {
    val docId = refObj.optNonBlankString("document_id") ?: return null
    val doc = docsById[docId] ?: return null

    val rawBucket = refObj.optNonBlankString("bucket") ?: return null
    val bucket = ClaimBucketType.fromWireValue(rawBucket) ?: return null
    if (bucket != doc.bucket) return null
    if (expectedBuckets != null && doc.bucket !in expectedBuckets) return null

    val fileName = refObj.optNonBlankString("original_file_name") ?: return null
    if (fileName != doc.originalFileName) return null

    // Quote must be preserved verbatim; never translated or fabricated
    val rawQuote = if (refObj.has("extracted_quote") && !refObj.isNull("extracted_quote")) {
      refObj.optString("extracted_quote", "")
    } else {
      ""
    }
    if (rawQuote.isBlank() || rawQuote.equals("null", ignoreCase = true)) return null

    // Validate pageNumber rules strictly
    val pageNumber: Int? = if (!refObj.has("page_number") || refObj.isNull("page_number")) {
      null
    } else {
      val parsedInt = when (val rawPage = refObj.opt("page_number")) {
        is Int -> rawPage
        is Number -> {
          val asLong = rawPage.toLong()
          if (rawPage.toDouble() != asLong.toDouble() || asLong > Int.MAX_VALUE) return null
          asLong.toInt()
        }
        else -> return null
      }
      if (parsedInt <= 0) return null
      // Images and pasted text must never receive an invented PDF page number
      val isPdf = !doc.isPastedText && doc.detectedMimeType.equals("application/pdf", ignoreCase = true)
      if (!isPdf) return null
      if (doc.pageCount != null && parsedInt > doc.pageCount) return null
      parsedInt
    }

    val isPdfDoc = !doc.isPastedText && doc.detectedMimeType.equals("application/pdf", ignoreCase = true)
    val extractedSourceText = verifiedExtractedTextsByDocumentId[doc.documentId]
    val hasIndependentExactTextMatch = !extractedSourceText.isNullOrBlank() &&
      nfc(extractedSourceText).contains(nfc(rawQuote))

    val verificationStatus = when {
      hasIndependentExactTextMatch -> QuoteVerificationStatus.EXACT_TEXT_MATCH
      isPdfDoc && pageNumber != null && doc.pageCount != null && pageNumber in 1..doc.pageCount ->
        QuoteVerificationStatus.PAGE_NUMBER_RANGE_CHECKED
      else -> QuoteVerificationStatus.AI_EXTRACTED_UNVERIFIED
    }

    return ClaimSourceReference(
      documentId = doc.documentId,
      bucket = doc.bucket,
      originalFileName = doc.originalFileName,
      pageNumber = pageNumber,
      extractedQuote = rawQuote,
      sourceLanguageCode = refObj.optNonBlankString("source_language_code"),
      quoteVerificationStatus = verificationStatus
    )
  }

  private fun parseFactsArray(
    arr: JSONArray?,
    docsById: Map<String, ClaimDocumentMetadata>,
    verifiedTexts: Map<String, String>,
    collectedRefs: MutableList<ClaimSourceReference>,
    isAllegation: Boolean,
    copy: ClaimReviewStrings,
    expectedBuckets: Set<ClaimBucketType>? = null
  ): List<ClaimExtractionFact>? {
    if (arr == null) return emptyList()
    val result = mutableListOf<ClaimExtractionFact>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: return null
      val label = obj.optNonBlankString("label") ?: return null
      val value = obj.optNonBlankString("value") ?: return null
      val ref = obj.optJSONObject("source_reference")?.let {
        validateAndBuildSourceReference(it, docsById, verifiedTexts, expectedBuckets) ?: return null
      }
      if (ref != null) collectedRefs.add(ref)
      val forcedAllegation = isAllegation || ref?.bucket == ClaimBucketType.REJECTION_LETTER
      result.add(
        ClaimExtractionFact(
          label = sanitizeTextOrFallback(label, copy.notMentionedFallback),
          value = sanitizeTextOrFallback(value, copy.notMentionedFallback),
          sourceReference = ref,
          isInsurerAllegationOnly = forcedAllegation || obj.optBoolean("is_insurer_allegation_only", false)
        )
      )
    }
    return result
  }

  private fun parseClausesArray(
    arr: JSONArray?,
    defaultRole: ClaimDocumentRole,
    docsById: Map<String, ClaimDocumentMetadata>,
    verifiedTexts: Map<String, String>,
    collectedRefs: MutableList<ClaimSourceReference>,
    copy: ClaimReviewStrings,
    expectedBuckets: Set<ClaimBucketType>? = null
  ): List<PolicyClauseRecord>? {
    if (arr == null) return emptyList()
    val result = mutableListOf<PolicyClauseRecord>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: return null
      val record = parseSingleClause(obj, defaultRole, docsById, verifiedTexts, collectedRefs, copy, expectedBuckets) ?: return null
      result.add(record)
    }
    return result
  }

  private fun parseSingleClause(
    obj: JSONObject,
    defaultRole: ClaimDocumentRole,
    docsById: Map<String, ClaimDocumentMetadata>,
    verifiedTexts: Map<String, String>,
    collectedRefs: MutableList<ClaimSourceReference>,
    copy: ClaimReviewStrings,
    expectedBuckets: Set<ClaimBucketType>? = null
  ): PolicyClauseRecord? {
    val title = obj.optNonBlankString("clause_title") ?: return null
    val summary = obj.optNonBlankString("plain_language_summary") ?: return null
    val roleRaw = obj.optNonBlankString("document_role")
    val role = if (roleRaw != null) {
      ClaimDocumentRole.fromWireValue(roleRaw) ?: return null
    } else {
      defaultRole
    }
    if (expectedBuckets == setOf(ClaimBucketType.POLICY_DOCS) && role !in POLICY_DOCUMENT_ROLES) {
      return null
    }
    val refObj = obj.optJSONObject("source_reference") ?: return null
    val ref = validateAndBuildSourceReference(refObj, docsById, verifiedTexts, expectedBuckets) ?: return null
    collectedRefs.add(ref)

    val safeTitle = sanitizeTextOrFallback(title, copy.notMentionedFallback)
    val safeSummary = sanitizeTextOrFallback(summary, copy.safeSummaryFallback)
    return PolicyClauseRecord(
      clauseTitle = safeTitle,
      clauseIdentifier = sanitizeTextOrNull(obj.optNonBlankString("clause_identifier")),
      documentRole = role,
      plainLanguageSummary = safeSummary,
      statedAmountOrPeriod = sanitizeTextOrNull(obj.optNonBlankString("stated_amount_or_period")),
      sourceReference = ref
    )
  }

  private fun parseEvidenceFindings(
    arr: JSONArray?,
    docsById: Map<String, ClaimDocumentMetadata>,
    verifiedTexts: Map<String, String>,
    collectedRefs: MutableList<ClaimSourceReference>,
    fallback: String
  ): List<ClaimEvidenceFinding>? {
    if (arr == null) return emptyList()
    val result = mutableListOf<ClaimEvidenceFinding>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: return null
      val desc = obj.optNonBlankString("description") ?: return null
      val ref = obj.optJSONObject("source_reference")?.let {
        validateAndBuildSourceReference(it, docsById, verifiedTexts) ?: return null
      }
      if (ref != null) collectedRefs.add(ref)
      val forcedAllegation = ref?.bucket == ClaimBucketType.REJECTION_LETTER
      result.add(
        ClaimEvidenceFinding(
          description = sanitizeTextOrFallback(desc, fallback),
          isInsurerAllegationOnly = forcedAllegation || obj.optBoolean("is_insurer_allegation_only", false),
          sourceReference = ref
        )
      )
    }
    return result
  }

  private fun parseSafeStringList(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    val result = mutableListOf<String>()
    for (i in 0 until arr.length()) {
      val item = arr.optString(i, "").trim()
      if (item.isNotBlank() && !item.equals("null", ignoreCase = true) && !containsUnsafeAdvice(item)) {
        result.add(item)
      }
    }
    return result
  }

  private fun stripCodeFences(raw: String): String {
    var trimmed = raw.trim()
    if (trimmed.startsWith("```")) {
      val firstNewline = trimmed.indexOf('\n')
      if (firstNewline >= 0) {
        trimmed = trimmed.substring(firstNewline + 1)
      }
      if (trimmed.endsWith("```")) {
        trimmed = trimmed.substring(0, trimmed.length - 3)
      }
    }
    return trimmed.trim()
  }

  private fun JSONObject.optNonBlankString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)?.toString()?.trim() ?: return null
    if (value.isBlank() || value.equals("null", ignoreCase = true)) return null
    return value
  }
}
