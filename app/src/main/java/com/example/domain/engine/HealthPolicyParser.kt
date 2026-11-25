package com.example.domain.engine

import com.example.model.HealthPolicyCopy
import com.example.model.HealthPolicyExplanation
import com.example.model.HowToClaim
import com.example.model.MoneyYouPay
import com.example.model.PolicyBasics
import com.example.model.PolicyErrorType
import com.example.model.PolicyGlossaryTerm
import com.example.model.PolicyRedFlag
import com.example.model.RedFlagSeverity
import com.example.model.SupportedExplanationLanguage
import com.example.model.WaitingPeriodItem
import org.json.JSONArray
import org.json.JSONObject

sealed class PolicyParseOutcome {
  data class Success(val explanation: HealthPolicyExplanation) : PolicyParseOutcome()
  data class DomainError(val errorType: PolicyErrorType) : PolicyParseOutcome()
}

/**
 * Parses and validates the JSON payload returned by Gemini for "Explain My Health Policy",
 * and formats safe plain-text share summaries without retaining raw policy source files.
 */
object HealthPolicyParser {

  fun parseResponse(
    rawJson: String,
    languageCode: String
  ): PolicyParseOutcome {
    val lang = SupportedExplanationLanguage.fromCodeOrName(languageCode)
      ?: SupportedExplanationLanguage.ENGLISH
    val copy = HealthPolicyCopy.forLanguage(lang.code)
    val cleaned = stripCodeFences(rawJson)
    if (cleaned.isBlank()) {
      return PolicyParseOutcome.DomainError(PolicyErrorType.UNREADABLE)
    }

    val jsonText = extractJsonObjectBounds(cleaned)
      ?: return PolicyParseOutcome.DomainError(PolicyErrorType.GENERIC)

    val root = try {
      JSONObject(jsonText)
    } catch (_: Exception) {
      return PolicyParseOutcome.DomainError(PolicyErrorType.GENERIC)
    }

    val errorValue = root.optString("error", "").trim().lowercase()
    if (errorValue.isNotEmpty() && !root.isNull("error")) {
      return when (errorValue) {
        "not_a_health_policy" -> PolicyParseOutcome.DomainError(PolicyErrorType.NOT_A_HEALTH_POLICY)
        "unreadable" -> PolicyParseOutcome.DomainError(PolicyErrorType.UNREADABLE)
        else -> PolicyParseOutcome.DomainError(PolicyErrorType.GENERIC)
      }
    }

    val hasCoreSections = root.has("policy_basics") ||
      root.has("simple_summary") ||
      root.has("what_is_covered")
    if (!hasCoreSections) {
      return PolicyParseOutcome.DomainError(PolicyErrorType.GENERIC)
    }

    val fallback = copy.notMentionedFallback

    val basicsObj = root.optJSONObject("policy_basics") ?: JSONObject()
    val policyBasics = PolicyBasics(
      insurerName = basicsObj.safeString("insurer_name", fallback),
      planName = basicsObj.safeString("plan_name", fallback),
      policyType = basicsObj.safeString("policy_type", fallback),
      sumInsured = basicsObj.safeString("sum_insured", fallback),
      premium = basicsObj.safeString("premium", fallback),
      policyPeriod = basicsObj.safeString("policy_period", fallback),
      membersCovered = basicsObj.safeString("members_covered", fallback)
    )

    val simpleSummary = root.safeString("simple_summary", fallback)
    val whatIsCovered = root.safeStringList("what_is_covered")
    val whatIsNotCovered = root.safeStringList("what_is_not_covered")

    val waitingPeriods = mutableListOf<WaitingPeriodItem>()
    val wpArr = root.optJSONArray("waiting_periods") ?: JSONArray()
    for (i in 0 until wpArr.length()) {
      val itemObj = wpArr.optJSONObject(i) ?: continue
      val itemTitle = itemObj.safeString("item", "")
      val duration = itemObj.safeString("duration", fallback)
      val meaning = itemObj.safeString("meaning_in_simple_words", fallback)
      if (itemTitle.isNotBlank() || meaning != fallback) {
        waitingPeriods.add(
          WaitingPeriodItem(
            item = itemTitle.ifBlank { fallback },
            duration = duration,
            meaningInSimpleWords = meaning
          )
        )
      }
    }

    val moneyObj = root.optJSONObject("money_you_pay") ?: JSONObject()
    val moneyYouPay = MoneyYouPay(
      coPayment = moneyObj.safeString("co_payment", fallback),
      deductible = moneyObj.safeString("deductible", fallback),
      roomRentLimit = moneyObj.safeString("room_rent_limit", fallback),
      subLimits = moneyObj.safeString("sub_limits", fallback),
      nonPayableItemsNote = moneyObj.safeString("non_payable_items_note", fallback)
    )

    val claimObj = root.optJSONObject("how_to_claim") ?: JSONObject()
    val howToClaim = HowToClaim(
      cashlessSteps = claimObj.safeStringList("cashless_steps"),
      reimbursementSteps = claimObj.safeStringList("reimbursement_steps"),
      documentsNeeded = claimObj.safeStringList("documents_needed"),
      timeLimits = claimObj.safeString("time_limits", fallback)
    )

    val redFlags = mutableListOf<PolicyRedFlag>()
    val rfArr = root.optJSONArray("red_flags") ?: JSONArray()
    for (i in 0 until rfArr.length()) {
      val rfObj = rfArr.optJSONObject(i) ?: continue
      val title = rfObj.safeString("title", "")
      val explanationText = rfObj.safeString("simple_explanation", "")
      if (title.isNotBlank() || explanationText.isNotBlank()) {
        redFlags.add(
          PolicyRedFlag(
            severity = RedFlagSeverity.fromString(rfObj.optString("severity", "low")),
            title = title.ifBlank { copy.redFlagsTitle },
            simpleExplanation = explanationText.ifBlank { fallback }
          )
        )
      }
    }
    // Sort HIGH -> MEDIUM -> LOW for quick visibility
    val sortedRedFlags = redFlags.sortedBy {
      when (it.severity) {
        RedFlagSeverity.HIGH -> 0
        RedFlagSeverity.MEDIUM -> 1
        RedFlagSeverity.LOW -> 2
      }
    }

    val questions = root.safeStringList("questions_to_ask_insurer")

    val glossary = mutableListOf<PolicyGlossaryTerm>()
    val gArr = root.optJSONArray("plain_language_glossary") ?: JSONArray()
    for (i in 0 until gArr.length()) {
      val gObj = gArr.optJSONObject(i) ?: continue
      val term = gObj.safeString("term", "")
      val meaning = gObj.safeString("meaning", "")
      if (term.isNotBlank() && meaning.isNotBlank()) {
        glossary.add(PolicyGlossaryTerm(term = term, meaning = meaning))
      }
    }

    return PolicyParseOutcome.Success(
      HealthPolicyExplanation(
        languageCode = lang.code,
        policyBasics = policyBasics,
        simpleSummary = simpleSummary,
        whatIsCovered = whatIsCovered,
        whatIsNotCovered = whatIsNotCovered,
        waitingPeriods = waitingPeriods,
        moneyYouPay = moneyYouPay,
        howToClaim = howToClaim,
        redFlags = sortedRedFlags,
        questionsToAskInsurer = questions,
        plainLanguageGlossary = glossary
      )
    )
  }

  /**
   * Builds a clean, shareable plain-text summary including localized mandatory disclaimers.
   */
  fun buildShareSummaryText(explanation: HealthPolicyExplanation): String {
    val copy = HealthPolicyCopy.forLanguage(explanation.languageCode)
    val sb = StringBuilder()

    sb.appendLine("NexzyHealth — ${copy.featureTitle}")
    sb.appendLine()
    sb.appendLine(copy.mandatoryDisclaimer)
    sb.appendLine()

    sb.appendLine("=== ${copy.keyFactsTitle} ===")
    sb.appendLine("${copy.insurerLabel}: ${explanation.policyBasics.insurerName}")
    sb.appendLine("${copy.planLabel}: ${explanation.policyBasics.planName}")
    sb.appendLine("${copy.policyTypeLabel}: ${explanation.policyBasics.policyType}")
    sb.appendLine("${copy.sumInsuredLabel}: ${explanation.policyBasics.sumInsured}")
    sb.appendLine("${copy.premiumLabel}: ${explanation.policyBasics.premium}")
    sb.appendLine("${copy.policyPeriodLabel}: ${explanation.policyBasics.policyPeriod}")
    sb.appendLine("${copy.membersCoveredLabel}: ${explanation.policyBasics.membersCovered}")
    sb.appendLine()

    sb.appendLine("=== ${copy.simpleSummaryTitle} ===")
    sb.appendLine(explanation.simpleSummary)
    sb.appendLine()

    if (explanation.redFlags.isNotEmpty()) {
      sb.appendLine("=== ${copy.redFlagsTitle} ===")
      explanation.redFlags.forEach { flag ->
        val sevLabel = when (flag.severity) {
          RedFlagSeverity.HIGH -> copy.severityHigh
          RedFlagSeverity.MEDIUM -> copy.severityMedium
          RedFlagSeverity.LOW -> copy.severityLow
        }
        sb.appendLine("[$sevLabel] ${flag.title}: ${flag.simpleExplanation}")
      }
      sb.appendLine()
    }

    if (explanation.whatIsCovered.isNotEmpty()) {
      sb.appendLine("=== ${copy.coveredTitle} ===")
      explanation.whatIsCovered.forEach { sb.appendLine("✓ $it") }
      sb.appendLine()
    }

    if (explanation.whatIsNotCovered.isNotEmpty()) {
      sb.appendLine("=== ${copy.notCoveredTitle} ===")
      explanation.whatIsNotCovered.forEach { sb.appendLine("✗ $it") }
      sb.appendLine()
    }

    if (explanation.waitingPeriods.isNotEmpty()) {
      sb.appendLine("=== ${copy.waitingPeriodsTitle} ===")
      explanation.waitingPeriods.forEach { wp ->
        sb.appendLine("• ${wp.item} (${wp.duration}): ${wp.meaningInSimpleWords}")
      }
      sb.appendLine()
    }

    sb.appendLine("=== ${copy.moneyYouPayTitle} ===")
    sb.appendLine("${copy.coPaymentLabel}: ${explanation.moneyYouPay.coPayment}")
    sb.appendLine("${copy.deductibleLabel}: ${explanation.moneyYouPay.deductible}")
    sb.appendLine("${copy.roomRentLabel}: ${explanation.moneyYouPay.roomRentLimit}")
    sb.appendLine("${copy.subLimitsLabel}: ${explanation.moneyYouPay.subLimits}")
    sb.appendLine("${copy.nonPayableLabel}: ${explanation.moneyYouPay.nonPayableItemsNote}")
    sb.appendLine()

    if (explanation.questionsToAskInsurer.isNotEmpty()) {
      sb.appendLine("=== ${copy.questionsTitle} ===")
      explanation.questionsToAskInsurer.forEachIndexed { idx, q ->
        sb.appendLine("${idx + 1}. $q")
      }
      sb.appendLine()
    }

    sb.appendLine(copy.mandatoryDisclaimer)
    return sb.toString().trim()
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

  private fun extractJsonObjectBounds(text: String): String? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return text.substring(start, end + 1)
  }

  private fun JSONObject.safeString(key: String, fallback: String): String {
    if (!has(key) || isNull(key)) return fallback
    val value = optString(key, "").trim()
    return if (value.isBlank() || value.equals("null", ignoreCase = true)) fallback else value
  }

  private fun JSONObject.safeStringList(key: String): List<String> {
    val arr = optJSONArray(key) ?: return emptyList()
    val result = mutableListOf<String>()
    for (i in 0 until arr.length()) {
      val item = arr.optString(i, "").trim()
      if (item.isNotBlank() && !item.equals("null", ignoreCase = true)) {
        result.add(item)
      }
    }
    return result
  }
}
