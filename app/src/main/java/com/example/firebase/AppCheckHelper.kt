package com.example.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

/**
 * Authoritative App Check initialization helper required for Firebase AI Logic.
 * Ensures debug tokens are synchronously committed and release providers are safely installed.
 */
object AppCheckHelper {
  private const val TAG = "NexzyAppCheck"

  private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
  private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
  private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
  private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

  fun configureAppCheck(context: Context, intent: Intent?) {
    try {
      if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(context.applicationContext)
      }

      val appCheck = FirebaseAppCheck.getInstance()

      if (BuildConfig.DEBUG) {
        // Debug variant: Check intent extras for debug token
        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        if (!debugToken.isNullOrEmpty()) {
          val persistenceKey = FirebaseApp.getInstance().persistenceKey
          val prefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
          )
          // CRITICAL: Synchronous commit() to prevent race conditions with AppCheck factory initialization
          prefs.edit()
            .putString(DEBUG_SECRET_KEY, debugToken)
            .commit()
          Log.d(TAG, "App Check debug token persisted synchronously.")
        } else {
          Log.d(TAG, "No debug token found in launch Intent extras.")
        }

        appCheck.installAppCheckProviderFactory(
          DebugAppCheckProviderFactory.getInstance()
        )
        Log.i(TAG, "DebugAppCheckProviderFactory installed successfully.")
      } else {
        // Release variant: Look up recaptcha_site_key safely without throwing NotFoundException
        val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
        if (resId != 0) {
          val siteKey = context.getString(resId)
          if (siteKey.isNotBlank()) {
            appCheck.installAppCheckProviderFactory(
              RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            Log.i(TAG, "RecaptchaAppCheckProviderFactory installed successfully.")
          } else {
            Log.w(TAG, "recaptcha_site_key resource is blank. Release App Check not bound.")
          }
        } else {
          Log.d(TAG, "recaptcha_site_key not found in resources; relying on default provider.")
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize Firebase App Check: ${e.message}", e)
    }
  }
}
