package com.example

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.IntentSender
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.example.model.DocumentPreviewState
import com.example.ui.upload.AiConsentStore
import com.example.ui.upload.AndroidDocumentPreviewLoader
import com.example.ui.upload.DocumentContentReader
import com.example.ui.upload.DocumentScannerHelper
import com.example.ui.upload.ReportUploadViewModel
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import java.io.FileNotFoundException
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MedicalDocumentSafeLoggingUnitTest {

  private lateinit var application: Application
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    application = ApplicationProvider.getApplicationContext()
    ShadowLog.clear()
  }

  @After
  fun tearDown() {
    ShadowLog.clear()
    Dispatchers.resetMain()
  }

  private fun previewAndUploadLogs(): List<ShadowLog.LogItem> =
    ShadowLog.getLogs().filter {
      it.tag == "NexzyPreviewLoader" || it.tag == "NexzyUploadVM" || it.tag == "NexzyScannerHelper"
    }

  private fun joinedMessages(items: List<ShadowLog.LogItem>): String =
    items.joinToString("\n") { "${it.tag}: ${it.msg}" }

  private fun registerFailingProvider(authority: String, provider: FailingContentProvider) {
    val info = ProviderInfo().apply {
      this.authority = authority
      this.packageName = application.packageName
      this.exported = true
    }
    provider.attachInfo(application, info)
    ShadowContentResolver.registerProviderInternal(authority, provider)
  }

  @Test
  fun `preview loader logs only safe class names without uri filename or throwable on pdf errors`() = runTest {
    val secretPdfUri = Uri.parse("content://com.nexzy.test.pdfprovider/raw/Rahul_Sharma_CBC_1990.pdf")
    registerFailingProvider(
      "com.nexzy.test.pdfprovider",
      FailingContentProvider(
        openFileError = { SecurityException("Denied access to $secretPdfUri patient=Rahul_Sharma") }
      )
    )

    val loader = AndroidDocumentPreviewLoader(application)
    val secState = loader.loadPreview(secretPdfUri, isPdf = true, pageIndex = 0)
    assertTrue(secState is DocumentPreviewState.Error)
    assertEquals(
      "This PDF is password-protected. Please select an unencrypted copy.",
      (secState as DocumentPreviewState.Error).message
    )

    val ioPdfUri = Uri.parse("content://com.nexzy.test.pdfioprovider/raw/Meera_Thyroid_Report.pdf")
    registerFailingProvider(
      "com.nexzy.test.pdfioprovider",
      FailingContentProvider(
        openFileError = { FileNotFoundException("Corrupted $ioPdfUri Hemoglobin=9.2") }
      )
    )
    val ioState = loader.loadPreview(ioPdfUri, isPdf = true, pageIndex = 0)
    assertTrue(ioState is DocumentPreviewState.Error)
    assertEquals("Corrupted or unreadable PDF document.", (ioState as DocumentPreviewState.Error).message)

    val runtimePdfUri = Uri.parse("content://com.nexzy.test.pdfruntime/raw/Arjun_Lipid_Panel.pdf")
    registerFailingProvider(
      "com.nexzy.test.pdfruntime",
      FailingContentProvider(
        openFileError = { IllegalStateException("Unexpected crash on $runtimePdfUri LDL=180") }
      )
    )
    val runtimeState = loader.loadPreview(runtimePdfUri, isPdf = true, pageIndex = 0)
    assertTrue(runtimeState is DocumentPreviewState.Error)
    assertTrue((runtimeState as DocumentPreviewState.Error).message.startsWith("Unable to render document preview"))

    val logs = previewAndUploadLogs()
    val text = joinedMessages(logs)
    assertTrue(text.contains("PDF is password-protected or encrypted: SecurityException"))
    assertTrue(text.contains("Corrupted PDF file: FileNotFoundException"))
    assertTrue(text.contains("Unexpected error rendering PDF preview: IllegalStateException"))

    logs.forEach { assertNull("Expected no throwable attached to Log call", it.throwable) }
    assertFalse(text.contains("Rahul_Sharma"))
    assertFalse(text.contains("Meera_Thyroid"))
    assertFalse(text.contains("Arjun_Lipid"))
    assertFalse(text.contains("Hemoglobin=9.2"))
    assertFalse(text.contains("LDL=180"))
    assertFalse(text.contains("content://"))
  }

  @Test
  fun `preview loader logs only safe class names without uri filename or throwable on image errors`() = runTest {
    val secretImgUri = Uri.parse("content://com.nexzy.test.imgprovider/raw/Sita_Verma_XRay.jpg")
    registerFailingProvider(
      "com.nexzy.test.imgprovider",
      FailingContentProvider(
        openAssetError = { SecurityException("Permission denied for $secretImgUri") }
      )
    )

    val loader = AndroidDocumentPreviewLoader(application)
    val secState = loader.loadPreview(secretImgUri, isPdf = false, pageIndex = 0)
    assertTrue(secState is DocumentPreviewState.Error)
    assertEquals("Permission to access image was revoked.", (secState as DocumentPreviewState.Error).message)

    val runtimeImgUri = Uri.parse("content://com.nexzy.test.imgruntime/raw/Vikram_ECG_Scan.png")
    registerFailingProvider(
      "com.nexzy.test.imgruntime",
      FailingContentProvider(
        openAssetError = { IllegalStateException("Decode failure for $runtimeImgUri QTc=450ms") }
      )
    )
    val runtimeState = loader.loadPreview(runtimeImgUri, isPdf = false, pageIndex = 0)
    assertTrue(runtimeState is DocumentPreviewState.Error)
    assertEquals("Could not render image preview.", (runtimeState as DocumentPreviewState.Error).message)

    val logs = previewAndUploadLogs()
    val text = joinedMessages(logs)
    assertTrue(text.contains("SecurityException reading image URI: SecurityException"))
    assertTrue(text.contains("Error decoding image preview: IllegalStateException"))
    logs.forEach { assertNull("Expected no throwable attached to Log call", it.throwable) }
    assertFalse(text.contains("Sita_Verma"))
    assertFalse(text.contains("Vikram_ECG"))
    assertFalse(text.contains("QTc=450ms"))
    assertFalse(text.contains("content://"))
  }

  @Test
  fun `upload viewmodel does not log uri filename or exception messages during metadata and permission handling`() = runTest(testDispatcher) {
    val secretUri = Uri.parse("content://com.nexzy.test.uploadprovider/doc/Ananya_Discharge_Summary.pdf")
    registerFailingProvider(
      "com.nexzy.test.uploadprovider",
      FailingContentProvider(
        queryError = { SecurityException("Denied query on $secretUri") },
        openAssetError = { FileNotFoundException("Missing file /sdcard/Ananya_Discharge_Summary.pdf") }
      )
    )

    val vm = ReportUploadViewModel(
      application = application,
      consentStore = object : AiConsentStore {
        override fun hasGrantedAiConsent(): Boolean = false
        override fun setAiConsentGranted(granted: Boolean) {}
      },
      contentReader = object : DocumentContentReader {
        override suspend fun readPdfBytes(uriString: String) = Result.failure<ByteArray>(UnsupportedOperationException())
        override suspend fun decodeBitmap(uriString: String) = Result.failure<android.graphics.Bitmap>(UnsupportedOperationException())
      }
    )

    vm.onDocumentSelected(secretUri)
    advanceUntilIdle()

    val logs = previewAndUploadLogs()
    val text = joinedMessages(logs)
    assertTrue(text.contains("ContentResolver query failed: SecurityException"))
    assertTrue(text.contains("Could not open stream for size check: FileNotFoundException"))
    logs.forEach { assertNull("Expected no throwable attached to Log call", it.throwable) }
    assertFalse(text.contains("Ananya_Discharge_Summary"))
    assertFalse(text.contains("/sdcard/"))
    assertFalse(text.contains("content://"))
  }

  @Test
  fun `document scanner helper logs exception class only while preserving user error mapping`() {
    val activity = Robolectric.buildActivity(Activity::class.java).create().get()
    val secretException = IllegalStateException("Google Play Services context leak /data/patient_secret.pdf")
    val failingScanner = Proxy.newProxyInstance(
      GmsDocumentScanner::class.java.classLoader,
      arrayOf(GmsDocumentScanner::class.java)
    ) { _, method, _ ->
      if (method.name == "getStartScanIntent") {
        Tasks.forException<IntentSender>(secretException)
      } else {
        null
      }
    } as GmsDocumentScanner

    var receivedUserError: String? = null
    DocumentScannerHelper.launchScanner(
      activity = activity,
      scanner = failingScanner,
      onIntentSenderReady = {},
      onError = { receivedUserError = it }
    )
    shadowOf(activity.mainLooper).idle()

    assertEquals(
      "Google Play services document scanner module is initializing or unavailable. Please try again in a moment or select an existing document file.",
      receivedUserError
    )

    val logs = previewAndUploadLogs()
    val text = joinedMessages(logs)
    assertTrue(text.contains("Failed to start document scanner: IllegalStateException"))
    logs.forEach { assertNull("Expected no throwable attached to Log call", it.throwable) }
    assertFalse(text.contains("patient_secret.pdf"))
    assertFalse(text.contains("context leak"))
  }

  private class FailingContentProvider(
    private val queryError: (() -> Throwable)? = null,
    private val openFileError: (() -> Throwable)? = null,
    private val openAssetError: (() -> Throwable)? = null
  ) : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?
    ): Cursor? {
      queryError?.let { throw it() }
      return null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
      openFileError?.let { throw it() }
      openAssetError?.let { throw it() }
      return super.openFile(uri, mode)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
  }
}
