package com.example.domain.engine

import com.example.model.ExplanationConstants
import com.example.model.SupportedExplanationLanguage
import java.text.Normalizer

/**
 * Pure Kotlin registry of localized medical-safety resources, abbreviation expansion patterns,
 * protective-warning exemptions, unsafe-advice filters, and fallback/disclaimer copy for the
 * four approved launch languages (English, Hindi, Bengali, Marathi).
 *
 * Design invariants:
 * - Zero Android Context dependency so safety validation runs deterministically on the JVM.
 * - Normalizes text to Unicode NFC before matching so composed and decomposed Devanagari /
 *   Bengali sequences behave identically.
 * - Uses Unicode letter/number/combining-mark boundaries (`\p{L}\p{N}\p{M}`) rather than ASCII `\b`
 *   so Indic matras, halant, and anusvara never split words falsely.
 * - Bounds protective-warning exemptions with `.!?;\\n\\u0964\\u0965` so exemptions never conceal
 *   a separate unsafe instruction across ASCII or Indic danda (`।` / `॥`) sentence boundaries.
 * - Preserves [ExplanationConstants.MEDICAL_EXPLANATION_DISCLAIMER],
 *   [ExplanationConstants.MEDICATION_SAFETY_NOTE], and existing Task 3D English strings verbatim.
 */
object LocalizedSafetyResources {

  /** Unicode word-character class covering letters, digits, and combining marks (matras/halant/nukta). */
  const val UNICODE_WORD_CLASS = "\\p{L}\\p{N}\\p{M}"
  const val B_START = "(?<![$UNICODE_WORD_CLASS])"
  const val B_END = "(?![$UNICODE_WORD_CLASS])"

  /** Sentence terminators across ASCII and Devanagari/Bengali danda (`।`) and double danda (`॥`). */
  private const val SENTENCE_DELIMITERS = ".!?;\\n\\u0964\\u0965"
  private const val SAME_SENTENCE = "[^$SENTENCE_DELIMITERS]*"
  private const val SAME_SENTENCE_LAZY = "[^$SENTENCE_DELIMITERS]*?"

  enum class AbbreviationRuleId {
    OD,
    QD,
    BD_BID,
    TID_TDS,
    QID,
    PRN_SOS,
    HS,
    QOD,
    AC,
    PC,
    OS_OU
  }

  data class AbbreviationRuleSpec(
    val id: AbbreviationRuleId,
    val codeRegex: Regex,
    val englishExpansionRegex: Regex,
    val localizedExpansions: Map<SupportedExplanationLanguage, Regex>
  ) {
    fun expansionRegexesFor(language: SupportedExplanationLanguage): List<Regex> =
      if (language == SupportedExplanationLanguage.ENGLISH) {
        listOf(englishExpansionRegex)
      } else {
        listOfNotNull(englishExpansionRegex, localizedExpansions[language])
      }
  }

  data class SafetyStringsPack(
    val language: SupportedExplanationLanguage,
    val safeFallbackSummary: String,
    val uncertainStatusNote: String,
    val notMentionedStatusNote: String,
    val cardiologyConclusionNotReported: String,
    val specialisedConclusionNotReported: String,
    val medicalExplanationDisclaimer: String,
    val medicationSafetyNote: String,
    val cardiologyReportLabel: String,
    val specialisedReportLabel: String,
    val itemPrefix: String,
    val findingPrefix: String,
    val impressionPrefix: String,
    val cardiologyMetricLabels: Map<String, String>,
    val specialisedSectionLabels: Map<String, String>
  ) {
    fun itemLabel(oneBasedIndex: Int): String = "$itemPrefix $oneBasedIndex"
    fun findingLabel(oneBasedIndex: Int): String = "$findingPrefix $oneBasedIndex"
    fun impressionLabel(oneBasedIndex: Int): String = "$impressionPrefix $oneBasedIndex"
    fun cardiologyMetricLabel(key: String, defaultLabel: String): String =
      cardiologyMetricLabels[key] ?: defaultLabel
    fun specialisedSectionLabel(key: String, defaultLabel: String): String =
      specialisedSectionLabels[key] ?: defaultLabel
  }

  data class LanguageSafetyRegexPack(
    val protectiveWarningRegexes: List<Regex>,
    val unsafeAdviceRegexes: List<Regex>,
    val medicationReferenceRegex: Regex,
    val clauseSplitRegex: Regex
  )

  fun normalizeForMatching(text: String): String =
    if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) {
      text
    } else {
      Normalizer.normalize(text, Normalizer.Form.NFC)
    }

  fun resolveLanguage(language: String?): SupportedExplanationLanguage =
    SupportedExplanationLanguage.fromCodeOrName(language) ?: SupportedExplanationLanguage.ENGLISH

  private fun uRegex(pattern: String): Regex =
    Regex("$B_START(?:${normalizeForMatching(pattern)})$B_END", RegexOption.IGNORE_CASE)

  // ==========================================================================
  // ENGLISH BASELINE SAFETY REGEXES (Task 3D Contracts Preserved Exact)
  // ==========================================================================

  private val EN_PROTECTIVE_MEDICATION_WARNING_REGEX = Regex(
    "\\b(?:do\\s+not|don't|never|should\\s+not|must\\s+not)\\s+(?:ever\\s+)?" +
      "(?:stop(?:\\s+taking|\\s+using)?|discontinue|halt(?:\\s+taking|\\s+using)?|cease|quit|pause|hold|skip|omit|" +
      "change|alter|adjust|modify|increase|decrease|reduce|double|taper(?:\\s+off)?|start|restart)" +
      "$SAME_SENTENCE_LAZY\\b(?:without\\s+(?:first\\s+)?(?:asking|consulting|speaking\\s+(?:to|with)|talking\\s+(?:to|with)|checking\\s+with)\\s+(?:a\\s+|your\\s+)?(?:doctor|physician|prescriber|pharmacist|clinician|healthcare\\s+provider)|" +
      "unless\\s+(?:told|directed|advised|instructed)\\s+by\\s+(?:a\\s+|your\\s+)?(?:doctor|physician|prescriber|pharmacist|clinician|healthcare\\s+provider))",
    RegexOption.IGNORE_CASE
  )

  private val EN_CONSULT_BEFORE_CHANGE_WARNING_REGEX = Regex(
    "\\b(?:always\\s+)?(?:ask|consult|check\\s+with|speak\\s+(?:to|with)|talk\\s+(?:to|with))\\s+" +
      "(?:a\\s+|your\\s+)?(?:doctor|physician|prescriber|pharmacist|clinician|healthcare\\s+provider)\\s+" +
      "before\\s+(?:stopping|discontinuing|halting|changing|altering|adjusting|increasing|decreasing|reducing|tapering|starting|omitting|skipping)\\b$SAME_SENTENCE",
    RegexOption.IGNORE_CASE
  )

  private val EN_UNSAFE_MEDICATION_ADVICE_REGEX = Regex(
    "\\b(" +
      // Stopping / halting / pausing / omitting / starting directives
      "stop\\s+taking|stop\\s+using|stop\\s+this\\s+(?:med|medicine|medication|drug|pill|tablet|capsule)|" +
      "discontinue(?:\\s+(?:taking|using|this))?|halt\\s+(?:taking|using|this)|cease\\s+(?:taking|using)|quit\\s+(?:taking|using)|" +
      "pause\\s+(?:taking|using|this)|omit\\s+(?:a|an|one|the|your)\\s+(?:dose|pill|tablet|capsule)|" +
      "skip\\s+(?:a|an|one|the|your)\\s+(?:dose|pill|tablet|capsule)|start\\s+taking|begin\\s+taking|" +
      // Tapering / changing / adjusting / doubling / pill-count modifications
      "taper\\s*(?:off|down)?|wean\\s*(?:off|yourself)|" +
      "increase\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|amount)|" +
      "decrease\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|amount)|" +
      "reduce\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|amount)|" +
      "lower\\s+(?:the\\s+|your\\s+)?(?:dose|dosage)|raise\\s+(?:the\\s+|your\\s+)?(?:dose|dosage)|" +
      "double\\s+(?:the\\s+|your\\s+|up\\s+(?:on\\s+)?(?:the\\s+|your\\s+)?)?(?:dose|dosage|pills?|tablets?|capsules?)?|" +
      "halve\\s+(?:the\\s+|your\\s+)?(?:dose|pill|tablet)|cut\\s+(?:the\\s+|your\\s+)?(?:dose|pill|tablet)\\s+in\\s+half|" +
      "split\\s+(?:the\\s+|your\\s+)?(?:dose|pill|tablet)|" +
      "take\\s+(?:two|2|three|3|four|4|extra|double|half|additional|more|fewer)\\s+(?:pills?|tablets?|capsules?|doses?)|" +
      "take\\s+instead|switch\\s+(?:this\\s+|your\\s+(?:medicine|medication|drug)\\s+)?to\\b|" +
      "change\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|schedule)|" +
      "adjust\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|schedule)|" +
      "modify\\s+(?:the\\s+|your\\s+)?(?:dose|dosage|frequency|duration|schedule)|" +
      // Prescribing recommendations
      "you\\s+should\\s+(?:take|start|stop|switch|increase|decrease|adjust|taper)|" +
      "we\\s+recommend\\s+(?:taking|starting|stopping|changing)|" +
      "i\\s+recommend\\s+(?:taking|starting|stopping|changing)|" +
      "consider\\s+(?:taking|starting|stopping|switching)|" +
      "you\\s+need\\s+to\\s+(?:take|start|stop|increase|decrease)|" +
      // Unsupported medication purpose / indication (condition-agnostic)
      "indicated\\s+(?:for|to\\s+treat|in\\s+the\\s+treatment\\s+of)|" +
      "prescribed\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent|ease|address|help)|because)|" +
      "used\\s+(?:to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent|ease|address|help)|for\\s+(?!your\\s+(?:review|record|records|reference|visit|appointment|clarity|information)|measuring|checking|recording|evaluating|assessing|examining|imaging|viewing|detecting|screening|identifying|observing|monitoring|displaying|tracking|logging|informational|diagnostic|reference|\\d)[a-z]+)|" +
      "meant\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent))|" +
      "intended\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent))|" +
      "given\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent))|" +
      "taken\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent))|" +
      "(?:to|for)\\s+(?:treat|treating|cure|curing|relieve|relieving|alleviate|alleviating)\\s+[a-z]+|" +
      "(?:treats|cures|relieves|alleviates)\\s+[a-z]+|" +
      "(?:helps|designed|formulated|works)\\s+(?:to\\s+)?(?:treat|manage|control|cure|relieve|lower|reduce|prevent)\\s+[a-z]+|" +
      "(?:this\\s+)?(?:medication|medicine|drug|tablet|capsule|pill)\\s+(?:that|which)\\s+(?:treats|manages|controls|lowers|reduces|prevents|relieves|cures)|" +
      "(?:this\\s+)?(?:medication|medicine|drug|tablet|capsule|pill)\\s+(?:is|are)\\s+(?:primarily\\s+)?for\\s+(?!\\d|oral|external|topical|informational|your\\s+(?:review|record|records|reference|visit|appointment|clarity|information))[a-z]+|" +
      "(?:take|taking)\\s+(?:this\\s+|the\\s+|your\\s+)?(?:medication|medicine|drug|pill|tablet|capsule|it)\\s+(?:for|to\\s+(?:treat|manage|control|cure|relieve|lower|reduce|prevent))\\s+(?!\\d|a\\s+(?:day|week|month|year)|one\\s+|two\\s+|three\\s+|four\\s+|five\\s+|six\\s+|seven\\s+|breakfast|lunch|dinner|meals?|bedtime|your\\s+(?:next|doctor|review|records?))[a-z]+" +
      ")\\b",
    RegexOption.IGNORE_CASE
  )

  private val EN_MEDICATION_REFERENCE_REGEX = Regex(
    "\\b(meds?|medicine|medicines|medication|medications|drug|drugs|prescription|prescriptions|prescribed|pill|pills|tablet|tablets|capsule|capsules|dose|doses|dosing|dosage|regimen|drop|drops|eye\\s+drops?|inhaler|syrup|ointment)\\b",
    RegexOption.IGNORE_CASE
  )

  private val EN_CLAUSE_SPLIT_REGEX = Regex(
    "[.!?;,\\n\\u0964\\u0965]|\\band\\b",
    RegexOption.IGNORE_CASE
  )

  // ==========================================================================
  // HINDI (hi) SAFETY REGEXES (Devanagari + Bilingual Code-Switching)
  // ==========================================================================

  private const val HI_CLINICIAN =
    "(?:डॉक्टर(?:ों)?|चिकित्सक|फार्मासिस्ट|फ़ार्मासिस्ट|प्रिस्क्राइबर|स्वास्थ्य\\s+प्रदाता)"

  private const val HI_MED_NOUN =
    "(?:दवा(?:ई|इयां|इयाँ|इयों)?|औषधि|गोली|गोलियां|गोलियाँ|गोलियों|कैप्सूल|टैबलेट|सिरप|dose|dosage|tablet|capsule|pill|medicine|medication)"

  private const val HI_DOSE_OR_SCHED =
    "(?:खुराक|मात्रा|डोज|डोज़|अवधि|समय|शेड्यूल|dose|dosage|frequency|duration|schedule)"

  private val HI_PROTECTIVE_REGEXES = listOf(
    // E.g. "डॉक्टर से पूछे बिना यह दवा बंद न करें" / "अपने डॉक्टर की सलाह के बिना खुराक न बदलें"
    uRegex(
      "(?:अपने\\s+)?$HI_CLINICIAN\\s*" +
        "(?:से\\s+पूछे\\s+(?:बिना|बगैर)|की\\s+सलाह\\s+(?:के\\s+)?(?:बिना|बगैर)|के\\s+(?:परामर्श|निर्देश|बिना\\s+पूछे)(?:\\s+(?:के\\s+)?(?:बिना|बगैर))?)" +
        "$SAME_SENTENCE" +
        "(?:बंद|रोक(?:ें|ना)?|छोड़(?:ें|ना)?|बदल(?:ें|ना)?|कम|ज्यादा|ज़्यादा|बढ़ा(?:एं|एँ|ना)?|घटा(?:एं|एँ|ना)?|दोगुन[ाी]|दुगन[ाी]|आध[ाी]|शुरू|परिवर्तन)" +
        "$SAME_SENTENCE" +
        "(?:न|मत|कभी\\s+न|नहीं)\\s*(?:करें|करो|कीजिए|लें|लो|लीजिए|खाएं|खाएँ)?"
    ),
    // E.g. "दवा बंद करने या खुराक बदलने से पहले हमेशा अपने डॉक्टर से सलाह लें"
    uRegex(
      "(?:$HI_MED_NOUN|$HI_DOSE_OR_SCHED)$SAME_SENTENCE" +
        "(?:बंद\\s+करने|रोकने|छोड़ने|बदलने|कम\\s+करने|बढ़ाने|घटाने|शुरू\\s+करने)$SAME_SENTENCE" +
        "(?:से\\s+पहले|पहले)$SAME_SENTENCE" +
        "$HI_CLINICIAN\\s*(?:से\\s+(?:पूछें|सलाह\\s+लें|परामर्श\\s+करें|बात\\s+करें)|की\\s+सलाह\\s+लें)"
    ),
    // E.g. "दवा तब तक बंद न करें जब तक डॉक्टर न कहें"
    uRegex(
      "(?:$HI_MED_NOUN|$HI_DOSE_OR_SCHED)$SAME_SENTENCE" +
        "(?:बंद|बदल|कम|बढ़ा|घटा)\\s+(?:न|मत)\\s*(?:करें|करो|कीजिए)$SAME_SENTENCE" +
        "जब\\s+तक\\s+(?:आपके\\s+)?$HI_CLINICIAN"
    )
  )

  private val HI_UNSAFE_ADVICE_REGEXES = listOf(
    // Stopping / halting / skipping / starting medication
    uRegex(
      "(?:$HI_MED_NOUN)(?:\\s+(?:को|लेना|खाना|पीना))?\\s+" +
        "(?:बंद\\s*(?:कर\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|करें|करो|कीजिए)|रोक\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|छोड़\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|" +
        "शुरू\\s*(?:कर\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|करें|करो|कीजिए)|stop\\s*(?:करें|कर\\s+दें))"
    ),
    uRegex("(?:एक|कोई)?\\s*(?:खुराक|गोली|डोज|डोज़|dose)\\s+(?:छोड़|टाल|स्किप|skip)\\s*(?:दें|दो|दीजिए|कर\\s+दें|करें|सकते\\s+हैं)"),
    uRegex("नई\\s+$HI_MED_NOUN\\s+(?:लेना\\s+)?शुरू\\s+(?:करें|कर\\s+दें|करो|कीजिए)"),
    // Dose / frequency / duration increase, reduction, doubling, halving, alteration
    uRegex(
      "(?:$HI_DOSE_OR_SCHED)(?:\\s+को)?\\s+" +
        "(?:बढ़ा(?:\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|एं|एँ)|" +
        "घटा(?:\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|एं|एँ)|" +
        "कम\\s+(?:कर\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|करें|करो|कीजिए)|" +
        "ज्यादा\\s+(?:कर\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|करें)|" +
        "ज़्यादा\\s+(?:कर\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|करें)|" +
        "बदल(?:\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|ें)|" +
        "(?:दोगुन[ाी]|दुगन[ाी]|आध[ाी]|double|halve|increase|decrease|reduce)\\s*(?:कर\\s*(?:दें|दो|दीजिए|सकते\\s+हैं)|करें|करो|कीजिए))"
    ),
    // Splitting / cutting tablet in half
    uRegex("(?:गोली|टैबलेट|tablet)(?:\\s+को)?\\s+आध[ाी]\\s+(?:करें|कर\\s+दें|तोड़\\s*(?:लें|दें)|काट\\s*(?:लें|दें))"),
    // Taking extra or multiple pills (ASCII + Devanagari numerals + words)
    uRegex(
      "(?:दो|2|२|तीन|3|३|चार|4|४|अतिरिक्त|एक्स्ट्रा|ज्यादा|ज़्यादा|दोगुनी|दुगनी|आधी|extra|double)\\s+" +
        "(?:गोलियां|गोलियाँ|गोली|कैप्सूल|टैबलेट|खुराक|pills?|tablets?|capsules?|doses?)\\s+" +
        "(?:लें|लो|लीजिए|खाएं|खाएँ|ले\\s+सकते\\s+हैं|खा\\s+सकते\\s+हैं)"
    ),
    // Switching medication
    uRegex("(?:इस\\s+दवा\\s+के\\s+बदले|इसकी\\s+जगह|इसके\\s+बदले)\\s+(?:दूसरी|कोई\\s+और|अन्य)?\\s*(?:दवा|गोली)?\\s*(?:लें|शुरू\\s+करें|बदल\\s+दें|ले\\s+सकते\\s+हैं)"),
    // Prescribing recommendations
    uRegex("(?:आपको|तुम्हें|मरीज\\s+को)\\s+(?:यह\\s+|नई\\s+|दूसरी\\s+|अधिक\\s+)?(?:$HI_MED_NOUN|$HI_DOSE_OR_SCHED)\\s+(?:लेनी|लेना|शुरू\\s+करनी|बंद\\s+करनी|बढ़ानी|कम\\s+करनी|बदलनी)\\s+चाहिए"),
    uRegex("(?:हम|मैं)\\s+(?:इस\\s+)?(?:$HI_MED_NOUN|$HI_DOSE_OR_SCHED)(?:\\s+को)?\\s+(?:लेने|शुरू\\s+करने|बंद\\s+करने|बदलने|बढ़ाने|कम\\s+करने)\\s+की\\s+सलाह\\s+(?:देता\\s+हूं|देता\\s+हूँ|देती\\s+हूं|देते\\s+हैं)"),
    // Conservative unsupported therapeutic purpose claims anchored to medication + treatment verb
    uRegex(
      "(?:यह|इस)\\s+$HI_MED_NOUN(?:\\s+का\\s+उपयोग)?\\s+[^.!?;\\n\\u0964\\u0965]{1,50}?" +
        "(?:के\\s+इलाज\\s+(?:में|के\\s+लिए)|के\\s+उपचार\\s+(?:में|के\\s+लिए)|को\\s+ठीक\\s+करने\\s+(?:में|के\\s+लिए)|को\\s+नियंत्रित\\s+करने\\s+(?:में|के\\s+लिए)|को\\s+कम\\s+करने\\s+(?:में|के\\s+लिए))"
    ),
    uRegex("(?:के\\s+इलाज|के\\s+उपचार)\\s+(?:के\\s+लिए|हेतु)\\s+(?:दी\\s+(?:गई|जाती)\\s+है|लिखी\\s+(?:गई|जाती)\\s+है|प्रयोग\\s+होती\\s+है)")
  )

  private val HI_MEDICATION_REFERENCE_REGEX = uRegex(
    "दवा(?:ई|इयां|इयाँ|इयों)?|औषधि|गोली|गोलियां|गोलियाँ|गोलियों|कैप्सूल|टैबलेट|खुराक|मात्रा|डोज|डोज़|पर्चा|नुस्खा|इंजेक्शन|सिरप|बूंदें|मरहम|" +
      "meds?|medicine|medicines|medication|medications|drug|drugs|pill|pills|tablet|tablets|capsule|capsules|dose|dosage|drops?"
  )

  private val HI_CLAUSE_SPLIT_REGEX = Regex(
    "[.!?;,\\n\\u0964\\u0965]|$B_START(?:and|और|तथा|व|एवं)$B_END",
    RegexOption.IGNORE_CASE
  )

  // ==========================================================================
  // BENGALI (bn) SAFETY REGEXES (Bengali + Bilingual Code-Switching)
  // ==========================================================================

  private const val BN_CLINICIAN =
    "(?:ডাক্তার(?:ের|কে)?|চিকিৎসক(?:ের|কে)?|ফার্মাসিস্ট(?:ের)?|স্বাস্থ্যসেবা\\s+প্রদানকারী(?:র)?)"

  private const val BN_MED_NOUN =
    "(?:ওষুধ(?:টি|গুলো|পত্র)?|ঔষধ|বড়ি(?:টি|গুলো)?|ট্যাবলেট(?:টি)?|ক্যাপসুল(?:টি)?|সিরাপ|dose|dosage|tablet|capsule|pill|medicine|medication)"

  private const val BN_DOSE_OR_SCHED =
    "(?:ডোজ(?:টি)?|মাত্রা(?:টি)?|পরিমাণ|সময়সূচী|সময়কাল|dose|dosage|frequency|duration|schedule)"

  private val BN_PROTECTIVE_REGEXES = listOf(
    // E.g. "ডাক্তারের পরামর্শ ছাড়া ওষুধ বন্ধ করবেন না"
    uRegex(
      "$BN_CLINICIAN\\s*(?:পরামর্শ|নির্দেশ|অনুমতি|কথা)\\s*ছাড়া$SAME_SENTENCE" +
        "(?:বন্ধ|থামাবেন|পরিবর্তন|বদল|কমা|বাড়া|দ্বিগুণ|অর্ধেক|শুরু|বাদ)$SAME_SENTENCE" +
        "(?:করবেন\\s+না|নেবেন\\s+না|খাবেন\\s+না|না)"
    ),
    // E.g. "ওষুধ বন্ধ করার বা ডোজ পরিবর্তনের আগে ডাক্তারের পরামর্শ নিন"
    uRegex(
      "(?:$BN_MED_NOUN|$BN_DOSE_OR_SCHED)$SAME_SENTENCE" +
        "(?:বন্ধ\\s+করার|থামানোর|পরিবর্তন(?:ের|\\s+করার)|বদলানোর|কমানোর|বাড়ানোর|শুরু\\s+করার)\\s*আগে$SAME_SENTENCE" +
        "$BN_CLINICIAN\\s*(?:পরামর্শ\\s+নিন|সাথে\\s+কথা\\s+বলুন|জিজ্ঞাসা\\s+করুন)"
    )
  )

  private val BN_UNSAFE_ADVICE_REGEXES = listOf(
    // Stopping / halting / skipping / starting medication
    uRegex(
      "(?:$BN_MED_NOUN)(?:\\s+(?:খাওয়া|নেওয়া))?\\s+" +
        "(?:বন্ধ\\s+(?:করুন|করো|করে\\s+দিন|করতে\\s+পারেন)|থামান|থামিয়ে\\s+দিন|বাদ\\s+দিন|ছেড়ে\\s+দিন|" +
        "শুরু\\s+(?:করুন|করো|করতে\\s+পারেন)|stop\\s*(?:করুন|করতে\\s+পারেন))"
    ),
    uRegex("(?:একটি|একটা|কোনো)\\s+(?:ডোজ|বড়ি|ট্যাবলেট|dose)\\s+(?:বাদ\\s+দিন|এড়িয়ে\\s+যান|skip\\s*করুন|না\\s+খেলেও\\s+হবে)"),
    uRegex("নতুন\\s+$BN_MED_NOUN\\s+(?:খাওয়া\\s+|নেওয়া\\s+)?শুরু\\s+(?:করুন|করো|করতে\\s+পারেন)"),
    // Dose / frequency / duration modifications
    uRegex(
      "(?:$BN_DOSE_OR_SCHED)\\s+" +
        "(?:বাড়িয়ে\\s+দিন|বাড়ান|বাড়াতে\\s+পারেন|" +
        "কমিয়ে\\s+দিন|কমান|কমাতে\\s+পারেন|" +
        "পরিবর্তন\\s+(?:করুন|করতে\\s+পারেন)|বদলে\\s+দিন|" +
        "দ্বিগুণ\\s+(?:করুন|করে\\s+দিন|করতে\\s+পারেন)|" +
        "অর্ধেক\\s+(?:করুন|করে\\s+দিন|করতে\\s+পারেন)|" +
        "(?:double|halve|increase|decrease|reduce|কম|বেশি)\\s*(?:করুন|করে\\s+দিন|করতে\\s+পারেন))"
    ),
    uRegex("(?:বড়ি|ট্যাবলেট|tablet)(?:টি)?\\s+অর্ধেক\\s+(?:করুন|ভেঙে\\s+নিন|কেটে\\s+নিন)"),
    // Extra or multiple pills (ASCII + Bengali digits + words)
    uRegex(
      "(?:দুই|দুটি|দুটো|2|২|তিন|তিনটি|3|৩|চার|চারটি|4|৪|অতিরিক্ত|বেশি|দ্বিগুণ|অর্ধেক|extra|double)(?:টি|টা|খানা)?\\s+" +
        "(?:বড়ি|ট্যাবলেট|ক্যাপসুল|ডোজ|মাত্রা|pills?|tablets?|capsules?|doses?)\\s+" +
        "(?:নিন|খান|খেতে\\s+পারেন|নিতে\\s+পারেন)"
    ),
    // Switching
    uRegex("(?:এই\\s+ওষুধের\\s+বদলে|এর\\s+পরিবর্তে)\\s+(?:অন্য\\s+ওষুধ)?\\s*(?:নিন|খান|ব্যবহার\\s+করুন)"),
    // Prescribing recommendations
    uRegex("(?:আপনার|তোমার|রোগীর)\\s+(?:এই\\s+|নতুন\\s+|অন্য\\s+)?(?:$BN_MED_NOUN|$BN_DOSE_OR_SCHED)\\s+(?:খাওয়া|নেওয়া|শুরু\\s+করা|বন্ধ\\s+করা|বাড়ানো|কমানো|পরিবর্তন\\s+করা)\\s+উচিত"),
    uRegex("(?:আমি|আমরা)\\s+(?:এই\\s+)?(?:$BN_MED_NOUN|$BN_DOSE_OR_SCHED)\\s+(?:নেওয়ার|খাওয়ার|শুরু\\s+করার|বন্ধ\\s+করার|বাড়ানোর|কমানোর)\\s+পরামর্শ\\s+(?:দিচ্ছি|দিই)"),
    // Conservative unsupported therapeutic purpose claims (no broad isolated "জন্য")
    uRegex(
      "(?:এই\\s+)?$BN_MED_NOUN\\s+[^.!?;\\n\\u0964\\u0965]{1,50}?" +
        "(?:চিকিৎসার\\s+জন্য\\s+(?:দেওয়া|ব্যবহৃত|নির্ধারিত)|উপশমের\\s+জন্য\\s+(?:দেওয়া|ব্যবহৃত)|সারাতে\\s+ব্যবহৃত|নিয়ন্ত্রণে\\s+রাখার\\s+জন্য|চিকিৎসায়\\s+ব্যবহৃত\\s+হয়)"
    ),
    uRegex("(?:রোগ|ব্যথা|সংক্রমণ|প্রদাহ|জ্বর|রক্তচাপ)\\s+(?:সারাতে|কমাতে|চিকিৎসার\\s+জন্য)\\s+(?:এই\\s+ওষুধ|ওষুধটি|দেওয়া\\s+হয়েছে)")
  )

  private val BN_MEDICATION_REFERENCE_REGEX = uRegex(
    "ওষুধ(?:পত্র|টি|গুলো)?|ঔষধ|বড়ি(?:টি|গুলো)?|ট্যাবলেট(?:টি)?|ক্যাপসুল|ডোজ(?:টি)?|মাত্রা|প্রেসক্রিপশন|সিরাপ|ইনজেকশন|ড্রপ|মলম|" +
      "meds?|medicine|medicines|medication|medications|drug|drugs|pill|pills|tablet|tablets|capsule|capsules|dose|dosage|drops?"
  )

  private val BN_CLAUSE_SPLIT_REGEX = Regex(
    "[.!?;,\\n\\u0964\\u0965]|$B_START(?:and|এবং|ও|আর)$B_END",
    RegexOption.IGNORE_CASE
  )

  // ==========================================================================
  // MARATHI (mr) SAFETY REGEXES (Devanagari + Bilingual Code-Switching)
  // ==========================================================================

  private const val MR_CLINICIAN =
    "(?:डॉक्टर(?:ांचा|ांशी|ांच्या|ांना)?|चिकित्सक|वैद्यकीय\\s+तज्ज्ञ|फार्मासिस्ट)"

  private const val MR_MED_NOUN =
    "(?:औषध(?:े)?|गोळी|गोळ्या|कॅप्सूल|टॅब्लेट|सिरप|dose|dosage|tablet|capsule|pill|medicine|medication)"

  private const val MR_DOSE_OR_SCHED =
    "(?:डोस|मात्रा|प्रमाण|कालावधी|वारंवारता|वेळापत्रक|dose|dosage|frequency|duration|schedule)"

  private val MR_PROTECTIVE_REGEXES = listOf(
    // E.g. "डॉक्टरांचा सल्ला घेतल्याशिवाय औषध बंद करू नका" / "डॉक्टरांच्या सल्ल्याशिवाय डोस बदलू नका"
    uRegex(
      "$MR_CLINICIAN\\s*(?:सल्ला\\s+घेतल्याशिवाय|सल्ल्याशिवाय|विचारल्याशिवाय|परवानगीशिवाय|सल्ल्याविना)$SAME_SENTENCE" +
        "(?:बंद|थांबव|सोड|बदल|कमी|जास्त|वाढव|घटव|दुप्पट|निम्म[ाी]|सुरू)$SAME_SENTENCE" +
        "(?:करू\\s+नका|घेऊ\\s+नका|थांबवू\\s+नका|बदलू\\s+नका|वाढवू\\s+नका|नका)"
    ),
    // E.g. "औषध बंद करण्यापूर्वी किंवा डोस बदलण्यापूर्वी डॉक्टरांचा सल्ला घ्या"
    uRegex(
      "(?:$MR_MED_NOUN|$MR_DOSE_OR_SCHED)$SAME_SENTENCE" +
        "(?:बंद\\s+करण्यापूर्वी|थांबवण्यापूर्वी|बदलण्यापूर्वी|कमी\\s+करण्यापूर्वी|वाढवण्यापूर्वी|सुरू\\s+करण्यापूर्वी)$SAME_SENTENCE" +
        "$MR_CLINICIAN$SAME_SENTENCE(?:सल्ला\\s+घ्या|विचारा|बोला|चर्चा\\s+करा)"
    )
  )

  private val MR_UNSAFE_ADVICE_REGEXES = listOf(
    // Stopping / halting / skipping / starting medication
    uRegex(
      "(?:$MR_MED_NOUN)(?:\\s+(?:घेणे|खाणे))?\\s+" +
        "(?:बंद\\s+(?:करा|करावे|करू\\s+शकता|करून\\s+टाका)|थांबवा|सोडून\\s+द्या|टाळा|" +
        "सुरू\\s+(?:करा|करावे|करू\\s+शकता)|stop\\s*(?:करा|करू\\s+शकता))"
    ),
    uRegex("(?:एक|एखादा|एखादी)\\s+(?:डोस|गोळी|मात्रा|dose)\\s+(?:सोडा|वगळा|टाळा|skip\\s*करा|बंद\\s+करा)"),
    uRegex("नवीन\\s+$MR_MED_NOUN\\s+(?:घेणे\\s+)?सुरू\\s+(?:करा|करावे|करू\\s+शकता)"),
    // Dose / frequency / duration modifications
    uRegex(
      "(?:$MR_DOSE_OR_SCHED)\\s+" +
        "(?:वाढवा|वाढवू\\s+शकता|कमी\\s+(?:करा|करू\\s+शकता)|घटवा|बदला|बदलवा|" +
        "दुप्पट\\s+(?:करा|करू\\s+शकता)|निम्म[ाी]\\s+(?:करा|करू\\s+शकता)|अर्धा\\s+करा|" +
        "(?:double|halve|increase|decrease|reduce)\\s*(?:करा|करू\\s+शकता))"
    ),
    uRegex("(?:गोळी|टॅब्लेट|tablet)\\s+(?:अर्धी\\s+(?:करा|तोडा|कापा)|दोन\\s+तुकडे\\s+करा)"),
    // Extra or multiple pills (ASCII + Devanagari numerals + words)
    uRegex(
      "(?:दोन|2|२|तीन|3|३|चार|4|४|अतिरिक्त|जास्त|दुप्पट|अर्धी|अर्धा|extra|double)\\s+" +
        "(?:गोळ्या|गोळी|कॅप्सूल|टॅब्लेट|डोस|मात्रा|pills?|tablets?|capsules?|doses?)\\s+" +
        "(?:घ्या|घ्याव्यात|खा|घेऊ\\s+शकता)"
    ),
    // Switching
    uRegex("(?:या\\s+औषधाऐवजी|याऐवजी)\\s+(?:दुसरे\\s+औषध)?\\s*(?:घ्या|सुरू\\s+करा|बदला)"),
    // Prescribing recommendations
    uRegex("(?:तुम्ही|आपण|रुग्णाने)\\s+(?:हे\\s+|नवीन\\s+|दुसरे\\s+)?(?:$MR_MED_NOUN|$MR_DOSE_OR_SCHED)\\s+(?:घ्यावे|घ्यावी|घ्यावा|सुरू\\s+करावे|बंद\\s+करावे|वाढवावे|कमी\\s+करावे|बदलावे)"),
    uRegex("(?:मी|आम्ही)\\s+(?:हे\\s+)?(?:$MR_MED_NOUN|$MR_DOSE_OR_SCHED)\\s+(?:घेण्याचा|सुरू\\s+करण्याचा|बंद\\s+करण्याचा|बदलण्याचा)\\s+सल्ला\\s+(?:देतो|देते|देतोय)"),
    // Conservative unsupported therapeutic purpose claims (no broad isolated "साठी")
    uRegex(
      "(?:हे\\s+)?$MR_MED_NOUN\\s+[^.!?;\\n\\u0964\\u0965]{1,50}?" +
        "(?:उपचारासाठी\\s+(?:दिले\\s+आहे|दिली\\s+आहे|वापरले\\s+जाते)|बरे\\s+करण्यासाठी|कमी\\s+करण्यासाठी\\s+(?:दिले\\s+आहे|वापरतात)|नियंत्रित\\s+करण्यासाठी)"
    ),
    uRegex("उपचारासाठी\\s+(?:औषध\\s+)?(?:दिले\\s+आहे|लिहिले\\s+आहे|वापरले\\s+जाते)")
  )

  private val MR_MEDICATION_REFERENCE_REGEX = uRegex(
    "औषध(?:े|ांचा|ाची|ाला)?|गोळी|गोळ्या|कॅप्सूल|टॅब्लेट|डोस|मात्रा|प्रिस्क्रिप्शन|सिरप|इंजेक्शन|थेंब|मलम|" +
      "meds?|medicine|medicines|medication|medications|drug|drugs|pill|pills|tablet|tablets|capsule|capsules|dose|dosage|drops?"
  )

  private val MR_CLAUSE_SPLIT_REGEX = Regex(
    "[.!?;,\\n\\u0964\\u0965]|$B_START(?:and|आणि|व|तसेच)$B_END",
    RegexOption.IGNORE_CASE
  )

  // ==========================================================================
  // AMBIGUOUS ABBREVIATION RULES (11 Rules Covering 15 Codes Across 4 Languages)
  // ==========================================================================

  val AMBIGUOUS_ABBREVIATION_RULES: List<AbbreviationRuleSpec> = listOf(
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.OD,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])O\\.?D\\.?(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex(
        "\\b(once\\s+(?:a\\s+)?daily|once\\s+(?:per\\s+|a\\s+)?day|one\\s+time\\s+(?:a\\s+|per\\s+)?day|every\\s+day|right\\s+eye|oculus\\s+dexter)\\b",
        RegexOption.IGNORE_CASE
      ),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex(
          "दिन\\s+में\\s+एक\\s+बार|रोज(?:़|ाना)?\\s+एक\\s+बार|प्रतिदिन\\s+एक\\s+बार|हर\\s+रोज(?:़)?|हर\\s+दिन|दाहिनी\\s+आ[ँं]ख|दाईं\\s+आ[ँं]ख"
        ),
        SupportedExplanationLanguage.BENGALI to uRegex(
          "দিনে\\s+একবার|রোজ\\s+একবার|প্রতিদিন\\s+একবার|প্রতিদিন|রোজ|ডান\\s+চোখ(?:ে)?"
        ),
        SupportedExplanationLanguage.MARATHI to uRegex(
          "दिवसातून\\s+एकदा|दररोज\\s+एकदा|रोज\\s+एकदा|दररोज|उजवा\\s+डोळा|उजव्या\\s+डोळ्यात"
        )
      )
    ),
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.QD,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])Q\\.?D\\.?(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex(
        "\\b(once\\s+(?:a\\s+)?daily|once\\s+(?:per\\s+|a\\s+)?day|one\\s+time\\s+(?:a\\s+|per\\s+)?day|every\\s+day|each\\s+day)\\b",
        RegexOption.IGNORE_CASE
      ),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex(
          "दिन\\s+में\\s+एक\\s+बार|रोज(?:़|ाना)?\\s+एक\\s+बार|प्रतिदिन\\s+एक\\s+बार|हर\\s+रोज(?:़)?|हर\\s+दिन|प्रत्येक\\s+दिन"
        ),
        SupportedExplanationLanguage.BENGALI to uRegex(
          "দিনে\\s+একবার|রোজ\\s+একবার|প্রতিদিন\\s+একবার|প্রতিদিন|প্রত্যেক\\s+দিন|রোজ"
        ),
        SupportedExplanationLanguage.MARATHI to uRegex(
          "दिवसातून\\s+एकदा|दररोज\\s+एकदा|रोज\\s+एकदा|दररोज|प्रत्येक\\s+दिवशी"
        )
      )
    ),
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.BD_BID,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])(?:B\\.?D\\.?|B\\.?I\\.?D\\.?)(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex(
        "\\b(twice\\s+(?:a\\s+)?daily|twice\\s+(?:per\\s+|a\\s+)?day|two\\s+times\\s+(?:a\\s+|per\\s+)?day|2\\s+times\\s+(?:a\\s+)?day|every\\s+12\\s+hours)\\b",
        RegexOption.IGNORE_CASE
      ),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex(
          "दिन\\s+में\\s+(?:दो|2|२)\\s+बार|रोज(?:़)?\\s+दो\\s+बार|सुबह[- ]?शाम|हर\\s+(?:12|१२)\\s+घंटे(?:\\s+में)?"
        ),
        SupportedExplanationLanguage.BENGALI to uRegex(
          "দিনে\\s+(?:দুবার|দুইবার|(?:2|২)\\s*বার)|সকাল[- ]?সন্ধ্যা|সকাল\\s+ও\\s+রাতে|প্রতি\\s+(?:12|১২)\\s+ঘণ্টা(?:\\s+পরপর|\\s+অন্তর)?"
        ),
        SupportedExplanationLanguage.MARATHI to uRegex(
          "दिवसातून\\s+(?:दोनदा|दोन\\s+वेळा|(?:2|२)\\s+वेळा)|सकाळ[- ]?संध्याकाळ|दर\\s+(?:12|१२)\\s+तासांनी"
        )
      )
    ),
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.TID_TDS,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])(?:T\\.?I\\.?D\\.?|T\\.?D\\.?S\\.?)(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex(
        "\\b(three\\s+times\\s+(?:a\\s+)?daily|three\\s+times\\s+(?:per\\s+|a\\s+)?day|thrice\\s+(?:a\\s+)?day|thrice\\s+daily|3\\s+times\\s+(?:a\\s+)?day|every\\s+8\\s+hours)\\b",
        RegexOption.IGNORE_CASE
      ),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex(
          "दिन\\s+में\\s+(?:तीन|3|३)\\s+बार|रोज(?:़)?\\s+तीन\\s+बार|सुबह,?\\s*दोपहर\\s+(?:और|व)\\s+(?:शाम|रात)|हर\\s+(?:8|८)\\s+घंटे(?:\\s+में)?"
        ),
        SupportedExplanationLanguage.BENGALI to uRegex(
          "দিনে\\s+(?:তিনবার|তিন\\s+বার|(?:3|৩)\\s*বার)|সকাল,?\\s*দুপুর\\s+ও\\s+রাত(?:ে)?|প্রতি\\s+(?:8|৮)\\s+ঘণ্টা(?:\\s+পরপর|\\s+অন্তর)?"
        ),
        SupportedExplanationLanguage.MARATHI to uRegex(
          "दिवसातून\\s+(?:तीनदा|तीन\\s+वेळा|(?:3|३)\\s+वेळा)|सकाळ,?\\s*दुपार\\s+(?:आणि|व)\\s+(?:संध्याकाळ|रात्र(?:ी)?)|दर\\s+(?:8|८)\\s+तासांनी"
        )
      )
    ),
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.QID,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])Q\\.?I\\.?D\\.?(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex(
        "\\b(four\\s+times\\s+(?:a\\s+)?daily|four\\s+times\\s+(?:per\\s+|a\\s+)?day|4\\s+times\\s+(?:a\\s+)?day|every\\s+6\\s+hours)\\b",
        RegexOption.IGNORE_CASE
      ),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex(
          "दिन\\s+में\\s+(?:चार|4|४)\\s+बार|हर\\s+(?:6|६)\\s+घंटे(?:\\s+में)?"
        ),
        SupportedExplanationLanguage.BENGALI to uRegex(
          "দিনে\\s+(?:চারবার|চার\\s+বার|(?:4|৪)\\s*বার)|প্রতি\\s+(?:6|৬)\\s+ঘণ্টা(?:\\s+পরপর|\\s+অন্তর)?"
        ),
        SupportedExplanationLanguage.MARATHI to uRegex(
          "दिवसातून\\s+(?:चारदा|चार\\s+वेळा|(?:4|४)\\s+वेळा)|दर\\s+(?:6|६)\\s+तासांनी"
        )
      )
    ),
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.PRN_SOS,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])(?:P\\.?R\\.?N\\.?|S\\.?O\\.?S\\.?)(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex(
        "\\b(as\\s+needed|when\\s+needed|if\\s+needed|as\\s+required|when\\s+required|only\\s+when|when\\s+symptoms)\\b",
        RegexOption.IGNORE_CASE
      ),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex(
          "आवश्यकता\\s+पड़ने\\s+पर|आवश्यकतानुसार|(?:ज|ज़)रूरत\\s+(?:पड़ने|होने)\\s+पर|(?:ज|ज़)रूरत\\s+के\\s+अनुसार|केवल\\s+(?:ज|ज़)रूरत\\s+होने\\s+पर|लक्षण\\s+होने\\s+पर"
        ),
        SupportedExplanationLanguage.BENGALI to uRegex(
          "প্রয়োজন\\s+হলে|প্রয়োজন\\s+অনুযায়ী|দরকার\\s+হলে|দরকার\\s+অনুযায়ী|শুধুমাত্র\\s+প্রয়োজন\\s+হলে|উপসর্গ\\s+দেখা\\s+দিলে"
        ),
        SupportedExplanationLanguage.MARATHI to uRegex(
          "गरज\\s+(?:पडल्यास|असल्यास)|गरजेनुसार|आवश्यकता\\s+असल्यास|लक्षणे\\s+दिसल्यास"
        )
      )
    ),
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.HS,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])H\\.?S\\.?(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex(
        "\\b(at\\s+bedtime|before\\s+(?:bed|sleep|sleeping)|bedtime|every\\s+night|at\\s+night|half[- ]strength)\\b",
        RegexOption.IGNORE_CASE
      ),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex(
          "सोते\\s+समय|सोने\\s+से\\s+पहले|रात\\s+को\\s+सोते\\s+समय|हर\\s+रात|रात\\s+(?:में|को)|आधी\\s+ताकत"
        ),
        SupportedExplanationLanguage.BENGALI to uRegex(
          "ঘুমানোর\\s+আগে|ঘুমাতে\\s+যাওয়ার\\s+আগে|শোয়ার\\s+সময়|প্রতি\\s+রাতে|রাতে|অর্ধেক\\s+মাত্রা(?:য়)?"
        ),
        SupportedExplanationLanguage.MARATHI to uRegex(
          "झोपण्यापूर्वी|झोपताना|रात्री\\s+झोपताना|दररोज\\s+रात्री|रात्री|निम्मी\\s+ताकद"
        )
      )
    ),
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.QOD,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])Q\\.?O\\.?D\\.?(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex("\\b(every\\s+other\\s+day|alternate\\s+days?)\\b", RegexOption.IGNORE_CASE),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex("एक\\s+दिन\\s+छोड़कर|हर\\s+दूसरे\\s+दिन"),
        SupportedExplanationLanguage.BENGALI to uRegex("এক\\s+দিন\\s+পর\\s*পর|একদিন\\s+পরপর|প্রতি\\s+দ্বিতীয়\\s+দিনে"),
        SupportedExplanationLanguage.MARATHI to uRegex("एक\\s+दिवसाआड|एक\\s+दिवस\\s+सोडून")
      )
    ),
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.AC,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])A\\.?C\\.?(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex("\\b(before\\s+(?:meals?|eating|food)|on\\s+an\\s+empty\\s+stomach)\\b", RegexOption.IGNORE_CASE),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex("(?:खाने|भोजन)\\s+से\\s+पहले|खाली\\s+पेट"),
        SupportedExplanationLanguage.BENGALI to uRegex("(?:খাওয়ার|খাবারের)\\s+আগে|খালি\\s+পেটে"),
        SupportedExplanationLanguage.MARATHI to uRegex("जेवणापूर्वी|जेवणाआधी|खाण्यापूर्वी|रिकाम्या\\s+पोटी|उपाशी\\s+पोटी")
      )
    ),
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.PC,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])P\\.?C\\.?(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex("\\b(after\\s+(?:meals?|eating|food))\\b", RegexOption.IGNORE_CASE),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex("(?:खाने|भोजन)\\s+के\\s+बाद"),
        SupportedExplanationLanguage.BENGALI to uRegex("(?:খাওয়ার|খাবারের)\\s+পরে"),
        SupportedExplanationLanguage.MARATHI to uRegex("जेवणानंतर|जेवण\\s+झाल्यानंतर|खाण्यानंतर")
      )
    ),
    AbbreviationRuleSpec(
      id = AbbreviationRuleId.OS_OU,
      codeRegex = Regex("(?<![\\p{L}\\p{N}\\p{M}])(?:O\\.?S\\.?|O\\.?U\\.?)(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE),
      englishExpansionRegex = Regex("\\b(left\\s+eye|oculus\\s+sinister|both\\s+eyes|each\\s+eye|oculus\\s+uterque)\\b", RegexOption.IGNORE_CASE),
      localizedExpansions = mapOf(
        SupportedExplanationLanguage.HINDI to uRegex(
          "बांई\\s+आ[ँं]ख|बाईं\\s+आ[ँं]ख|बाएं\\s+आ[ँं]ख|दोनों\\s+आ[ँं]खों|हर\\s+आ[ँं]ख|प्रत्येक\\s+आ[ँं]ख"
        ),
        SupportedExplanationLanguage.BENGALI to uRegex(
          "বাম\\s+চোখ(?:ে)?|বাঁ\\s+চোখ(?:ে)?|দুই\\s+চোখ(?:ে)?|উভয়\\s+চোখ(?:ে)?|প্রতিটি\\s+চোখ(?:ে)?"
        ),
        SupportedExplanationLanguage.MARATHI to uRegex(
          "डावा\\s+डोळा|डाव्या\\s+डोळ्यात|दोन्ही\\s+डोळे|दोन्ही\\s+डोळ्यांत|प्रत्येक\\s+डोळ्यात"
        )
      )
    )
  )

  // ==========================================================================
  // REGEX PACKS PER LANGUAGE
  // ==========================================================================

  private val regexPacks: Map<SupportedExplanationLanguage, LanguageSafetyRegexPack> = mapOf(
    SupportedExplanationLanguage.ENGLISH to LanguageSafetyRegexPack(
      protectiveWarningRegexes = listOf(
        EN_PROTECTIVE_MEDICATION_WARNING_REGEX,
        EN_CONSULT_BEFORE_CHANGE_WARNING_REGEX
      ),
      unsafeAdviceRegexes = listOf(EN_UNSAFE_MEDICATION_ADVICE_REGEX),
      medicationReferenceRegex = EN_MEDICATION_REFERENCE_REGEX,
      clauseSplitRegex = EN_CLAUSE_SPLIT_REGEX
    ),
    SupportedExplanationLanguage.HINDI to LanguageSafetyRegexPack(
      protectiveWarningRegexes = HI_PROTECTIVE_REGEXES,
      unsafeAdviceRegexes = HI_UNSAFE_ADVICE_REGEXES,
      medicationReferenceRegex = HI_MEDICATION_REFERENCE_REGEX,
      clauseSplitRegex = HI_CLAUSE_SPLIT_REGEX
    ),
    SupportedExplanationLanguage.BENGALI to LanguageSafetyRegexPack(
      protectiveWarningRegexes = BN_PROTECTIVE_REGEXES,
      unsafeAdviceRegexes = BN_UNSAFE_ADVICE_REGEXES,
      medicationReferenceRegex = BN_MEDICATION_REFERENCE_REGEX,
      clauseSplitRegex = BN_CLAUSE_SPLIT_REGEX
    ),
    SupportedExplanationLanguage.MARATHI to LanguageSafetyRegexPack(
      protectiveWarningRegexes = MR_PROTECTIVE_REGEXES,
      unsafeAdviceRegexes = MR_UNSAFE_ADVICE_REGEXES,
      medicationReferenceRegex = MR_MEDICATION_REFERENCE_REGEX,
      clauseSplitRegex = MR_CLAUSE_SPLIT_REGEX
    )
  )

  // ==========================================================================
  // FALLBACK, UNCERTAINTY, DISCLAIMER & LABEL PACKS PER LANGUAGE
  // ==========================================================================

  private val stringsPacks: Map<SupportedExplanationLanguage, SafetyStringsPack> = mapOf(
    SupportedExplanationLanguage.ENGLISH to SafetyStringsPack(
      language = SupportedExplanationLanguage.ENGLISH,
      safeFallbackSummary =
        "Plain-language summary is limited for this document. Please review the preserved original extracted entries below with your doctor.",
      uncertainStatusNote =
        "Marked uncertain or unreadable in the source document; please check the original report with your doctor.",
      notMentionedStatusNote =
        "Finding status was not mentioned in the source document.",
      cardiologyConclusionNotReported =
        "Formal cardiology conclusion was not reported in the extracted document.",
      specialisedConclusionNotReported =
        "Pathologist diagnostic conclusion was not reported in the extracted document.",
      medicalExplanationDisclaimer = ExplanationConstants.MEDICAL_EXPLANATION_DISCLAIMER,
      medicationSafetyNote = ExplanationConstants.MEDICATION_SAFETY_NOTE,
      cardiologyReportLabel = "Cardiology report",
      specialisedReportLabel = "Specialised pathology report",
      itemPrefix = "Item",
      findingPrefix = "Finding",
      impressionPrefix = "Impression",
      cardiologyMetricLabels = mapOf(
        "heartRateBpm" to "Heart Rate",
        "rhythmInterpretation" to "Rhythm",
        "prIntervalMs" to "PR Interval (ms)",
        "qrsDurationMs" to "QRS Duration (ms)",
        "qtcIntervalMs" to "QTc Interval (ms)",
        "ejectionFractionPercent" to "Ejection Fraction",
        "wallMotionFindings" to "Wall Motion"
      ),
      specialisedSectionLabels = mapOf(
        "specimenSource" to "Specimen Source",
        "clinicalHistory" to "Clinical History",
        "grossDescription" to "Gross Description",
        "microscopicDescription" to "Microscopic Description",
        "gradingOrStaging" to "Grading / Staging"
      )
    ),
    SupportedExplanationLanguage.HINDI to SafetyStringsPack(
      language = SupportedExplanationLanguage.HINDI,
      safeFallbackSummary =
        "इस दस्तावेज़ के लिए सरल भाषा का सारांश सीमित है। कृपया नीचे दिए गए मूल निकाले गए विवरण अपने डॉक्टर के साथ देखें।",
      uncertainStatusNote =
        "मूल दस्तावेज़ में अनिश्चित या अपठनीय चिह्नित; कृपया अपनी मूल रिपोर्ट डॉक्टर से जांचें।",
      notMentionedStatusNote =
        "मूल दस्तावेज़ में निष्कर्ष की स्थिति का उल्लेख नहीं था।",
      cardiologyConclusionNotReported =
        "निकाले गए दस्तावेज़ में औपचारिक कार्डियोलॉजी निष्कर्ष दर्ज नहीं था।",
      specialisedConclusionNotReported =
        "निकाले गए दस्तावेज़ में पैथोलॉजिस्ट का निदान निष्कर्ष दर्ज नहीं था।",
      medicalExplanationDisclaimer =
        "AI द्वारा तैयार व्याख्या (चिकित्सकीय रूप से सत्यापित नहीं): यह सरल भाषा का सारांश केवल जानकारी की स्पष्टता के लिए आपकी निकाली गई रिपोर्ट से AI द्वारा तैयार किया गया है। यह कोई चिकित्सीय निदान, उपचार की सलाह या क्लिनिकल चेतावनी नहीं है। हमेशा अपनी मूल रिपोर्ट किसी योग्य डॉक्टर से दिखाएं।",
      medicationSafetyNote =
        "केवल अपने पर्चे पर छपी खुराक और समय का ही पालन करें। अपने डॉक्टर से सलाह लिए बिना इस दवा को कभी न बदलें और न ही बंद करें।",
      cardiologyReportLabel = "कार्डियोलॉजी रिपोर्ट",
      specialisedReportLabel = "विशेष पैथोलॉजी रिपोर्ट",
      itemPrefix = "आइटम",
      findingPrefix = "निष्कर्ष",
      impressionPrefix = "इंप्रेशन",
      cardiologyMetricLabels = mapOf(
        "heartRateBpm" to "हृदय गति",
        "rhythmInterpretation" to "रिदम",
        "prIntervalMs" to "PR इंटरवल (ms)",
        "qrsDurationMs" to "QRS अवधि (ms)",
        "qtcIntervalMs" to "QTc इंटरवल (ms)",
        "ejectionFractionPercent" to "इजेक्शन फ्रैक्शन",
        "wallMotionFindings" to "वॉल मोशन"
      ),
      specialisedSectionLabels = mapOf(
        "specimenSource" to "नमूना स्रोत",
        "clinicalHistory" to "क्लिनिकल इतिहास",
        "grossDescription" to "स्थूल विवरण",
        "microscopicDescription" to "सूक्ष्मदर्शी विवरण",
        "gradingOrStaging" to "ग्रेडिंग / स्टेजिंग"
      )
    ),
    SupportedExplanationLanguage.BENGALI to SafetyStringsPack(
      language = SupportedExplanationLanguage.BENGALI,
      safeFallbackSummary =
        "এই নথির জন্য সহজ ভাষার সারাংশ সীমিত। অনুগ্রহ করে নিচে সংরক্ষিত মূল উদ্ধৃত তথ্য আপনার ডাক্তারের সাথে পর্যালোচনা করুন।",
      uncertainStatusNote =
        "মূল নথিতে অনিশ্চিত বা অস্পষ্ট হিসেবে চিহ্নিত; অনুগ্রহ করে আপনার ডাক্তারের সাথে মূল রিপোর্টটি যাচাই করুন।",
      notMentionedStatusNote =
        "মূল নথিতে ফলাফলের অবস্থা উল্লেখ করা হয়নি।",
      cardiologyConclusionNotReported =
        "উদ্ধৃত নথিতে আনুষ্ঠানিক কার্ডিওলজি উপসংহার উল্লেখ করা হয়নি।",
      specialisedConclusionNotReported =
        "উদ্ধৃত নথিতে প্যাথলজিস্টের রোগনির্ণয় সংক্রান্ত উপসংহার উল্লেখ করা হয়নি।",
      medicalExplanationDisclaimer =
        "AI-প্রস্তুত ব্যাখ্যা (চিকিৎসাগতভাবে যাচাইকৃত নয়): এই সহজ ভাষার সারাংশটি কেবল তথ্যের স্পষ্টতার জন্য আপনার রিপোর্টের উদ্ধৃত অংশ থেকে AI দ্বারা তৈরি করা হয়েছে। এটি কোনো রোগনির্ণয়, চিকিৎসার পরামর্শ বা ক্লিনিকাল সতর্কবার্তা নয়। সর্বদা আপনার মূল রিপোর্টটি একজন যোগ্য ডাক্তারের সাথে পর্যালোচনা করুন।",
      medicationSafetyNote =
        "শুধুমাত্র আপনার প্রেসক্রিপশনে মুদ্রিত মাত্রা ও সময়সূচী অনুসরণ করুন। আপনার ডাক্তারের পরামর্শ ছাড়া কখনোই এই ওষুধ পরিবর্তন বা বন্ধ করবেন না।",
      cardiologyReportLabel = "কার্ডিওলজি রিপোর্ট",
      specialisedReportLabel = "বিশেষ প্যাথলজি রিপোর্ট",
      itemPrefix = "আইটেম",
      findingPrefix = "পর্যবেক্ষণ",
      impressionPrefix = "ইম্প্রেশন",
      cardiologyMetricLabels = mapOf(
        "heartRateBpm" to "হৃদস্পন্দনের হার",
        "rhythmInterpretation" to "হৃদস্পন্দনের ছন্দ",
        "prIntervalMs" to "PR ব্যবধান (ms)",
        "qrsDurationMs" to "QRS সময়কাল (ms)",
        "qtcIntervalMs" to "QTc ব্যবধান (ms)",
        "ejectionFractionPercent" to "ইজেকশন ফ্র্যাকশন",
        "wallMotionFindings" to "ওয়াল মোশন"
      ),
      specialisedSectionLabels = mapOf(
        "specimenSource" to "নমুনার উৎস",
        "clinicalHistory" to "ক্লিনিকাল ইতিহাস",
        "grossDescription" to "সামগ্রিক বিবরণ",
        "microscopicDescription" to "অণুবীক্ষণিক বিবরণ",
        "gradingOrStaging" to "গ্রেডিং / স্টেজিং"
      )
    ),
    SupportedExplanationLanguage.MARATHI to SafetyStringsPack(
      language = SupportedExplanationLanguage.MARATHI,
      safeFallbackSummary =
        "या दस्तऐवजासाठी सोप्या भाषेतील सारांश मर्यादित आहे. कृपया खाली दिलेला मूळ काढलेला तपशील तुमच्या डॉक्टरांसोबत तपासा.",
      uncertainStatusNote =
        "मूळ दस्तऐवजात अनिश्चित किंवा अस्पष्ट म्हणून नोंदवले आहे; कृपया मूळ अहवाल तुमच्या डॉक्टरांकडून तपासून घ्या.",
      notMentionedStatusNote =
        "मूळ दस्तऐवजात निष्कर्षाची स्थिती नमूद केलेली नव्हती.",
      cardiologyConclusionNotReported =
        "काढलेल्या दस्तऐवजात औपचारिक कार्डिओलॉजी निष्कर्ष नोंदवलेला नव्हता.",
      specialisedConclusionNotReported =
        "काढलेल्या दस्तऐवजात पॅथॉलॉजिस्टचा निदान निष्कर्ष नोंदवलेला नव्हता.",
      medicalExplanationDisclaimer =
        "AI-निर्मित स्पष्टीकरण (वैद्यकीयदृष्ट्या प्रमाणित नाही): हा सोप्या भाषेतील सारांश केवळ माहितीच्या स्पष्टतेसाठी तुमच्या काढलेल्या अहवालातील मजकुरावरून AI द्वारे तयार केला आहे. हे वैद्यकीय निदान, उपचारांचा सल्ला किंवा क्लिनिकल इशारा नाही. कृपया तुमचा मूळ अहवाल नेहमी पात्र डॉक्टरांना दाखवा.",
      medicationSafetyNote =
        "केवळ तुमच्या प्रिस्क्रिप्शनवर छापलेला डोस आणि वेळापत्रक पाळा. तुमच्या डॉक्टरांचा सल्ला घेतल्याशिवाय हे औषध कधीही बदलू किंवा बंद करू नका.",
      cardiologyReportLabel = "कार्डिओलॉजी अहवाल",
      specialisedReportLabel = "विशेष पॅथॉलॉजी अहवाल",
      itemPrefix = "घटक",
      findingPrefix = "निरीक्षण",
      impressionPrefix = "इम्प्रेशन",
      cardiologyMetricLabels = mapOf(
        "heartRateBpm" to "हृदयाचे ठोके",
        "rhythmInterpretation" to "लय (रिदम)",
        "prIntervalMs" to "PR अंतर (ms)",
        "qrsDurationMs" to "QRS कालावधी (ms)",
        "qtcIntervalMs" to "QTc अंतर (ms)",
        "ejectionFractionPercent" to "इजेक्शन फ्रॅक्शन",
        "wallMotionFindings" to "वॉल मोशन"
      ),
      specialisedSectionLabels = mapOf(
        "specimenSource" to "नमुना स्रोत",
        "clinicalHistory" to "क्लिनिकल इतिहास",
        "grossDescription" to "स्थूल वर्णन",
        "microscopicDescription" to "सूक्ष्मदर्शक वर्णन",
        "gradingOrStaging" to "ग्रेडिंग / स्टेजिंग"
      )
    )
  )

  fun stringsFor(language: SupportedExplanationLanguage): SafetyStringsPack =
    stringsPacks[language] ?: stringsPacks.getValue(SupportedExplanationLanguage.ENGLISH)

  fun stringsFor(language: String?): SafetyStringsPack =
    stringsFor(resolveLanguage(language))

  fun regexPackFor(language: SupportedExplanationLanguage): LanguageSafetyRegexPack =
    regexPacks[language] ?: regexPacks.getValue(SupportedExplanationLanguage.ENGLISH)

  /**
   * Evaluates BOTH English baseline rules and the active target language's rules so code-switched
   * English inside Hindi/Bengali/Marathi output cannot bypass safety protections.
   */
  fun activeRegexPacksFor(language: SupportedExplanationLanguage): List<LanguageSafetyRegexPack> {
    val english = regexPacks.getValue(SupportedExplanationLanguage.ENGLISH)
    if (language == SupportedExplanationLanguage.ENGLISH) {
      return listOf(english)
    }
    val localized = regexPacks[language] ?: return listOf(english)
    return listOf(english, localized)
  }

  /**
   * Strips same-sentence protective medication warnings for both English and [language],
   * then tests all unsafe medication advice patterns across English and [language].
   */
  fun containsUnsafeMedicationAdvice(
    text: String,
    language: SupportedExplanationLanguage = SupportedExplanationLanguage.ENGLISH
  ): Boolean {
    if (text.isBlank()) return false
    var scrubbed = normalizeForMatching(text)
    val packs = activeRegexPacksFor(language)
    for (pack in packs) {
      for (protectiveRegex in pack.protectiveWarningRegexes) {
        scrubbed = scrubbed.replace(protectiveRegex, " ")
      }
    }
    return packs.any { pack ->
      pack.unsafeAdviceRegexes.any { unsafeRegex -> unsafeRegex.containsMatchIn(scrubbed) }
    }
  }

  fun containsMedicationReference(
    text: String,
    language: SupportedExplanationLanguage
  ): Boolean {
    val normalized = normalizeForMatching(text)
    return activeRegexPacksFor(language).any { pack ->
      pack.medicationReferenceRegex.containsMatchIn(normalized)
    }
  }

  fun splitClausesForNarrative(
    text: String,
    language: SupportedExplanationLanguage
  ): List<String> {
    val normalized = normalizeForMatching(text)
    val packs = activeRegexPacksFor(language)
    var clauses = listOf(normalized)
    for (pack in packs) {
      clauses = clauses.flatMap { segment -> segment.split(pack.clauseSplitRegex) }
    }
    return clauses
  }
}
