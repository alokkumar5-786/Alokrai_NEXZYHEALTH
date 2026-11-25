package com.example.model

/**
 * Domain models, UI state, and localized copy for the "Explain My Health Policy" feature.
 *
 * PRIVACY GUARANTEE:
 * All uploaded document bytes, pasted policy text, and AI-generated policy explanations
 * live strictly in transient in-memory state and are never written to disk, preferences,
 * Room, or Firestore.
 */

enum class HealthPolicyStep {
  LANGUAGE_SELECTION,
  UPLOAD_OR_PASTE,
  LOADING,
  RESULT
}

enum class RedFlagSeverity(val wireValue: String) {
  HIGH("high"),
  MEDIUM("medium"),
  LOW("low");

  companion object {
    fun fromString(raw: String?): RedFlagSeverity {
      return when (raw?.trim()?.lowercase()) {
        "high", "critical", "urgent", "severe" -> HIGH
        "medium", "moderate", "warning" -> MEDIUM
        else -> LOW
      }
    }
  }
}

enum class PolicyErrorType {
  NOT_A_HEALTH_POLICY,
  UNREADABLE,
  NO_INTERNET,
  FILE_TOO_LARGE,
  TOO_MANY_FILES,
  TIMEOUT,
  GENERIC
}

data class PolicyBasics(
  val insurerName: String,
  val planName: String,
  val policyType: String,
  val sumInsured: String,
  val premium: String,
  val policyPeriod: String,
  val membersCovered: String
)

data class WaitingPeriodItem(
  val item: String,
  val duration: String,
  val meaningInSimpleWords: String
)

data class MoneyYouPay(
  val coPayment: String,
  val deductible: String,
  val roomRentLimit: String,
  val subLimits: String,
  val nonPayableItemsNote: String
)

data class HowToClaim(
  val cashlessSteps: List<String>,
  val reimbursementSteps: List<String>,
  val documentsNeeded: List<String>,
  val timeLimits: String
)

data class PolicyRedFlag(
  val severity: RedFlagSeverity,
  val title: String,
  val simpleExplanation: String
)

data class PolicyGlossaryTerm(
  val term: String,
  val meaning: String
)

data class HealthPolicyExplanation(
  val languageCode: String,
  val policyBasics: PolicyBasics,
  val simpleSummary: String,
  val whatIsCovered: List<String>,
  val whatIsNotCovered: List<String>,
  val waitingPeriods: List<WaitingPeriodItem>,
  val moneyYouPay: MoneyYouPay,
  val howToClaim: HowToClaim,
  val redFlags: List<PolicyRedFlag>,
  val questionsToAskInsurer: List<String>,
  val plainLanguageGlossary: List<PolicyGlossaryTerm>
)

/**
 * Ephemeral in-memory attachment representing a PDF or page photo.
 */
class PolicyFileAttachment(
  val id: String,
  val fileName: String,
  val mimeType: String,
  val byteCount: Long,
  val bytes: ByteArray
) {
  val isPdf: Boolean
    get() = mimeType.equals("application/pdf", ignoreCase = true)

  val formattedSize: String
    get() = when {
      byteCount < 1024L -> "$byteCount B"
      byteCount < 1024L * 1024L -> "${(byteCount / 1024.0).let { String.format(java.util.Locale.US, "%.0f", it) }} KB"
      else -> "${(byteCount / (1024.0 * 1024.0)).let { String.format(java.util.Locale.US, "%.1f", it) }} MB"
    }

  /** Zeroes out sensitive document bytes in memory immediately on deletion. */
  fun wipe() {
    bytes.fill(0)
  }
}

data class HealthPolicyUiState(
  val currentStep: HealthPolicyStep = HealthPolicyStep.LANGUAGE_SELECTION,
  val selectedLanguageCode: String = SupportedExplanationLanguage.ENGLISH.code,
  val attachments: List<PolicyFileAttachment> = emptyList(),
  val pastedPolicyText: String = "",
  val errorType: PolicyErrorType? = null,
  val errorMessage: String? = null,
  val explanation: HealthPolicyExplanation? = null
) {
  val canAnalyze: Boolean
    get() = attachments.isNotEmpty() || pastedPolicyText.isNotBlank()

  val totalAttachmentBytes: Long
    get() = attachments.sumOf { it.byteCount }
}

data class HealthPolicyStrings(
  val featureTitle: String,
  val homeSubtitle: String,
  val languageScreenSubtitle: String,
  val languageConfirmButton: String,
  val uploadScreenTitle: String,
  val uploadScreenSubtitle: String,
  val privacyNotice: String,
  val uploadSectionHeading: String,
  val uploadFilesButton: String,
  val pickPhotosButton: String,
  val filesCountFormat: String,
  val pasteDividerLabel: String,
  val pasteInputLabel: String,
  val pasteInputPlaceholder: String,
  val explainButtonText: String,
  val readingPolicyTitle: String,
  val readingPolicySubtitle: String,
  val cancelButtonText: String,
  val mandatoryDisclaimer: String,
  val keyFactsTitle: String,
  val insurerLabel: String,
  val planLabel: String,
  val policyTypeLabel: String,
  val sumInsuredLabel: String,
  val premiumLabel: String,
  val policyPeriodLabel: String,
  val membersCoveredLabel: String,
  val redFlagsTitle: String,
  val noRedFlagsMessage: String,
  val severityHigh: String,
  val severityMedium: String,
  val severityLow: String,
  val simpleSummaryTitle: String,
  val coveredTitle: String,
  val notCoveredTitle: String,
  val waitingPeriodsTitle: String,
  val moneyYouPayTitle: String,
  val coPaymentLabel: String,
  val deductibleLabel: String,
  val roomRentLabel: String,
  val subLimitsLabel: String,
  val nonPayableLabel: String,
  val howToClaimTitle: String,
  val cashlessStepsLabel: String,
  val reimbursementStepsLabel: String,
  val documentsNeededLabel: String,
  val timeLimitsLabel: String,
  val questionsTitle: String,
  val glossaryTitle: String,
  val shareSummaryButton: String,
  val deleteNowButton: String,
  val deletedToastMessage: String,
  val errorNotAPolicy: String,
  val errorUnreadable: String,
  val errorNoInternet: String,
  val errorTooLarge: String,
  val errorTooManyFiles: String,
  val errorTimeout: String,
  val errorGeneric: String,
  val notMentionedFallback: String
)

object HealthPolicyCopy {
  const val MAX_POLICY_FILES = 3

  fun forLanguage(languageCodeOrName: String?): HealthPolicyStrings {
    val lang = SupportedExplanationLanguage.fromCodeOrName(languageCodeOrName)
      ?: SupportedExplanationLanguage.ENGLISH
    return when (lang) {
      SupportedExplanationLanguage.ENGLISH -> english
      SupportedExplanationLanguage.HINDI -> hindi
      SupportedExplanationLanguage.BENGALI -> bengali
      SupportedExplanationLanguage.MARATHI -> marathi
    }
  }

  private val english = HealthPolicyStrings(
    featureTitle = "Explain My Health Policy",
    homeSubtitle = "Understand coverage, waiting periods & red flags in simple words",
    languageScreenSubtitle = "Choose the language for your policy explanation",
    languageConfirmButton = "Continue",
    uploadScreenTitle = "Explain My Health Policy",
    uploadScreenSubtitle = "Upload PDF/photos (max 3) or paste text",
    privacyNotice = "Your document is used only to create this explanation and is not stored.",
    uploadSectionHeading = "Add your policy document",
    uploadFilesButton = "Upload PDF or Files",
    pickPhotosButton = "Pick Page Photos",
    filesCountFormat = "%d of 3 files added",
    pasteDividerLabel = "OR PASTE POLICY TEXT",
    pasteInputLabel = "Paste health policy text here",
    pasteInputPlaceholder = "Paste any part of your policy wording, schedule, or terms...",
    explainButtonText = "Explain My Policy",
    readingPolicyTitle = "Reading your policy...",
    readingPolicySubtitle = "Checking coverage, waiting periods, limits, and red flags in simple language.",
    cancelButtonText = "Cancel",
    mandatoryDisclaimer = "This explanation is generated by AI for understanding only. It is not insurance, legal or financial advice. Please read your original policy and confirm important details with your insurer.",
    keyFactsTitle = "Key Facts",
    insurerLabel = "Insurer",
    planLabel = "Plan Name",
    policyTypeLabel = "Policy Type",
    sumInsuredLabel = "Sum Insured",
    premiumLabel = "Premium",
    policyPeriodLabel = "Policy Period",
    membersCoveredLabel = "Members Covered",
    redFlagsTitle = "Red Flags to Check Carefully",
    noRedFlagsMessage = "No major red flags were highlighted in the text provided. Still confirm all limits with your insurer.",
    severityHigh = "High Attention",
    severityMedium = "Medium",
    severityLow = "Low",
    simpleSummaryTitle = "Simple Summary",
    coveredTitle = "What Is Covered",
    notCoveredTitle = "What Is Not Covered",
    waitingPeriodsTitle = "Waiting Periods",
    moneyYouPayTitle = "Money You May Pay From Your Pocket",
    coPaymentLabel = "Co-payment",
    deductibleLabel = "Deductible",
    roomRentLabel = "Room Rent Limit",
    subLimitsLabel = "Sub-limits",
    nonPayableLabel = "Non-payable Items",
    howToClaimTitle = "How to Claim",
    cashlessStepsLabel = "Cashless Claim Steps",
    reimbursementStepsLabel = "Reimbursement Claim Steps",
    documentsNeededLabel = "Documents Needed",
    timeLimitsLabel = "Claim Time Limits",
    questionsTitle = "Questions to Ask Your Insurer",
    glossaryTitle = "Plain-Language Glossary",
    shareSummaryButton = "Share summary",
    deleteNowButton = "Delete my document now",
    deletedToastMessage = "Your document and explanation were permanently cleared.",
    errorNotAPolicy = "This does not look like a health insurance policy document. Please upload a health policy PDF, page photo, or paste policy text.",
    errorUnreadable = "We could not read the text clearly from this file. Please upload clearer photos, an unencrypted PDF, or paste the policy text.",
    errorNoInternet = "No internet connection detected. Please connect to the internet and try again.",
    errorTooLarge = "The selected files are too large (limit 14 MB total). Please upload up to 3 smaller pages or paste the key policy text.",
    errorTooManyFiles = "You can upload up to 3 files at a time.",
    errorTimeout = "Reading the policy took too long. Please check your internet connection and try again.",
    errorGeneric = "We could not explain this policy right now. Please try again with clearer pages or pasted text.",
    notMentionedFallback = "Not mentioned in the document"
  )

  private val hindi = HealthPolicyStrings(
    featureTitle = "Explain My Health Policy",
    homeSubtitle = "पॉलिसी के नियम, वेटिंग पीरियड और जरूरी बातें सरल भाषा में समझें",
    languageScreenSubtitle = "अपनी पॉलिसी समझाने के लिए भाषा चुनें",
    languageConfirmButton = "आगे बढ़ें",
    uploadScreenTitle = "मेरी हेल्थ पॉलिसी समझें",
    uploadScreenSubtitle = "PDF/फ़ोटो (अधिकतम 3) अपलोड करें या टेक्स्ट पेस्ट करें",
    privacyNotice = "आपका दस्तावेज़ केवल यह विवरण तैयार करने के लिए उपयोग किया जाता है और इसे कहीं भी सहेजा नहीं जाता है।",
    uploadSectionHeading = "अपनी पॉलिसी का दस्तावेज़ जोड़ें",
    uploadFilesButton = "PDF या फ़ाइल अपलोड करें",
    pickPhotosButton = "पन्नों की फ़ोटो चुनें",
    filesCountFormat = "3 में से %d फ़ाइलें जोड़ी गईं",
    pasteDividerLabel = "या पॉलिसी का टेक्स्ट पेस्ट करें",
    pasteInputLabel = "यहाँ पॉलिसी का टेक्स्ट पेस्ट करें",
    pasteInputPlaceholder = "अपनी पॉलिसी के नियम, शेड्यूल या शर्तें यहाँ पेस्ट करें...",
    explainButtonText = "पॉलिसी समझाएं",
    readingPolicyTitle = "आपकी पॉलिसी पढ़ी जा रही है...",
    readingPolicySubtitle = "कवरेज, वेटिंग पीरियड, सीमाएं और जरूरी चेतावनियां सरल भाषा में देखी जा रही हैं।",
    cancelButtonText = "रद्द करें",
    mandatoryDisclaimer = "यह विवरण केवल समझने के लिए AI द्वारा तैयार किया गया है। यह बीमा, कानूनी या वित्तीय सलाह नहीं है। कृपया अपनी मूल पॉलिसी पढ़ें और महत्वपूर्ण जानकारी की पुष्टि अपनी बीमा कंपनी से करें।",
    keyFactsTitle = "मुख्य बातें (Key Facts)",
    insurerLabel = "बीमा कंपनी",
    planLabel = "प्लान का नाम",
    policyTypeLabel = "पॉलिसी प्रकार",
    sumInsuredLabel = "बीमा राशि (Sum Insured)",
    premiumLabel = "प्रीमियम",
    policyPeriodLabel = "पॉलिसी अवधि",
    membersCoveredLabel = "शामिल सदस्य",
    redFlagsTitle = "ध्यान देने योग्य जरूरी बातें (Red Flags)",
    noRedFlagsMessage = "दिए गए दस्तावेज़ में कोई बड़ी चेतावनी नहीं मिली। फिर भी सभी नियम अपनी बीमा कंपनी से जांचें।",
    severityHigh = "उच्च ध्यान",
    severityMedium = "मध्यम",
    severityLow = "कम",
    simpleSummaryTitle = "सरल सारांश",
    coveredTitle = "क्या कवर है (शामिल लाभ)",
    notCoveredTitle = "क्या कवर नहीं है (बहिष्करण)",
    waitingPeriodsTitle = "प्रतीक्षा अवधि (Waiting Periods)",
    moneyYouPayTitle = "आपको अपनी जेब से कितना देना पड़ सकता है",
    coPaymentLabel = "को-पेमेंट (हिस्सेदारी)",
    deductibleLabel = "डिडक्टिबल (कटौती)",
    roomRentLabel = "कमरे का किराया सीमा",
    subLimitsLabel = "सब-लिमिट (इलाज की सीमा)",
    nonPayableLabel = "गैर-भुगतान योग्य खर्च",
    howToClaimTitle = "क्लेम कैसे करें",
    cashlessStepsLabel = "कैशलेस क्लेम के चरण",
    reimbursementStepsLabel = "रीइम्बर्समेंट क्लेम के चरण",
    documentsNeededLabel = "जरूरी दस्तावेज़",
    timeLimitsLabel = "क्लेम की समय सीमा",
    questionsTitle = "बीमा कंपनी से पूछने के लिए सवाल",
    glossaryTitle = "सरल शब्दकोश",
    shareSummaryButton = "सारांश साझा करें",
    deleteNowButton = "मेरा दस्तावेज़ अभी हटाएं",
    deletedToastMessage = "आपका दस्तावेज़ और विवरण पूरी तरह हटा दिया गया है।",
    errorNotAPolicy = "यह स्वास्थ्य बीमा पॉलिसी जैसा नहीं लग रहा है। कृपया हेल्थ पॉलिसी PDF, फ़ोटो अपलोड करें या टेक्स्ट पेस्ट करें।",
    errorUnreadable = "इस फ़ाइल से टेक्स्ट साफ़ पढ़ा नहीं जा सका। कृपया साफ़ फ़ोटो, बिना पासवर्ड वाला PDF अपलोड करें या टेक्स्ट पेस्ट करें।",
    errorNoInternet = "इंटरनेट कनेक्शन नहीं है। कृपया इंटरनेट चालू करें और फिर प्रयास करें।",
    errorTooLarge = "चुनी गई फ़ाइलें बहुत बड़ी हैं (अधिकतम 14 MB)। कृपया 3 छोटे पन्ने अपलोड करें या मुख्य टेक्स्ट पेस्ट करें।",
    errorTooManyFiles = "आप एक बार में अधिकतम 3 फ़ाइलें ही जोड़ सकते हैं।",
    errorTimeout = "पॉलिसी पढ़ने में बहुत समय लग गया। कृपया इंटरनेट जांचें और फिर कोशिश करें।",
    errorGeneric = "अभी इस पॉलिसी को समझाया नहीं जा सका। कृपया साफ़ पन्नों या टेक्स्ट के साथ फिर प्रयास करें।",
    notMentionedFallback = "दस्तावेज़ में उल्लेख नहीं है"
  )

  private val bengali = HealthPolicyStrings(
    featureTitle = "Explain My Health Policy",
    homeSubtitle = "কভারেজ, অপেক্ষার সময় এবং সতর্কতা সহজ ভাষায় বুঝুন",
    languageScreenSubtitle = "আপনার পলিসি বোঝার জন্য ভাষা বেছে নিন",
    languageConfirmButton = "এগিয়ে যান",
    uploadScreenTitle = "আমার হেলথ পলিসি বুঝুন",
    uploadScreenSubtitle = "PDF/ছবি (সর্বোচ্চ ৩টি) আপলোড করুন বা লেখা পেস্ট করুন",
    privacyNotice = "আপনার নথিটি শুধুমাত্র এই ব্যাখ্যা তৈরি করার জন্য ব্যবহার করা হয় এবং কোথাও সংরক্ষণ করা হয় না।",
    uploadSectionHeading = "আপনার পলিসির নথি যোগ করুন",
    uploadFilesButton = "PDF বা ফাইল আপলোড",
    pickPhotosButton = "পাতার ছবি বেছে নিন",
    filesCountFormat = "৩টির মধ্যে %dটি ফাইল যোগ করা হয়েছে",
    pasteDividerLabel = "অথবা পলিসির লেখা পেস্ট করুন",
    pasteInputLabel = "এখানে পলিসির লেখা পেস্ট করুন",
    pasteInputPlaceholder = "আপনার পলিসির শর্তাবলী বা শিডিউলের লেখা এখানে পেস্ট করুন...",
    explainButtonText = "পলিসি ব্যাখ্যা করুন",
    readingPolicyTitle = "আপনার পলিসি পড়া হচ্ছে...",
    readingPolicySubtitle = "কভারেজ, অপেক্ষার সময়, সীমা এবং জরুরি সতর্কতা সহজ ভাষায় দেখা হচ্ছে।",
    cancelButtonText = "বাতিল করুন",
    mandatoryDisclaimer = "এই ব্যাখ্যাটি শুধুমাত্র বোঝার জন্য AI দ্বারা তৈরি করা হয়েছে। এটি কোনো বীমা, আইনি বা আর্থিক পরামর্শ নয়। অনুগ্রহ করে আপনার মূল পলিসিটি পড়ুন এবং গুরুত্বপূর্ণ তথ্য আপনার বীমা কোম্পানির সাথে যাচাই করুন।",
    keyFactsTitle = "মূল তথ্য (Key Facts)",
    insurerLabel = "বীমা কোম্পানি",
    planLabel = "প্ল্যানের নাম",
    policyTypeLabel = "পলিসির ধরন",
    sumInsuredLabel = "বীমার পরিমাণ (Sum Insured)",
    premiumLabel = "প্রিমিয়াম",
    policyPeriodLabel = "পলিসির মেয়াদ",
    membersCoveredLabel = "আওতাভুক্ত সদস্য",
    redFlagsTitle = "গুরুত্বপূর্ণ সতর্কতা (Red Flags)",
    noRedFlagsMessage = "প্রদত্ত লেখায় বড় কোনো সতর্কতা পাওয়া যায়নি। তবুও বীমা কোম্পানির সাথে সব শর্ত যাচাই করুন।",
    severityHigh = "বেশি সতর্কতা",
    severityMedium = "মাঝারি",
    severityLow = "কম",
    simpleSummaryTitle = "সহজ সারসংক্ষেপ",
    coveredTitle = "যা কভার করা হয়েছে",
    notCoveredTitle = "যা কভার করা হয়নি",
    waitingPeriodsTitle = "অপেক্ষার সময় (Waiting Periods)",
    moneyYouPayTitle = "নিজের পকেট থেকে যা দিতে হতে পারে",
    coPaymentLabel = "কো-পেমেন্ট",
    deductibleLabel = "ডিডাক্টিবল",
    roomRentLabel = "রুম ভাড়ার সীমা",
    subLimitsLabel = "সাব-লিমিট",
    nonPayableLabel = "অ-পরিশোধযোগ্য খরচ",
    howToClaimTitle = "ক্লেম করার নিয়ম",
    cashlessStepsLabel = "ক্যাশলেস ক্লেমের ধাপ",
    reimbursementStepsLabel = "রিইম্বার্সমেন্ট ক্লেমের ধাপ",
    documentsNeededLabel = "প্রয়োজনীয় নথিপত্র",
    timeLimitsLabel = "ক্লেমের সময়সীমা",
    questionsTitle = "বীমা কোম্পানিকে জিজ্ঞাসা করার প্রশ্ন",
    glossaryTitle = "সহজ শব্দকোষ",
    shareSummaryButton = "সারাংশ শেয়ার করুন",
    deleteNowButton = "এখনই আমার নথি মুছুন",
    deletedToastMessage = "আপনার নথি এবং ব্যাখ্যা স্থায়ীভাবে মুছে ফেলা হয়েছে।",
    errorNotAPolicy = "এটি স্বাস্থ্য বীমা পলিসি বলে মনে হচ্ছে না। অনুগ্রহ করে হেলথ পলিসির PDF, ছবি আপলোড করুন বা লেখা পেস্ট করুন।",
    errorUnreadable = "এই ফাইল থেকে লেখা স্পষ্ট পড়া যায়নি। অনুগ্রহ করে পরিষ্কার ছবি, পাসওয়ার্ডহীন PDF আপলোড করুন বা লেখা পেস্ট করুন।",
    errorNoInternet = "ইন্টারনেট সংযোগ নেই। অনুগ্রহ করে ইন্টারনেট চালু করে আবার চেষ্টা করুন।",
    errorTooLarge = "নির্বাচিত ফাইলগুলো অনেক বড় (সর্বোচ্চ ১৪ MB)। অনুগ্রহ করে ৩টি ছোট পাতা দিন বা মূল লেখা পেস্ট করুন।",
    errorTooManyFiles = "একসাথে সর্বোচ্চ ৩টি ফাইল যোগ করা যাবে।",
    errorTimeout = "পলিসি পড়তে অনেক সময় লেগেছে। অনুগ্রহ করে ইন্টারনেট পরীক্ষা করে আবার চেষ্টা করুন।",
    errorGeneric = "এই মুহূর্তে পলিসিটি ব্যাখ্যা করা যায়নি। অনুগ্রহ করে পরিষ্কার ছবি বা লেখা দিয়ে আবার চেষ্টা করুন।",
    notMentionedFallback = "নথিতে উল্লেখ নেই"
  )

  private val marathi = HealthPolicyStrings(
    featureTitle = "Explain My Health Policy",
    homeSubtitle = "कव्हरेज, प्रतीक्षा कालावधी आणि महत्त्वाचे धोके सोप्या भाषेत समजून घ्या",
    languageScreenSubtitle = "पॉलिसी समजून घेण्यासाठी भाषा निवडा",
    languageConfirmButton = "पुढे जा",
    uploadScreenTitle = "माझी हेल्थ पॉलिसी समजावून घ्या",
    uploadScreenSubtitle = "PDF/फोटो (कमाल ३) अपलोड करा किंवा मजकूर पेस्ट करा",
    privacyNotice = "तुमचे दस्तऐवज केवळ हे स्पष्टीकरण तयार करण्यासाठी वापरले जाते आणि ते कुठेही साठवले जात नाही.",
    uploadSectionHeading = "तुमच्या पॉलिसीचे दस्तऐवज जोडा",
    uploadFilesButton = "PDF किंवा फाइल अपलोड",
    pickPhotosButton = "पानांचे फोटो निवडा",
    filesCountFormat = "३ पैकी %d फाइल्स जोडल्या",
    pasteDividerLabel = "किंवा पॉलिसीचा मजकूर पेस्ट करा",
    pasteInputLabel = "येथे पॉलिसीचा मजकूर पेस्ट करा",
    pasteInputPlaceholder = "तुमच्या पॉलिसीचे नियम, वेळापत्रक किंवा अटी येथे पेस्ट करा...",
    explainButtonText = "पॉलिसी समजावून सांगा",
    readingPolicyTitle = "तुमची पॉलिसी वाचली जात आहे...",
    readingPolicySubtitle = "कव्हरेज, प्रतीक्षा कालावधी, मर्यादा आणि महत्त्वाचे धोके सोप्या भाषेत तपासत आहोत.",
    cancelButtonText = "रद्द करा",
    mandatoryDisclaimer = "हे स्पष्टीकरण केवळ समजून घेण्यासाठी AI ने तयार केले आहे. हा विमा, कायदेशीर किंवा आर्थिक सल्ला नाही. कृपया तुमची मूळ पॉलिसी वाचा आणि महत्त्वाच्या तपशिलांची खात्री तुमच्या विमा कंपनीकडून करून घ्या.",
    keyFactsTitle = "मुख्य माहिती (Key Facts)",
    insurerLabel = "विमा कंपनी",
    planLabel = "प्लॅनचे नाव",
    policyTypeLabel = "पॉलिसी प्रकार",
    sumInsuredLabel = "विमा रक्कम (Sum Insured)",
    premiumLabel = "प्रीमियम",
    policyPeriodLabel = "पॉलिसी कालावधी",
    membersCoveredLabel = "समाविष्ट सदस्य",
    redFlagsTitle = "लक्ष देण्यासारखे महत्त्वाचे धोके (Red Flags)",
    noRedFlagsMessage = "दिलेल्या मजकुरात मोठे धोके आढळले नाहीत. तरीही सर्व अटी विमा कंपनीकडून तपासून घ्या.",
    severityHigh = "जास्त लक्ष द्या",
    severityMedium = "मध्यम",
    severityLow = "कमी",
    simpleSummaryTitle = "सोपा सारांश",
    coveredTitle = "काय कव्हर आहे (समाविष्ट लाभ)",
    notCoveredTitle = "काय कव्हर नाही (वगळलेल्या गोष्टी)",
    waitingPeriodsTitle = "प्रतीक्षा कालावधी (Waiting Periods)",
    moneyYouPayTitle = "तुम्हाला स्वतःच्या खिशातून द्यावे लागणारे पैसे",
    coPaymentLabel = "को-पेमेंट",
    deductibleLabel = "डिडक्टिबल",
    roomRentLabel = "खोली भाडे मर्यादा",
    subLimitsLabel = "सब-लिमिट्स",
    nonPayableLabel = "न मिळणारा खर्च",
    howToClaimTitle = "क्लेम कसा करावा",
    cashlessStepsLabel = "कॅशलेस क्लेम टप्पे",
    reimbursementStepsLabel = "रिइम्बर्समेंट क्लेम टप्पे",
    documentsNeededLabel = "लागणारी कागदपत्रे",
    timeLimitsLabel = "क्लेमची मुदत",
    questionsTitle = "विमा कंपनीला विचारण्याचे प्रश्न",
    glossaryTitle = "सोपा शब्दकोश",
    shareSummaryButton = "सारांश शेअर करा",
    deleteNowButton = "माझे दस्तऐवज आत्ताच हटवा",
    deletedToastMessage = "तुमचे दस्तऐवज आणि स्पष्टीकरण कायमचे काढून टाकले आहे.",
    errorNotAPolicy = "हे आरोग्य विमा पॉलिसी दस्तऐवज वाटत नाही. कृपया हेल्थ पॉलिसी PDF, फोटो अपलोड करा किंवा मजकूर पेस्ट करा.",
    errorUnreadable = "या फाइलमधील मजकूर स्पष्ट वाचता आला नाही. कृपया स्पष्ट फोटो, पासवर्ड नसलेली PDF अपलोड करा किंवा मजकूर पेस्ट करा.",
    errorNoInternet = "इंटरनेट कनेक्शन नाही. कृपया इंटरनेट चालू करून पुन्हा प्रयत्न करा.",
    errorTooLarge = "निवडलेल्या फाइल्स खूप मोठ्या आहेत (कमाल १४ MB). कृपया कमाल ३ लहान पाने अपलोड करा किंवा मुख्य मजकूर पेस्ट करा.",
    errorTooManyFiles = "तुम्ही एका वेळी जास्तीत जास्त ३ फाइल्स जोडू शकता.",
    errorTimeout = "पॉलिसी वाचायला खूप वेळ लागला. कृपया इंटरनेट तपासा आणि पुन्हा प्रयत्न करा.",
    errorGeneric = "सध्या ही पॉलिसी समजावून सांगता आली नाही. कृपया स्पष्ट पाने किंवा मजकूर देऊन पुन्हा प्रयत्न करा.",
    notMentionedFallback = "दस्तऐवजात उल्लेख नाही"
  )
}
