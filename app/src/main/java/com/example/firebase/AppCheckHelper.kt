package com.example.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Authoritative App Check initialization helper required for Firebase AI Logic.
 * Delegates provider installation to the build-variant-specific [AppCheckVariantProvider]
 * and exposes non-sensitive initialization status for observability.
 */
object AppCheckHelper {
  private const val TAG = "NexzyAppCheck"

  @Volatile
  var isConfigured: Boolean = false
    private set

  @Volatile
  var activeProviderName: String? = null
    private set

  @Volatile
  var lastErrorMessage: String? = null
    private set

  fun configureAppCheck(context: Context, intent: Intent?) {
    isConfigured = false
    activeProviderName = null
    lastErrorMessage = null

    try {
      val appContext = context.applicationContext ?: context
      if (FirebaseApp.getApps(appContext).isEmpty()) {
        FirebaseApp.initializeApp(appContext)
      }

      val appCheck = FirebaseAppCheck.getInstance()
      val installedProvider = AppCheckVariantProvider.install(context, intent, appCheck)

      isConfigured = true
      activeProviderName = installedProvider
      Log.i(TAG, "Firebase App Check configured using $installedProvider.")
    } catch (e: Exception) {
      isConfigured = false
      activeProviderName = null
      lastErrorMessage = e.javaClass.simpleName
      Log.e(TAG, "Failed to initialize Firebase App Check (${e.javaClass.simpleName}).")
    }
  }
}
