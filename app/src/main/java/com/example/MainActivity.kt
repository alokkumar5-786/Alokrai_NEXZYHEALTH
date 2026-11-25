package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.firebase.FirebaseManager
import com.example.ui.NexzyApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  companion object {
    private const val TAG = "MainActivity"
    private var lastProcessedLink: String? = null
  }

  // State holding pending email-link for Compose navigation
  private val incomingEmailLink = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // 1. Ensure Firebase and App Check are configured before any UI or model calls
    com.example.firebase.AppCheckHelper.configureAppCheck(this, intent)
    FirebaseManager.ensureInitialized(applicationContext)

    handleIncomingIntent(intent)

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          NexzyApp(
            incomingEmailLink = incomingEmailLink.value,
            onEmailLinkConsumed = {
              incomingEmailLink.value = null
            }
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    val dataUri = intent?.data ?: return
    val link = dataUri.toString()

    try {
      FirebaseManager.ensureInitialized(applicationContext)
      if (FirebaseManager.auth.isSignInWithEmailLink(link)) {
        if (link != lastProcessedLink) {
          Log.i(TAG, "Incoming email sign-in link detected.")
          lastProcessedLink = link
          incomingEmailLink.value = link
        } else {
          Log.d(TAG, "Duplicate incoming email sign-in link ignored.")
        }
      } else {
        Log.d(TAG, "Email-link validation failed: incoming intent URI is not a Firebase sign-in link.")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Email-link validation failed: ${e.javaClass.simpleName}")
    }
  }
}
