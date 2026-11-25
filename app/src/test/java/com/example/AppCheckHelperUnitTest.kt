package com.example

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.firebase.AppCheckHelper
import com.google.firebase.FirebaseApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppCheckHelperUnitTest {

  private lateinit var application: Application

  @Before
  fun setUp() {
    application = ApplicationProvider.getApplicationContext()
    if (FirebaseApp.getApps(application).isEmpty()) {
      FirebaseApp.initializeApp(application)
    }
    clearDebugStorePrefs()
  }

  @After
  fun tearDown() {
    clearDebugStorePrefs()
  }

  private fun clearDebugStorePrefs() {
    val apps = FirebaseApp.getApps(application)
    if (apps.isNotEmpty()) {
      val persistenceKey = apps.first().persistenceKey
      application.getSharedPreferences(
        "com.google.firebase.appcheck.debug.store.$persistenceKey",
        Context.MODE_PRIVATE
      ).edit().clear().commit()
    }
  }

  @Test
  fun `configureAppCheck installs DebugAppCheckProviderFactory and persists debug token synchronously`() {
    val intent = Intent().apply {
      putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", "debug-token-fixture-value")
    }

    AppCheckHelper.configureAppCheck(application, intent)

    assertTrue(AppCheckHelper.isConfigured)
    assertEquals("DebugAppCheckProviderFactory", AppCheckHelper.activeProviderName)
    assertNull(AppCheckHelper.lastErrorMessage)

    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val prefs = application.getSharedPreferences(
      "com.google.firebase.appcheck.debug.store.$persistenceKey",
      Context.MODE_PRIVATE
    )
    assertEquals(
      "debug-token-fixture-value",
      prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
    )
  }

  @Test
  fun `configureAppCheck installs DebugAppCheckProviderFactory when Intent is null`() {
    AppCheckHelper.configureAppCheck(application, null)

    assertTrue(AppCheckHelper.isConfigured)
    assertEquals("DebugAppCheckProviderFactory", AppCheckHelper.activeProviderName)
    assertNull(AppCheckHelper.lastErrorMessage)

    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val prefs = application.getSharedPreferences(
      "com.google.firebase.appcheck.debug.store.$persistenceKey",
      Context.MODE_PRIVATE
    )
    assertNull(prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null))
  }

  @Test
  fun `configureAppCheck records failure when synchronous debug token persistence fails`() {
    val failingContext = object : ContextWrapper(application) {
      override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        val delegate = super.getSharedPreferences(name, mode)
        return object : SharedPreferences by delegate {
          override fun edit(): SharedPreferences.Editor {
            val editorDelegate = delegate.edit()
            return object : SharedPreferences.Editor by editorDelegate {
              override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                editorDelegate.putString(key, value)
                return this
              }

              override fun commit(): Boolean = false
            }
          }
        }
      }
    }

    val intent = Intent().apply {
      putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", "debug-token-fixture-value")
    }

    AppCheckHelper.configureAppCheck(failingContext, intent)

    assertFalse(AppCheckHelper.isConfigured)
    assertNull(AppCheckHelper.activeProviderName)
    assertEquals("IllegalStateException", AppCheckHelper.lastErrorMessage)
  }

  @Test
  fun `debug runtime classpath includes DebugAppCheckProviderFactory and excludes PlayIntegrity and Recaptcha`() {
    val debugFactory = Class.forName(
      "com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory"
    )
    assertNotNull(debugFactory)

    val playIntegrityMissing = runCatching {
      Class.forName("com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory")
    }.exceptionOrNull()
    assertTrue(
      "PlayIntegrityAppCheckProviderFactory must not be on debug runtime classpath",
      playIntegrityMissing is ClassNotFoundException
    )

    val recaptchaMissing = runCatching {
      Class.forName("com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory")
    }.exceptionOrNull()
    assertTrue(
      "RecaptchaAppCheckProviderFactory must be removed from classpath",
      recaptchaMissing is ClassNotFoundException
    )
  }
}
