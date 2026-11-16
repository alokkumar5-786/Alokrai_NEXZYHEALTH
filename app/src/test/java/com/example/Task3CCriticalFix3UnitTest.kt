package com.example

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.domain.engine.DocumentExtractor
import com.example.model.DocumentExtractionResult
import com.example.model.DocumentExtractionState
import com.example.model.DocumentPreviewState
import com.example.model.ExtractedLabItem
import com.example.model.ExtractedLaboratoryReport
import com.example.model.FieldProvenance
import com.example.model.FindingStatus
import com.example.model.MedicalDocumentCategory
import com.example.model.ProvenanceAvailability
import com.example.model.UniversalMedicalReport
import com.example.ui.screens.ExtractionResultScreen
import com.example.ui.screens.UploadProgressScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.upload.AiConsentStore
import com.example.ui.upload.DocumentContentReader
import com.example.ui.upload.DocumentPreviewLoader
import com.example.ui.upload.ReportUploadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Task3CCriticalFix3UnitTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class InMemoryConsentStore(initialConsent: Boolean = false) : AiConsentStore {
    private var consented = initialConsent
    override fun hasGrantedAiConsent(): Boolean = consented
    override fun setAiConsentGranted(granted: Boolean) {
      consented = granted
    }
  }

  private class FakeDocumentContentReader : DocumentContentReader {
    override suspend fun readPdfBytes(uriString: String): Result<ByteArray> =
      Result.success("%PDF-1.4 fake-medical-report-$uriString".toByteArray())

    override suspend fun decodeBitmap(uriString: String): Result<Bitmap> =
      Result.success(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
  }

  private class FakeDocumentExtractor(
    private val handler: suspend (callIndex: Int) -> Result<DocumentExtractionResult>
  ) : DocumentExtractor {
    var callCount: Int = 0
      private set

    override suspend fun extractFromPdfBytes(
      pdfBytes: ByteArray,
      pageCount: Int
    ): Result<DocumentExtractionResult> {
      callCount += 1
      return handler(callCount)
    }

    override suspend fun extractFromBitmaps(
      pageBitmaps: List<Bitmap>
    ): Result<DocumentExtractionResult> {
      callCount += 1
      return handler(callCount)
    }
  }

  private fun createRealThyroidExtraction(): DocumentExtractionResult = DocumentExtractionResult(
    category = MedicalDocumentCategory.LABORATORY,
    pageCount = 2,
    isMultiPageBatch = true,
    report = UniversalMedicalReport.Laboratory(
      ExtractedLaboratoryReport(
        labFacilityName = "Metro Endocrine Lab",
        collectionDate = "2026-11-10",
        reportDate = "2026-11-11",
        items = listOf(
          ExtractedLabItem(
            originalName = "TSH Receptor Antibody",
            originalValue = "3.85",
            originalUnit = "IU/L",
            referenceRange = "< 1.75",
            laboratoryFlag = "H",
            findingStatus = FindingStatus.ABNORMAL,
            provenance = FieldProvenance(
              pageNumber = 2,
              sourceSnippet = "TSH Receptor Antibody 3.85 IU/L (< 1.75) H",
              estimatedExtractionConfidence = 0.94f,
              availability = ProvenanceAvailability.AVAILABLE
            )
          )
        )
      )
    )
  )

  private class FakeDocumentPreviewLoader : DocumentPreviewLoader {
    override suspend fun loadPreview(uri: Uri, isPdf: Boolean, pageIndex: Int): DocumentPreviewState =
      DocumentPreviewState.Ready(
        isPdf = isPdf,
        pageCount = if (isPdf) 2 else 1,
        currentPage = pageIndex + 1,
        pageInfo = if (isPdf) "Page ${pageIndex + 1} of 2" else "Single Image",
        bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888),
        width = 16,
        height = 16
      )
  }

  private fun createViewModel(
    consentStore: AiConsentStore,
    extractor: DocumentExtractor
  ): ReportUploadViewModel {
    val app = ApplicationProvider.getApplicationContext<Application>()
    return ReportUploadViewModel(
      application = app,
      extractor = extractor,
      consentStore = consentStore,
      contentReader = FakeDocumentContentReader(),
      previewLoader = FakeDocumentPreviewLoader()
    )
  }

  @Test
  fun verifySuccessfulExtractionUpdatesStateAndTriggersResultNavigation() {
    val realResult = createRealThyroidExtraction()
    val consentStore = InMemoryConsentStore(initialConsent = true)
    val extractor = FakeDocumentExtractor { Result.success(realResult) }
    val viewModel = createViewModel(consentStore, extractor)

    viewModel.onDocumentMetadataProvided(
      fileName = "Thyroid_Panel_2026.pdf",
      mimeType = "application/pdf",
      sizeBytes = 256_000L,
      uriString = "content://nexzy/doc/thyroid"
    )

    var callbackResult: DocumentExtractionResult? = null
    viewModel.requestExtraction(onSuccess = { callbackResult = it })

    val state = viewModel.uiState.value
    assertFalse(state.isExtracting)
    assertTrue(state.extractionState is DocumentExtractionState.Success)
    assertEquals(realResult, state.extractionResult)
    assertEquals(realResult, callbackResult)

    // Verify UploadProgressScreen triggers navigation to result screen ONLY on Success
    var navigatedResult: DocumentExtractionResult? = null
    composeTestRule.setContent {
      MyApplicationTheme {
        UploadProgressScreen(
          fileName = state.selectedDocument?.fileName ?: "",
          extractionState = state.extractionState,
          onExtractionSucceeded = { navigatedResult = it },
          onInspectUnidentified = {},
          onRetryClick = {},
          onCancelClick = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    assertEquals(realResult, navigatedResult)
  }

  @Test
  fun verifyExtractionResultScreenDisplaysRealDataInsteadOfMockData() {
    val realResult = createRealThyroidExtraction()

    composeTestRule.setContent {
      MyApplicationTheme {
        ExtractionResultScreen(
          extractionResult = realResult,
          fileName = "Thyroid_Panel_2026.pdf",
          patientName = "Ramesh Kumar",
          onUploadAnotherClick = {},
          onNavigateBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    // Real extracted values must be displayed (scroll within LazyColumn for items below fold)
    val listNode = composeTestRule.onNodeWithTag("extraction_result_list")
    listNode.performScrollToNode(hasText("Metro Endocrine Lab"))
    composeTestRule.onNodeWithText("Metro Endocrine Lab").assertIsDisplayed()

    listNode.performScrollToNode(hasText("TSH Receptor Antibody"))
    composeTestRule.onNodeWithText("TSH Receptor Antibody").assertIsDisplayed()

    listNode.performScrollToNode(hasText("3.85 IU/L"))
    composeTestRule.onNodeWithText("3.85 IU/L").assertIsDisplayed()

    listNode.performScrollToNode(hasText("Page 2 • AI extraction confidence: 94%"))
    composeTestRule.onNodeWithText("Page 2 • AI extraction confidence: 94%").assertIsDisplayed()

    // MockDataProvider.sampleReport demo text must NOT be displayed
    composeTestRule.onNodeWithText("DEMO DATA", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Hemoglobin (Hb)", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("11.2 g/dL", substring = true).assertDoesNotExist()
  }

  @Test
  fun verifyPreviouslyGrantedConsentDoesNotSkipExtractionForNewUpload() {
    val firstResult = createRealThyroidExtraction()
    val secondResult = DocumentExtractionResult(
      category = MedicalDocumentCategory.LABORATORY,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Laboratory(
        ExtractedLaboratoryReport(
          labFacilityName = "City Lipid Diagnostics",
          collectionDate = null,
          reportDate = null,
          items = listOf(
            ExtractedLabItem(
              originalName = "LDL Cholesterol",
              originalValue = "148",
              originalUnit = "mg/dL",
              referenceRange = "< 100",
              laboratoryFlag = "H",
              findingStatus = FindingStatus.ABNORMAL,
              provenance = FieldProvenance(
                pageNumber = 1,
                sourceSnippet = "LDL Cholesterol 148 mg/dL",
                estimatedExtractionConfidence = 0.93f,
                availability = ProvenanceAvailability.AVAILABLE
              )
            )
          )
        )
      )
    )

    // Pre-existing consent stored from an earlier session
    val consentStore = InMemoryConsentStore(initialConsent = true)
    val extractor = FakeDocumentExtractor { callIndex ->
      if (callIndex == 1) Result.success(firstResult) else Result.success(secondResult)
    }
    val viewModel = createViewModel(consentStore, extractor)

    assertTrue(viewModel.uiState.value.hasGivenAiConsent)

    // First upload proceeds immediately without showing consent dialog
    viewModel.onDocumentMetadataProvided(
      fileName = "Thyroid_1.pdf",
      mimeType = "application/pdf",
      sizeBytes = 120_000L,
      uriString = "content://nexzy/doc/1"
    )
    viewModel.requestExtraction()
    assertFalse(viewModel.uiState.value.isConsentDialogVisible)
    assertEquals(1, extractor.callCount)
    assertEquals(firstResult, viewModel.uiState.value.extractionResult)

    // Second upload MUST ALSO run extraction instead of skipping or reusing first result
    viewModel.onDocumentMetadataProvided(
      fileName = "Lipid_2.pdf",
      mimeType = "application/pdf",
      sizeBytes = 140_000L,
      uriString = "content://nexzy/doc/2"
    )
    assertNull(viewModel.uiState.value.extractionResult)
    assertTrue(viewModel.uiState.value.hasGivenAiConsent)

    viewModel.requestExtraction()
    assertFalse(viewModel.uiState.value.isConsentDialogVisible)
    assertEquals(2, extractor.callCount)
    assertEquals(secondResult, viewModel.uiState.value.extractionResult)
  }

  @Test
  fun verifyConsentIsRequiredBeforeFirstExtractionAndGuardsDirectCalls() {
    val realResult = createRealThyroidExtraction()
    val consentStore = InMemoryConsentStore(initialConsent = false)
    val extractor = FakeDocumentExtractor { Result.success(realResult) }
    val viewModel = createViewModel(consentStore, extractor)

    viewModel.onDocumentMetadataProvided(
      fileName = "First_Upload.pdf",
      mimeType = "application/pdf",
      sizeBytes = 200_000L,
      uriString = "content://nexzy/doc/first"
    )

    // Requesting extraction without consent opens dialog and does NOT process
    viewModel.requestExtraction()
    assertTrue(viewModel.uiState.value.isConsentDialogVisible)
    assertTrue(viewModel.uiState.value.extractionState is DocumentExtractionState.WaitingForConsent)
    assertEquals(0, extractor.callCount)
    assertNull(viewModel.uiState.value.extractionResult)

    // Direct call to startDocumentExtraction without consent must also refuse processing
    viewModel.startDocumentExtraction()
    assertEquals(0, extractor.callCount)
    assertFalse(consentStore.hasGrantedAiConsent())

    // Confirming consent stores consent and launches extraction
    var startedCalled = false
    viewModel.confirmConsentAndStartExtraction(onStarted = { startedCalled = true })
    assertTrue(startedCalled)
    assertTrue(consentStore.hasGrantedAiConsent())
    assertTrue(viewModel.uiState.value.hasGivenAiConsent)
    assertFalse(viewModel.uiState.value.isConsentDialogVisible)
    assertEquals(1, extractor.callCount)
    assertEquals(realResult, viewModel.uiState.value.extractionResult)
  }

  @Test
  fun verifyFailedAndUnidentifiedExtractionNeverNavigateToSuccessResult() {
    val consentStore = InMemoryConsentStore(initialConsent = true)
    val failingExtractor = FakeDocumentExtractor {
      Result.failure(IOException("Firebase AI network timeout"))
    }
    val viewModel = createViewModel(consentStore, failingExtractor)

    viewModel.onDocumentMetadataProvided(
      fileName = "Broken_Report.pdf",
      mimeType = "application/pdf",
      sizeBytes = 180_000L,
      uriString = "content://nexzy/doc/broken"
    )

    var successCalled = false
    viewModel.requestExtraction(onSuccess = { successCalled = true })

    val failureState = viewModel.uiState.value
    assertFalse(successCalled)
    assertNull(failureState.extractionResult)
    assertTrue(failureState.extractionState is DocumentExtractionState.Failure)
    assertEquals("Firebase AI network timeout", failureState.errorMessage)

    // Progress UI shows Retry & Failure card and NEVER invokes onExtractionSucceeded
    var uiNavigatedToSuccess = false
    composeTestRule.setContent {
      MyApplicationTheme {
        UploadProgressScreen(
          fileName = "Broken_Report.pdf",
          extractionState = failureState.extractionState,
          onExtractionSucceeded = { uiNavigatedToSuccess = true },
          onInspectUnidentified = {},
          onRetryClick = {},
          onCancelClick = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    assertFalse(uiNavigatedToSuccess)
    composeTestRule.onNodeWithTag("progress_error_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_retry_extraction").assertIsDisplayed()

    // Unidentified document also must NOT trigger Success navigation
    val unidentifiedResult = DocumentExtractionResult(
      category = MedicalDocumentCategory.UNCERTAIN,
      pageCount = 1,
      isMultiPageBatch = false,
      report = UniversalMedicalReport.Unidentified(
        rawSnippet = "Monthly Electricity Invoice",
        reason = "Document is not a medical report."
      )
    )
    val unidentifiedExtractor = FakeDocumentExtractor { Result.success(unidentifiedResult) }
    val vm2 = createViewModel(consentStore, unidentifiedExtractor)
    vm2.onDocumentMetadataProvided("Invoice.pdf", "application/pdf", 50_000L, "content://nexzy/doc/inv")
    var unidentifiedSuccessCallback = false
    vm2.requestExtraction(onSuccess = { unidentifiedSuccessCallback = true })
    assertFalse(unidentifiedSuccessCallback)
    assertTrue(vm2.uiState.value.extractionState is DocumentExtractionState.Unidentified)
  }

  @Test
  fun verifyNullableFieldsAndProvenanceRemainUnchangedInNavigationAndUi() {
    val nullableResult = DocumentExtractionResult(
      category = MedicalDocumentCategory.LABORATORY,
      pageCount = 2,
      isMultiPageBatch = true,
      report = UniversalMedicalReport.Laboratory(
        ExtractedLaboratoryReport(
          labFacilityName = null,
          collectionDate = null,
          reportDate = null,
          items = listOf(
            ExtractedLabItem(
              originalName = "Serum Copper",
              originalValue = null,
              originalUnit = null,
              referenceRange = null,
              laboratoryFlag = null,
              findingStatus = FindingStatus.NOT_MENTIONED,
              provenance = FieldProvenance.unavailable()
            ),
            ExtractedLabItem(
              originalName = "Free T4",
              originalValue = "1.4",
              originalUnit = "ng/dL",
              referenceRange = "0.8 - 1.8",
              laboratoryFlag = null,
              findingStatus = FindingStatus.NORMAL,
              provenance = FieldProvenance(
                pageNumber = 2,
                sourceSnippet = "Free T4 1.4 ng/dL",
                estimatedExtractionConfidence = null,
                availability = ProvenanceAvailability.PARTIALLY_AVAILABLE
              )
            )
          )
        )
      )
    )

    val consentStore = InMemoryConsentStore(initialConsent = true)
    val extractor = FakeDocumentExtractor { Result.success(nullableResult) }
    val viewModel = createViewModel(consentStore, extractor)

    viewModel.onDocumentMetadataProvided(
      fileName = "Sparse_Report.pdf",
      mimeType = "application/pdf",
      sizeBytes = 90_000L,
      uriString = "content://nexzy/doc/sparse"
    )
    viewModel.requestExtraction()

    val storedResult = viewModel.uiState.value.extractionResult!!
    val storedLab = (storedResult.report as UniversalMedicalReport.Laboratory).report
    assertNull(storedLab.items[0].originalValue)
    assertNull(storedLab.items[0].provenance.pageNumber)
    assertNull(storedLab.items[0].provenance.estimatedExtractionConfidence)
    assertEquals(ProvenanceAvailability.UNAVAILABLE, storedLab.items[0].provenance.availability)

    assertEquals(2, storedLab.items[1].provenance.pageNumber)
    assertNull(storedLab.items[1].provenance.estimatedExtractionConfidence)
    assertEquals(ProvenanceAvailability.PARTIALLY_AVAILABLE, storedLab.items[1].provenance.availability)

    composeTestRule.setContent {
      MyApplicationTheme {
        ExtractionResultScreen(
          extractionResult = storedResult,
          fileName = "Sparse_Report.pdf",
          patientName = "Priya Sharma",
          onUploadAnotherClick = {},
          onNavigateBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    // Nullable values stay explicitly unreported (never "N/A", page 1, or 95%)
    val listNode = composeTestRule.onNodeWithTag("extraction_result_list")
    listNode.performScrollToNode(hasText("Serum Copper"))
    composeTestRule.onNodeWithText("Serum Copper").assertIsDisplayed()

    listNode.performScrollToNode(hasText("Provenance: Unavailable (not provided in document extraction)"))
    composeTestRule.onNodeWithText("Provenance: Unavailable (not provided in document extraction)").assertIsDisplayed()

    listNode.performScrollToNode(hasText("Page 2 • AI confidence unavailable"))
    composeTestRule.onNodeWithText("Page 2 • AI confidence unavailable").assertIsDisplayed()

    composeTestRule.onNodeWithText("N/A").assertDoesNotExist()
    composeTestRule.onNodeWithText("95%", substring = true).assertDoesNotExist()
  }
}
