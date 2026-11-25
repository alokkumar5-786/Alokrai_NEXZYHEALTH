package com.example.model

enum class TestStatus(val label: String) {
  NORMAL("Within Expected Range"),
  ATTENTION("Slightly Below Range"),
  REVIEW_NEEDED("Needs Discussion With Doctor"),
  INFORMATIONAL("General Reference")
}

data class TestParameter(
  val id: String,
  val name: String,
  val code: String,
  val value: String,
  val unit: String,
  val referenceRange: String,
  val status: TestStatus,
  val simpleMetaphor: String,
  val simpleExplanation: String,
  val doctorQuestion: String
)

data class SampleReportData(
  val reportId: String = "REP-2026-CBC-091",
  val reportName: String = "Complete Blood Count (CBC) & Metabolic Panel",
  val date: String = "04 Nov 2026",
  val labName: String = "Apex Diagnostics & Wellness Lab",
  val patientName: String = "Priya Sharma (Self)",
  val primaryTest: TestParameter,
  val allParameters: List<TestParameter>,
  val audioTranscript: String,
  val audioDurationSeconds: Int = 145
)

data class LanguageOption(
  val code: String,
  val englishName: String,
  val nativeName: String,
  val isSupported: Boolean,
  val subtitle: String
)

data class FamilyMember(
  val id: String,
  val name: String,
  val relation: String,
  val age: Int,
  val gender: String,
  val conditionNotes: String,
  val reportCount: Int
)

data class ReportHistoryItem(
  val id: String,
  val title: String,
  val date: String,
  val labName: String,
  val patientName: String,
  val category: String,
  val mainHighlight: String,
  val status: TestStatus,
  val isDemo: Boolean = false,
  val expiresAtDisplay: String? = null,
  val retentionStatusLabel: String? = null,
  val isPendingDeletion: Boolean = false,
  val canDelete: Boolean = false
)

data class FaqItem(
  val question: String,
  val answer: String
)

data class SubscriptionPlan(
  val id: String,
  val name: String,
  val priceDisplay: String,
  val billingCycle: String,
  val description: String,
  val features: List<String>,
  val isPopular: Boolean = false,
  val isCurrent: Boolean = false
)

data class UserProfile(
  val uid: String = "",
  val email: String = "",
  val emailVerified: Boolean = false,
  val phoneNumber: String = "",
  val phoneVerified: Boolean = false,
  val displayName: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val accountStatus: String = "ACTIVE",
  val preferredLanguage: String = "en",
  val preferredLanguageSelected: Boolean = false
)
