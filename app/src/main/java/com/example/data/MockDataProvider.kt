package com.example.data

import com.example.model.FaqItem
import com.example.model.FamilyMember
import com.example.model.LanguageOption
import com.example.model.ReportHistoryItem
import com.example.model.SampleReportData
import com.example.model.SubscriptionPlan
import com.example.model.TestParameter
import com.example.model.TestStatus

object MockDataProvider {

  val sampleLanguages = listOf(
    LanguageOption(
      code = "en",
      englishName = "English",
      nativeName = "English",
      isSupported = true,
      subtitle = "Available now • High accuracy"
    ),
    LanguageOption(
      code = "hi",
      englishName = "Hindi",
      nativeName = "हिन्दी",
      isSupported = true,
      subtitle = "उपलब्ध है • Available now"
    ),
    LanguageOption(
      code = "bn",
      englishName = "Bengali",
      nativeName = "বাংলা",
      isSupported = true,
      subtitle = "উপলব্ধ আছে • Available now"
    ),
    LanguageOption(
      code = "ta",
      englishName = "Tamil",
      nativeName = "தமிழ்",
      isSupported = false,
      subtitle = "விரைவில் வரும் • Planned release"
    ),
    LanguageOption(
      code = "mr",
      englishName = "Marathi",
      nativeName = "मराठी",
      isSupported = true,
      subtitle = "उपलब्ध आहे • Available now"
    ),
    LanguageOption(
      code = "pa",
      englishName = "Punjabi",
      nativeName = "ਪੰਜਾਬੀ",
      isSupported = false,
      subtitle = "ਜਲਦੀ ਆ ਰਿਹਾ ਹੈ • Planned release"
    ),
    LanguageOption(
      code = "gu",
      englishName = "Gujarati",
      nativeName = "ગુજરાતી",
      isSupported = false,
      subtitle = "ટૂંક સમયમાં • Planned release"
    )
  )

  val primarySampleTest = TestParameter(
    id = "hb-1",
    name = "Hemoglobin (Hb)",
    code = "HGB",
    value = "11.2",
    unit = "g/dL",
    referenceRange = "12.0 - 15.5 g/dL",
    status = TestStatus.ATTENTION,
    simpleMetaphor = "🚚 Tiny oxygen delivery trucks",
    simpleExplanation = "Think of hemoglobin as tiny delivery vans carrying fresh oxygen through your body. Right now, you have slightly fewer delivery vans than usual. This is very common and often just means your body needs a bit more iron, like leafy greens, lentils, or dates.",
    doctorQuestion = "Should I take a gentle iron supplement or adjust my daily diet? Could my afternoon tiredness be related to this?"
  )

  val sampleParametersList = listOf(
    primarySampleTest,
    TestParameter(
      id = "glu-2",
      name = "Fasting Blood Glucose",
      code = "FBG",
      value = "94",
      unit = "mg/dL",
      referenceRange = "70 - 99 mg/dL",
      status = TestStatus.NORMAL,
      simpleMetaphor = "⚡ Fuel in your bloodstream",
      simpleExplanation = "This tells us how much natural energy sugar is traveling in your blood after sleeping overnight. Your score is 94, which fits comfortably within the healthy normal zone.",
      doctorQuestion = "Do I need to repeat fasting sugar annually?"
    ),
    TestParameter(
      id = "chol-3",
      name = "Total Cholesterol",
      code = "CHOL-TOT",
      value = "185",
      unit = "mg/dL",
      referenceRange = "125 - 200 mg/dL",
      status = TestStatus.NORMAL,
      simpleMetaphor = "🧈 Gentle cell building blocks",
      simpleExplanation = "Cholesterol helps your body build healthy cell walls. Your total level is 185, which is safely under the 200 limit that doctors like to see.",
      doctorQuestion = "Should I also check my good (HDL) and bad (LDL) cholesterol numbers?"
    ),
    TestParameter(
      id = "vitd-4",
      name = "Vitamin D (25-OH)",
      code = "VIT-D3",
      value = "18",
      unit = "ng/mL",
      referenceRange = "30 - 100 ng/mL",
      status = TestStatus.ATTENTION,
      simpleMetaphor = "☀️ Liquid sunshine for bones",
      simpleExplanation = "Vitamin D acts like a key that locks calcium firmly into your bones and helps your mood. Your score is 18, which is lower than recommended. Most adults in India have low levels due to indoor work.",
      doctorQuestion = "Would a weekly vitamin D3 sachet or capsule be helpful for my bone health?"
    ),
    TestParameter(
      id = "tsh-5",
      name = "Thyroid Stimulating Hormone (TSH)",
      code = "TSH",
      value = "2.4",
      unit = "µIU/mL",
      referenceRange = "0.4 - 4.2 µIU/mL",
      status = TestStatus.NORMAL,
      simpleMetaphor = "⏱️ Body's master speed governor",
      simpleExplanation = "Your thyroid sets the pace of how fast your body burns energy. A score of 2.4 shows your thyroid thermostat is working at a steady, balanced speed.",
      doctorQuestion = "Everything looks steady. Does any thyroid follow-up apply for me?"
    ),
    TestParameter(
      id = "plt-6",
      name = "Platelet Count",
      code = "PLT",
      value = "240",
      unit = "x10³/µL",
      referenceRange = "150 - 450 x10³/µL",
      status = TestStatus.NORMAL,
      simpleMetaphor = "🩹 Natural tiny bandages",
      simpleExplanation = "Platelets are microscopic sealants that plug tiny cuts or scrapes so you heal quickly. Your count of 240,000 is healthy and strong.",
      doctorQuestion = "Platelets are normal. No questions needed."
    )
  )

  val sampleReport = SampleReportData(
    primaryTest = primarySampleTest,
    allParameters = sampleParametersList,
    audioTranscript = "Hello Priya. Welcome to your NexzyHealth audio report summary. Let's look at your Complete Blood Count. Overall, your body is doing well. Your blood sugar and thyroid are in a very good place. The main thing your doctor will likely discuss is your Hemoglobin, which is at 11.2, slightly under the usual 12.0 benchmark. Imagine hemoglobin as tiny delivery vans carrying oxygen to your muscles and brain. When you have a few less, you might feel a little tired or sleepy in the afternoons. It is very common and easily helped with iron-rich foods like spinach, beetroots, lentils, or a doctor-recommended supplement. Be sure to ask your doctor if you should repeat this test in three months."
  )

  val sampleHistory = listOf(
    ReportHistoryItem(
      id = "rep-001",
      title = "Complete Blood Count & Metabolic Panel",
      date = "04 Nov 2026",
      labName = "Apex Diagnostics Lab",
      patientName = "Priya Sharma (Self)",
      category = "Blood Test",
      mainHighlight = "Hemoglobin 11.2 g/dL (Mild attention)",
      status = TestStatus.ATTENTION,
      isDemo = true
    ),
    ReportHistoryItem(
      id = "rep-002",
      title = "Annual Lipid & Heart Health Checkup",
      date = "18 Oct 2026",
      labName = "Dr. Lal PathLabs",
      patientName = "Ramesh Kumar (Father)",
      category = "Lipid Profile",
      mainHighlight = "Cholesterol & Triglycerides within safe range",
      status = TestStatus.NORMAL
    ),
    ReportHistoryItem(
      id = "rep-003",
      title = "Thyroid Function Test (T3, T4, TSH)",
      date = "02 Sep 2026",
      labName = "Thyrocare Technologies",
      patientName = "Sunita Kumar (Mother)",
      category = "Thyroid",
      mainHighlight = "TSH 3.8 µIU/mL (Stable under medication)",
      status = TestStatus.NORMAL
    ),
    ReportHistoryItem(
      id = "rep-004",
      title = "HbA1c & Fasting Glucose Screening",
      date = "15 Jul 2026",
      labName = "SRL Diagnostics",
      patientName = "Ramesh Kumar (Father)",
      category = "Diabetes Care",
      mainHighlight = "HbA1c 6.4% (Pre-diabetes monitoring)",
      status = TestStatus.ATTENTION
    )
  )

  val sampleFamilyMembers = listOf(
    FamilyMember(
      id = "fam-1",
      name = "Priya Sharma",
      relation = "Self (Account Owner)",
      age = 34,
      gender = "Female",
      conditionNotes = "General wellness, mild iron deficiency",
      reportCount = 6
    ),
    FamilyMember(
      id = "fam-2",
      name = "Ramesh Kumar",
      relation = "Father",
      age = 68,
      gender = "Male",
      conditionNotes = "Mild hypertension, annual diabetic checkup",
      reportCount = 5
    ),
    FamilyMember(
      id = "fam-3",
      name = "Sunita Kumar",
      relation = "Mother",
      age = 64,
      gender = "Female",
      conditionNotes = "Hypothyroidism on daily levothyroxine",
      reportCount = 3
    )
  )

  val sampleFaqs = listOf(
    FaqItem(
      question = "How does NexzyHealth make medical reports easy to understand?",
      answer = "Medical reports are filled with complex clinical terms like 'microcytic hypochromia' or 'euthyroid'. NexzyHealth translates these into friendly, 12-year-old reading level analogies (like 'tiny delivery trucks for oxygen') so you and your family can make sense of the results before seeing your physician."
    ),
    FaqItem(
      question = "Is NexzyHealth a replacement for my doctor?",
      answer = "No. NexzyHealth is strictly an educational tool to empower you with clarity. We never diagnose illnesses or prescribe medication. We prepare you with smart, targeted questions to ask your doctor during your appointment."
    ),
    FaqItem(
      question = "What report formats can I upload or scan?",
      answer = "You can take a photo of printed lab paper sheets directly using your phone's camera, or upload digital PDF files, JPGs, or PNGs from your device."
    ),
    FaqItem(
      question = "Can I manage reports for my elderly parents?",
      answer = "Yes! NexzyHealth is built specifically for adult family caregivers. You can create separate family profiles for your parents, children, or spouse and organize their reports individually."
    ),
    FaqItem(
      question = "Is my health data private and secure?",
      answer = "Absolutely. We adhere to zero-data-selling principles. Your reports are encrypted, never sold to insurance companies or advertisers, and you can permanently delete all data at any time from the Privacy screen."
    )
  )

  val subscriptionPlans = listOf(
    SubscriptionPlan(
      id = "free",
      name = "Starter Free",
      priceDisplay = "₹0",
      billingCycle = "Free Forever",
      description = "Ideal for occasional personal checkups",
      features = listOf(
        "3 free report explanations per month",
        "English, Hindi, Bengali & Marathi support",
        "Key test parameter breakdown",
        "Doctor question suggestions",
        "Standard processing"
      ),
      isCurrent = true
    ),
    SubscriptionPlan(
      id = "plus",
      name = "Nexzy Plus",
      priceDisplay = "₹199",
      billingCycle = "per month",
      description = "For individuals who need continuous health insights",
      features = listOf(
        "Unlimited medical report scans & uploads",
        "All 7 regional Indian languages (as released)",
        "Warm female audio voice explanations",
        "Complete doctor question guide",
        "Downloadable simplified PDF summaries"
      ),
      isPopular = true
    ),
    SubscriptionPlan(
      id = "family",
      name = "Family Caregiver",
      priceDisplay = "₹399",
      billingCycle = "per month",
      description = "Designed for adult caregivers managing aging parents",
      features = listOf(
        "Everything in Nexzy Plus",
        "Up to 6 dedicated family member profiles",
        "Medication & trend tracking comparisons",
        "Priority customer & caregiver support",
        "Offline audio downloads to share via WhatsApp"
      )
    )
  )
}
