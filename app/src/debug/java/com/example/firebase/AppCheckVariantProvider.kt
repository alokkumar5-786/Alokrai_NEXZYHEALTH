package com.example.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug-variant App Check provider installer.
 * Preserves synchronous debug-token persistence via SharedPreferences before installing
 * [DebugAppCheckProviderFactory]. Never compiled into release builds.
 */
internal object AppCheckVariantProvider {
  private const val TAG = "NexzyAppCheckDebug"
  private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
  private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
  private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

  fun install(context: Context, intent: Intent?, appCheck: FirebaseAppCheck): String {
    val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
    if (!debugToken.isNullOrEmpty()) {
      val persistenceKey = FirebaseApp.getInstance().persistenceKey
      val prefs = context.getSharedPreferences(
        "$DEBUG_STORE_PREFIX$persistenceKey",
        Context.MODE_PRIVATE
      )
      // CRITICAL: Synchronous commit() to prevent race conditions with DebugAppCheckProviderFactory initialization
      val persisted = prefs.edit()
        .putString(DEBUG_SECRET_KEY, debugToken)
        .commit()
      if (!persisted) {
        Log.e(TAG, "Failed to persist App Check debug token synchronously.")
        throw IllegalStateException("Synchronous SharedPreferences commit failed for App Check debug token.")
      }
      Log.d(TAG, "App Check debug token persisted synchronously.")
    } else {
      Log.d(TAG, "No debug token found in launch Intent extras.")
    }

    appCheck.installAppCheckProviderFactory(
      DebugAppCheckProviderFactory.getInstance()
    )
    return "DebugAppCheckProviderFactory"
  }
}
