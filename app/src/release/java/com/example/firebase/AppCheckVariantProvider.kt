package com.example.firebase

import android.content.Context
import android.content.Intent
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release-variant App Check provider installer using Play Integrity.
 * Contains zero references to debug providers, debug tokens, or reCAPTCHA.
 */
internal object AppCheckVariantProvider {
  fun install(context: Context, intent: Intent?, appCheck: FirebaseAppCheck): String {
    appCheck.installAppCheckProviderFactory(
      PlayIntegrityAppCheckProviderFactory.getInstance()
    )
    return "PlayIntegrityAppCheckProviderFactory"
  }
}
