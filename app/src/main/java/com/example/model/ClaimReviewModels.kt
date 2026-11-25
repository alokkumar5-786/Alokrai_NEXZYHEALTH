package com.example.model

import java.util.UUID

/**
 * Stage 1 Domain Models, Source Reference Integrity Contracts, State, and 4-Language Copy
 * for "Review My Rejected Health Claim".
 *
 * PRIVACY & SAFETY DESIGN:
 * - Uploaded document ByteArrays stay strictly in transient in-memory [ClaimDocumentAttachment]
 *   objects and are never included in persistent database entities.
 * - [ClaimSourceReference] explicitly tracks [QuoteVerificationStatus] so unverified AI quotations
 *   are never disguised as independently verified source text.
 */

enum class DecisionType(val wireValue: String) {
  CASHLESS_DENIAL("cashless_denial"),
  FINAL_REPUDIATION("final_repudiation"),
  PARTIAL_SETTLEMENT_DEDUCTION("partial_settlement_deduction"),
  UNCLEAR("unclear");

  companion object {
    fun fromWireValue(raw: String?): DecisionType? {
      val normalized = raw?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
      return entries.firstOrNull { it.wireValue == normalized }
    }
  }
}

enum class ComparisonOutcome(val wireValue: String) {
  DOCUMENT_SUPPORTED("document_supported"),
  POSSIBLE_MISMATCH("possible_mismatch"),
  INSUFFICIENT_INFORMATION("insufficient_information"),
  DOCUMENT_CONFLICT("document_conflict");

  companion object {
    fun fromWireValue(raw: String?): ComparisonOutcome? {
      val normalized = raw?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
      return entries.firstOrNull { it.wireValue == normalized }
    }
  }
}

enum class ClaimBucketType(val wireValue: String) {
  POLICY_DOCS("policy_docs"),
  REJECTION_LETTER("rejection_letter"),
  SUPPORTING_CLAIM_DOCS("supporting_claim_docs");

  companion object {
    fun fromWireValue(raw: String?): ClaimBucketType? {
      val normalized = raw?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
      return entries.firstOrNull { it.wireValue == normalized }
    }
  }
}

enum class ClaimErrorType {
  NOT_A_REJECTION_LETTER,
  UNREADABLE,
  FILE_TOO_LARGE,
  INVALID_FILE_TYPE,
  NO_INTERNET,
  TIMEOUT,
  INVALID_RESPONSE,
  INTERNAL_ERROR
}

enum class QuoteVerificationStatus {
  /** Quote came from AI model output only; not independently checked against source pages or text. */
  AI_EXTRACTED_UNVERIFIED,
  /** Cited PDF page number was checked against the uploaded PDF's inspected page count; quote text itself is unverified. */
  PAGE_NUMBER_RANGE_CHECKED,
  /** Quoted wording matched actual extracted source text from the same documentId. */
  EXACT_TEXT_MATCH
}

enum class ClaimDocumentRole(val wireValue: String) {
  POLICY_SCHEDULE("policy_schedule"),
  BASE_POLICY_WORDING("base_policy_wording"),
  ENDORSEMENT_OR_RIDER("endorsement_or_rider"),
  REJECTION_OR_SETTLEMENT_LETTER("rejection_or_settlement_letter"),
  CLAIM_FORM("claim_form"),
  DISCHARGE_SUMMARY("discharge_summary"),
  HOSPITAL_BILL("hospital_bill"),
  MEDICAL_RECORD("medical_record"),
  INVESTIGATION_REPORT("investigation_report"),
  INSURER_CORRESPONDENCE("insurer_correspondence"),
  OTHER("other");

  companion object {
    fun fromWireValue(raw: String?): ClaimDocumentRole? {
      val normalized = raw?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
      return entries.firstOrNull { it.wireValue == normalized }
    }
  }
}

enum class ClaimProcessingStage {
  IDLE,
  VALIDATING_DOCUMENTS,
  EXTRACTING_REJECTION_LETTER,
  EXTRACTING_POLICY_DOCUMENTS,
  EXTRACTING_SUPPORTING_DOCUMENTS,
  COMPARING_EVIDENCE,
  COMPLETED,
  ERROR
}

object ClaimReviewConstants {
  const val MAX_POLICY_DOCS_FILES = 3
  const val MAX_REJECTION_LETTER_FILES = 2
  const val MAX_SUPPORTING_DOCS_FILES = 4
  const val MAX_TOTAL_FILES = 6
  const val MAX_PASTED_TEXT_CHARS = 60_000

  fun maxFilesForBucket(bucket: ClaimBucketType): Int = when (bucket) {
    ClaimBucketType.POLICY_DOCS -> MAX_POLICY_DOCS_FILES
    ClaimBucketType.REJECTION_LETTER -> MAX_REJECTION_LETTER_FILES
    ClaimBucketType.SUPPORTING_CLAIM_DOCS -> MAX_SUPPORTING_DOCS_FILES
  }
}

/**
 * Ephemeral in-memory attachment representing an uploaded PDF or page photo.
 * Identified by an app-generated stable [documentId] (never by user-visible filename).
 */
class ClaimDocumentAttachment(
  val documentId: String = UUID.randomUUID().toString(),
  val bucket: ClaimBucketType,
  val role: ClaimDocumentRole,
  val originalFileName: String,
  val declaredMimeType: String?,
  val detectedMimeType: String,
  val byteCount: Long,
  val bytes: ByteArray,
  val pageCount: Int? = null
) {
  val isPdf: Boolean
    get() = detectedMimeType.equals("application/pdf", ignoreCase = true)

  val formattedSize: String
    get() = when {
      byteCount < 1024L -> "$byteCount B"
      byteCount < 1024L * 1024L -> "${(byteCount / 1024.0).let { String.format(java.util.Locale.US, "%.0f", it) }} KB"
      else -> "${(byteCount / (1024.0 * 1024.0)).let { String.format(java.util.Locale.US, "%.1f", it) }} MB"
    }

  fun toMetadata(): ClaimDocumentMetadata = ClaimDocumentMetadata(
    documentId = documentId,
    bucket = bucket,
    role = role,
    originalFileName = originalFileName,
    detectedMimeType = detectedMimeType,
    byteCount = byteCount,
    pageCount = pageCount,
    isPastedText = false
  )

  /** Best-effort zeroing of sensitive bytes in JVM memory on removal or exit. */
  fun wipe() {
    bytes.fill(0)
  }
}

/**
 * Lightweight, ByteArray-free metadata descriptor used for prompt binding and source validation.
 */
data class ClaimDocumentMetadata(
  val documentId: String,
  val bucket: ClaimBucketType,
  val role: ClaimDocumentRole,
  val originalFileName: String,
  val detectedMimeType: String,
  val byteCount: Long,
  val pageCount: Int? = null,
  val isPastedText: Boolean = false
)

/**
 * Source citation pointing back to an authenticated session attachment via [documentId].
 * Quotations always preserve exact source wording and start as [QuoteVerificationStatus.AI_EXTRACTED_UNVERIFIED].
 */
data class ClaimSourceReference(
  val documentId: String,
  val bucket: ClaimBucketType,
  val originalFileName: String,
  val pageNumber: Int? = null,
  val extractedQuote: String,
  val sourceLanguageCode: String? = null,
  val quoteVerificationStatus: QuoteVerificationStatus = QuoteVerificationStatus.AI_EXTRACTED_UNVERIFIED
)

/**
 * Structured factual claim extracted from documents, distinguishing insurer allegations from
 * independently established evidence.
 */
data class ClaimExtractionFact(
  val label: String,
  val value: String,
  val sourceReference: ClaimSourceReference? = null,
  val isInsurerAllegationOnly: Boolean = false
)

data class InsurerStatedReason(
  val allegationSummaryPlainLanguage: String,
  val citedClauseLabel: String? = null,
  val sourceReference: ClaimSourceReference
)

data class InsurerDecisionDetails(
  val decisionType: DecisionType,
  val claimReferenceNumber: String? = null,
  val insurerName: String? = null,
  val tpaName: String? = null,
  val decisionDate: String? = null,
  val hospitalizationDates: String? = null,
  val claimedAmount: String? = null,
  val approvedAmount: String? = null,
  val deductedAmount: String? = null,
  val statedReasons: List<InsurerStatedReason>
)

data class PolicyClauseRecord(
  val clauseTitle: String,
  val clauseIdentifier: String? = null,
  val documentRole: ClaimDocumentRole = ClaimDocumentRole.BASE_POLICY_WORDING,
  val plainLanguageSummary: String,
  val statedAmountOrPeriod: String? = null,
  val sourceReference: ClaimSourceReference
)

data class PolicyEvidenceSummary(
  val policyScheduleFacts: List<ClaimExtractionFact> = emptyList(),
  val basePolicyClauses: List<PolicyClauseRecord> = emptyList(),
  val endorsementsAndRiders: List<PolicyClauseRecord> = emptyList(),
  val waitingPeriods: List<PolicyClauseRecord> = emptyList(),
  val exclusions: List<PolicyClauseRecord> = emptyList(),
  val exceptionsToExclusions: List<PolicyClauseRecord> = emptyList(),
  val coPayment: String? = null,
  val deductible: String? = null,
  val roomRentLimit: String? = null,
  val subLimits: String? = null,
  val claimNotificationConditions: String? = null,
  val hasScheduleOrEndorsementConflict: Boolean = false,
  val scheduleOrEndorsementConflictNote: String? = null
)

data class SupportingClaimEvidenceSummary(
  val claimFormFacts: List<ClaimExtractionFact> = emptyList(),
  val dischargeSummaryFacts: List<ClaimExtractionFact> = emptyList(),
  val hospitalBillFacts: List<ClaimExtractionFact> = emptyList(),
  val medicalRecordFacts: List<ClaimExtractionFact> = emptyList(),
  val investigationReportFacts: List<ClaimExtractionFact> = emptyList(),
  val insurerCorrespondenceFacts: List<ClaimExtractionFact> = emptyList()
)

data class ClaimClauseComparison(
  val insurerCitedClauseOrAllegation: String,
  val insurerSourceReference: ClaimSourceReference?,
  val matchingPolicyClause: PolicyClauseRecord?,
  val plainLanguageComparison: String,
  val hasMissingOrUnverifiedCitation: Boolean
)

data class ClaimEvidenceFinding(
  val description: String,
  val isInsurerAllegationOnly: Boolean = false,
  val sourceReference: ClaimSourceReference? = null
)

data class ClaimComparisonResult(
  val languageCode: String,
  val canonicalLanguageName: String,
  val insurerDecision: InsurerDecisionDetails,
  val policyEvidence: PolicyEvidenceSummary,
  val supportingEvidence: SupportingClaimEvidenceSummary,
  val comparisonOutcome: ComparisonOutcome,
  val outcomeRationale: String,
  val safeSummary: String,
  val clauseComparisons: List<ClaimClauseComparison>,
  val supportingEvidenceItems: List<ClaimEvidenceFinding>,
  val contradictoryEvidenceItems: List<ClaimEvidenceFinding>,
  val missingEvidenceItems: List<String>,
  val questionsToAskInsurer: List<String>,
  val documentsToRequestOrProvide: List<String>,
  val sourceReferences: List<ClaimSourceReference>,
  val hasPolicyDocumentsProvided: Boolean,
  val outcomeDowngradedBySafetyGuard: Boolean = false,
  val disclaimer: String,
  val nonLegalAdviceWarning: String,
  val linguisticFilteringLimitationNote: String
)

data class ClaimReviewUiState(
  val processingStage: ClaimProcessingStage = ClaimProcessingStage.IDLE,
  val selectedLanguageCode: String = SupportedExplanationLanguage.ENGLISH.code,
  val attachments: List<ClaimDocumentAttachment> = emptyList(),
  val pastedRejectionLetterText: String = "",
  val pastedPolicyText: String = "",
  val errorType: ClaimErrorType? = null,
  val errorMessage: String? = null,
  val comparisonResult: ClaimComparisonResult? = null
) {
  val totalAttachmentBytes: Long
    get() = attachments.sumOf { it.byteCount }

  val hasRejectionLetterInput: Boolean
    get() = attachments.any { it.bucket == ClaimBucketType.REJECTION_LETTER } ||
      pastedRejectionLetterText.isNotBlank()

  val hasPolicyInput: Boolean
    get() = attachments.any { it.bucket == ClaimBucketType.POLICY_DOCS } ||
      pastedPolicyText.isNotBlank()
}

data class ClaimReviewStrings(
  val languageCode: String,
  val canonicalLanguageName: String,
  val featureTitle: String,
  val safeSummaryFallback: String,
  val insufficientInformationExplanation: String,
  val documentConflictExplanation: String,
  val possibleMismatchExplanation: String,
  val documentSupportedExplanation: String,
  val unreadableDocumentMessage: String,
  val invalidFileMessage: String,
  val fileTooLargeMessage: String,
  val noInternetMessage: String,
  val timeoutMessage: String,
  val invalidResponseMessage: String,
  val internalErrorMessage: String,
  val notARejectionLetterMessage: String,
  val missingRejectionLetterMessage: String,
  val disclaimer: String,
  val neutralQuestionsGuidance: String,
  val nonLegalAdviceWarning: String,
  val linguisticFilteringLimitationNote: String,
  val notMentionedFallback: String
)

object ClaimReviewCopy {

  fun forLanguage(languageCodeOrName: String?): ClaimReviewStrings {
    val lang = SupportedExplanationLanguage.fromCodeOrName(languageCodeOrName)
      ?: SupportedExplanationLanguage.ENGLISH
    return when (lang) {
      SupportedExplanationLanguage.ENGLISH -> english
      SupportedExplanationLanguage.HINDI -> hindi
      SupportedExplanationLanguage.BENGALI -> bengali
      SupportedExplanationLanguage.MARATHI -> marathi
    }
  }

  private val english = ClaimReviewStrings(
    languageCode = "en",
    canonicalLanguageName = "English",
    featureTitle = "Review My Rejected Health Claim",
    safeSummaryFallback = "This comparison reflects only the readable portions of the uploaded claim and policy documents. Please verify the exact clause wording and final claim status directly with your insurer.",
    insufficientInformationExplanation = "There is not enough verified policy or claim information in the uploaded pages to confirm whether the insurer's stated reason matches all policy terms and endorsements.",
    documentConflictExplanation = "The uploaded documents contain conflicting details—such as differences between the policy schedule, endorsements, base wording, or rejection letter—that require direct clarification from the insurer.",
    possibleMismatchExplanation = "Some details in the insurer's rejection or deduction reason appear to differ from or need clarification against the uploaded policy clauses or supporting records.",
    documentSupportedExplanation = "Based strictly on the uploaded documents, the insurer's stated reason corresponds to a cited clause in the provided policy text; still confirm all endorsements and exceptions with your insurer.",
    unreadableDocumentMessage = "We could not read the uploaded file or text clearly. Please provide a clearer photo, an unencrypted PDF, or readable pasted text.",
    invalidFileMessage = "One or more files could not be accepted. Please upload valid PDF, JPEG, PNG, or WEBP documents matching their file format.",
    fileTooLargeMessage = "The selected files or pasted text exceed the allowed size limit (14 MB total attachments or 60,000 characters). Please upload fewer or smaller pages.",
    noInternetMessage = "No internet connection detected. Please check your connection and try again.",
    timeoutMessage = "Reviewing the claim documents took too long. Please try again.",
    invalidResponseMessage = "We could not validate the structured comparison result safely. Please retry with clearer documents.",
    internalErrorMessage = "Something went wrong while preparing the claim comparison. Please try again.",
    notARejectionLetterMessage = "This does not appear to be an insurance claim rejection, repudiation, or settlement deduction document.",
    missingRejectionLetterMessage = "Please upload or paste the insurer's claim rejection or deduction letter before running a comparison.",
    disclaimer = "This is an AI-generated document comparison for understanding only. It is not legal, insurance or financial advice. The final claim decision and applicable policy terms must be confirmed with the insurer.",
    neutralQuestionsGuidance = "Use these calm, factual questions when requesting written clarification or an internal review from your insurer or TPA.",
    nonLegalAdviceWarning = "NexzyHealth does not provide legal representation, claim merit guarantees, or advice on lawsuits, fraud allegations, or settlements.",
    linguisticFilteringLimitationNote = "Automated safety filtering checks common legal-advice and misleading phrases across English, Hindi, Bengali, and Marathi, but is not exhaustive. Always verify important details with your insurer or a qualified advisor.",
    notMentionedFallback = "Not mentioned in the uploaded documents"
  )

  private val hindi = ClaimReviewStrings(
    languageCode = "hi",
    canonicalLanguageName = "Hindi",
    featureTitle = "अस्वीकृत हेल्थ क्लेम की समीक्षा",
    safeSummaryFallback = "यह तुलना केवल अपलोड किए गए क्लेम और पॉलिसी दस्तावेज़ों के पढ़ने योग्य हिस्से पर आधारित है। कृपया सटीक क्लॉज़ और अंतिम निर्णय की पुष्टि अपनी बीमा कंपनी से करें।",
    insufficientInformationExplanation = "अपलोड किए गए पन्नों में इतनी पूरी जानकारी उपलब्ध नहीं है जिससे यह पुष्टि हो सके कि बीमा कंपनी का कारण सभी पॉलिसी शर्तों और एंडोर्समेंट से मेल खाता है।",
    documentConflictExplanation = "अपलोड किए गए दस्तावेज़ों—जैसे पॉलिसी शेड्यूल, एंडोर्समेंट, मुख्य पॉलिसी शर्तें या अस्वीकृति पत्र—में परस्पर भिन्न विवरण दिखे हैं, जिन पर बीमा कंपनी से स्पष्टीकरण आवश्यक है।",
    possibleMismatchExplanation = "बीमा कंपनी के अस्वीकृति या कटौती कारण के कुछ बिंदु अपलोड किए गए पॉलिसी क्लॉज़ या सहायक रिकॉर्ड से अलग प्रतीत होते हैं और स्पष्टीकरण की आवश्यकता है।",
    documentSupportedExplanation = "केवल अपलोड किए गए दस्तावेज़ों के आधार पर, बीमा कंपनी का बताया गया कारण दिए गए पॉलिसी टेक्स्ट के एक क्लॉज़ से मेल खाता प्रतीत होता है; फिर भी सभी एंडोर्समेंट और अपवाद बीमा कंपनी से जांचें।",
    unreadableDocumentMessage = "अपलोड की गई फ़ाइल या टेक्स्ट साफ़ पढ़ा नहीं जा सका। कृपया साफ़ फ़ोटो, बिना पासवर्ड वाला PDF या पढ़ने योग्य टेक्स्ट दें।",
    invalidFileMessage = "एक या अधिक फ़ाइलें स्वीकार नहीं की जा सकीं। कृपया वैध PDF, JPEG, PNG या WEBP दस्तावेज़ अपलोड करें।",
    fileTooLargeMessage = "चुनी गई फ़ाइलें या पेस्ट किया गया टेक्स्ट तय सीमा (कुल 14 MB या 60,000 अक्षर) से अधिक है। कृपया कम या छोटे पन्ने अपलोड करें।",
    noInternetMessage = "इंटरनेट कनेक्शन उपलब्ध नहीं है। कृपया नेटवर्क जांचें और फिर प्रयास करें।",
    timeoutMessage = "क्लेम दस्तावेज़ों की समीक्षा में बहुत समय लग गया। कृपया दोबारा प्रयास करें।",
    invalidResponseMessage = "समीक्षा परिणाम सुरक्षित रूप से सत्यापित नहीं हो सका। कृपया साफ़ दस्तावेज़ों के साथ पुनः प्रयास करें।",
    internalErrorMessage = "क्लेम तुलना तैयार करते समय तकनीकी समस्या आई। कृपया पुनः प्रयास करें।",
    notARejectionLetterMessage = "यह दस्तावेज़ बीमा क्लेम अस्वीकृति, निरस्तीकरण या आंशिक कटौती पत्र जैसा नहीं लग रहा है।",
    missingRejectionLetterMessage = "तुलना शुरू करने से पहले कृपया बीमा कंपनी का क्लेम अस्वीकृति या कटौती पत्र अपलोड या पेस्ट करें।",
    disclaimer = "यह केवल समझने के लिए AI द्वारा तैयार दस्तावेज़ तुलना है। यह कानूनी, बीमा या वित्तीय सलाह नहीं है। अंतिम क्लेम निर्णय और लागू पॉलिसी शर्तों की पुष्टि बीमा कंपनी से अवश्य करें।",
    neutralQuestionsGuidance = "अपनी बीमा कंपनी या TPA से लिखित स्पष्टीकरण या समीक्षा मांगते समय आप ये तथ्यात्मक और शांत प्रश्न पूछ सकते हैं।",
    nonLegalAdviceWarning = "NexzyHealth कानूनी सलाह, क्लेम पास होने की गारंटी, मुकदमे, धोखाधड़ी के आरोप या सेटलमेंट स्वीकार/अस्वीकार करने की सलाह नहीं देता।",
    linguisticFilteringLimitationNote = "स्वचालित सुरक्षा फ़िल्टर अंग्रेज़ी, हिंदी, बंगाली और मराठी में सामान्य कानूनी-सलाह वाक्यों की जांच करता है, लेकिन यह पूर्ण नहीं है। महत्वपूर्ण विवरण हमेशा बीमा कंपनी या योग्य सलाहकार से जांचें।",
    notMentionedFallback = "अपलोड किए गए दस्तावेज़ों में उल्लेख नहीं है"
  )

  private val bengali = ClaimReviewStrings(
    languageCode = "bn",
    canonicalLanguageName = "Bengali",
    featureTitle = "বাতিল হওয়া হেলথ ক্লেম পর্যালোচনা",
    safeSummaryFallback = "এই তুলনাটি শুধুমাত্র আপলোড করা ক্লেম এবং পলিসি নথির পাঠযোগ্য অংশের উপর ভিত্তি করে তৈরি। অনুগ্রহ করে সঠিক ধারা ও চূড়ান্ত সিদ্ধান্ত সরাসরি আপনার বীমা কোম্পানির সাথে যাচাই করুন।",
    insufficientInformationExplanation = "বীমা কোম্পানির উল্লিখিত কারণটি পলিসির সব শর্ত ও এন্ডোর্সমেন্টের সাথে মেলে কিনা তা নিশ্চিত করার মতো পর্যাপ্ত তথ্য আপলোড করা পাতায় নেই।",
    documentConflictExplanation = "আপলোড করা নথিগুলোর—যেমন পলিসি শিডিউল, এন্ডোর্সমেন্ট, মূল পলিসির শর্ত বা বাতিল চিঠি—মধ্যে পরস্পরবিরোধী তথ্য পাওয়া গেছে, যার জন্য বীমা কোম্পানির ব্যাখ্যা প্রয়োজন।",
    possibleMismatchExplanation = "বীমা কোম্পানির ক্লেম বাতিল বা অর্থ কাটার কারণের কিছু অংশ আপলোড করা পলিসির শর্ত বা সহায়ক নথির সাথে ভিন্ন হতে পারে এবং স্পষ্টীকরণ প্রয়োজন।",
    documentSupportedExplanation = "শুধুমাত্র আপলোড করা নথির ভিত্তিতে, বীমা কোম্পানির উল্লিখিত কারণটি প্রদত্ত পলিসির একটি ধারার সাথে সামঞ্জস্যপূর্ণ মনে হচ্ছে; তবুও সব এন্ডোর্সমেন্ট ও ব্যতিক্রম বীমা কোম্পানির সাথে নিশ্চিত করুন।",
    unreadableDocumentMessage = "আপলোড করা ফাইল বা লেখা স্পষ্ট পড়া যায়নি। অনুগ্রহ করে পরিষ্কার ছবি, পাসওয়ার্ডহীন PDF বা পাঠযোগ্য লেখা দিন।",
    invalidFileMessage = "এক বা একাধিক ফাইল গ্রহণ করা যায়নি। অনুগ্রহ করে ফরম্যাট অনুযায়ী বৈধ PDF, JPEG, PNG বা WEBP নথি আপলোড করুন।",
    fileTooLargeMessage = "নির্বাচিত ফাইল বা পেস্ট করা লেখা নির্ধারিত সীমা (মোট ১৪ MB বা ৬০,০০০ অক্ষর) অতিক্রম করেছে। অনুগ্রহ করে কম বা ছোট পাতা দিন।",
    noInternetMessage = "ইন্টারনেট সংযোগ পাওয়া যায়নি। অনুগ্রহ করে সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।",
    timeoutMessage = "ক্লেম নথি পর্যালোচনা করতে অনেক সময় লেগেছে। অনুগ্রহ করে আবার চেষ্টা করুন।",
    invalidResponseMessage = "তুলনার ফলাফল নিরাপদভাবে যাচাই করা যায়নি। অনুগ্রহ করে পরিষ্কার নথি দিয়ে আবার চেষ্টা করুন।",
    internalErrorMessage = "ক্লেম তুলনা তৈরি করার সময় একটি সমস্যা হয়েছে। অনুগ্রহ করে আবার চেষ্টা করুন।",
    notARejectionLetterMessage = "এটি বীমা ক্লেম বাতিল, প্রত্যাখ্যান বা আংশিক অর্থ কাটার চিঠি বলে মনে হচ্ছে না।",
    missingRejectionLetterMessage = "তুলনা শুরু করার আগে অনুগ্রহ করে বীমা কোম্পানির ক্লেম বাতিল বা অর্থ কাটার চিঠি আপলোড বা পেস্ট করুন।",
    disclaimer = "এটি শুধুমাত্র বোঝার জন্য AI দ্বারা তৈরি নথির তুলনা। এটি কোনো আইনি, বীমা বা আর্থিক পরামর্শ নয়। চূড়ান্ত ক্লেম সিদ্ধান্ত এবং প্রযোজ্য পলিসির শর্ত অবশ্যই বীমা কোম্পানির সাথে নিশ্চিত করতে হবে।",
    neutralQuestionsGuidance = "আপনার বীমা কোম্পানি বা TPA-এর কাছে লিখিত ব্যাখ্যা বা পুনর্বিবেচনার অনুরোধ করার সময় এই নিরপেক্ষ ও তথ্যভিত্তিক প্রশ্নগুলো ব্যবহার করতে পারেন।",
    nonLegalAdviceWarning = "NexzyHealth কোনো আইনি সহায়তা, ক্লেম পাসের নিশ্চয়তা, মামলা, প্রতারণার অভিযোগ বা মীমাংসা গ্রহণ/প্রত্যাখ্যানের পরামর্শ দেয় না।",
    linguisticFilteringLimitationNote = "স্বয়ংক্রিয় নিরাপত্তা ফিল্টার ইংরেজি, হিন্দি, বাংলা ও মারাঠিতে সাধারণ আইনি-পরামর্শমূলক বাক্য পরীক্ষা করে, তবে এটি সম্পূর্ণ নয়। গুরুত্বপূর্ণ তথ্য সবসময় বীমা কোম্পানি বা যোগ্য পরামর্শদাতার সাথে যাচাই করুন।",
    notMentionedFallback = "আপলোড করা নথিতে উল্লেখ নেই"
  )

  private val marathi = ClaimReviewStrings(
    languageCode = "mr",
    canonicalLanguageName = "Marathi",
    featureTitle = "नाकारलेल्या हेल्थ क्लेमचे पुनरावलोकन",
    safeSummaryFallback = "ही तुलना केवळ अपलोड केलेल्या क्लेम आणि पॉलिसी दस्तऐवजांच्या वाचता येणाऱ्या भागावर आधारित आहे. कृपया अचूक कलम आणि अंतिम निर्णयाची खात्री तुमच्या विमा कंपनीकडून करून घ्या.",
    insufficientInformationExplanation = "विमा कंपनीचे कारण सर्व पॉलिसी अटी आणि एंडोर्समेंट्सशी जुळते का याची खात्री करण्यासाठी अपलोड केलेल्या पानांमध्ये पुरेशी माहिती उपलब्ध नाही.",
    documentConflictExplanation = "अपलोड केलेल्या दस्तऐवजांमध्ये—जसे की पॉलिसी वेळापत्रक, एंडोर्समेंट्स, मूळ पॉलिसी अटी किंवा नकार पत्र—परस्परविरोधी तपशील आढळले आहेत, ज्यावर विमा कंपनीकडून स्पष्टीकरण आवश्यक आहे.",
    possibleMismatchExplanation = "विमा कंपनीने दिलेले नकार किंवा कपातीचे कारण अपलोड केलेल्या पॉलिसी कलमांपेक्षा किंवा पूरक नोंदींपेक्षा वेगळे वाटते आणि त्यावर स्पष्टीकरणाची गरज आहे.",
    documentSupportedExplanation = "केवळ अपलोड केलेल्या दस्तऐवजांच्या आधारे, विमा कंपनीने दिलेले कारण पॉलिसी मजकुरातील नमूद कलमाशी सुसंगत दिसते; तरीही सर्व एंडोर्समेंट्स व अपवाद विमा कंपनीकडून तपासा.",
    unreadableDocumentMessage = "अपलोड केलेली फाइल किंवा मजकूर स्पष्ट वाचता आला नाही. कृपया स्पष्ट फोटो, पासवर्ड नसलेली PDF किंवा वाचण्यायोग्य मजकूर द्या.",
    invalidFileMessage = "एक किंवा अधिक फाइल्स स्वीकारता आल्या नाहीत. कृपया स्वरूपाशी जुळणारी वैध PDF, JPEG, PNG किंवा WEBP कागदपत्रे अपलोड करा.",
    fileTooLargeMessage = "निवडलेल्या फाइल्स किंवा पेस्ट केलेला मजकूर मर्यादेपेक्षा (एकूण १४ MB किंवा ६०,००० अक्षरे) जास्त आहे. कृपया कमी किंवा लहान पाने अपलोड करा.",
    noInternetMessage = "इंटरनेट कनेक्शन आढळले नाही. कृपया इंटरनेट तपासून पुन्हा प्रयत्न करा.",
    timeoutMessage = "क्लेम दस्तऐवजांचे पुनरावलोकन करण्यास खूप वेळ लागला. कृपया पुन्हा प्रयत्न करा.",
    invalidResponseMessage = "तुलना निकाल सुरक्षितपणे पडताळता आला नाही. कृपया स्पष्ट कागदपत्रांसह पुन्हा प्रयत्न करा.",
    internalErrorMessage = "क्लेम तुलना तयार करताना तांत्रिक अडचण आली. कृपया पुन्हा प्रयत्न करा.",
    notARejectionLetterMessage = "हे दस्तऐवज विमा क्लेम नकार, नामंजुरी किंवा आंशिक कपातीचे पत्र वाटत नाही.",
    missingRejectionLetterMessage = "तुलना सुरू करण्यापूर्वी कृपया विमा कंपनीचे क्लेम नकार किंवा कपात पत्र अपलोड किंवा पेस्ट करा.",
    disclaimer = "ही केवळ समजून घेण्यासाठी AI ने तयार केलेली दस्तऐवज तुलना आहे. हा कायदेशीर, विमा किंवा आर्थिक सल्ला नाही. अंतिम क्लेम निर्णय आणि लागू पॉलिसी अटी विमा कंपनीकडूनच खात्री करून घ्याव्यात.",
    neutralQuestionsGuidance = "तुमच्या विमा कंपनीकडून किंवा TPA कडून लेखी स्पष्टीकरण किंवा पुनरावलोकन मागताना हे शांत व वस्तुनिष्ठ प्रश्न वापरता येतील.",
    nonLegalAdviceWarning = "NexzyHealth कायदेशीर सल्ला, क्लेम मंजुरीची हमी, खटले, फसवणुकीचे आरोप किंवा तडजोड स्वीकारण्या/नाकारण्याबाबत मार्गदर्शन देत नाही.",
    linguisticFilteringLimitationNote = "स्वयंचलित सुरक्षा फिल्टर इंग्रजी, हिंदी, बंगाली आणि मराठीतील सामान्य कायदेशीर-सल्ला वाक्ये तपासते, परंतु ते परिपूर्ण नाही. महत्त्वाचे तपशील नेहमी विमा कंपनी किंवा पात्र सल्लागाराकडून तपासा.",
    notMentionedFallback = "अपलोड केलेल्या दस्तऐवजांमध्ये उल्लेख नाही"
  )
}
