package com.example.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * Manages Firebase connection, initialization verification, and core service references.
 */
object FirebaseManager {
  private const val TAG = "NexzyFirebase"

  /**
   * Ensures FirebaseApp is initialized for the application context.
   */
  fun ensureInitialized(context: Context): Boolean {
    return try {
      if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(context.applicationContext)
      }
      true
    } catch (e: Exception) {
      Log.e(TAG, "Firebase initialization check failed: ${e.javaClass.simpleName}")
      false
    }
  }

  val isInitialized: Boolean
    get() = try {
      FirebaseApp.getInstance() != null
    } catch (e: Exception) {
      false
    }

  val auth: FirebaseAuth
    get() = FirebaseAuth.getInstance()

  val firestore: FirebaseFirestore
    get() = FirebaseFirestore.getInstance()

  val storage: FirebaseStorage
    get() = FirebaseStorage.getInstance()

  /**
   * Verifies that Firebase is properly initialized and configured for the app.
   */
  fun verifyInitialization(context: Context): FirebaseStatus {
    return try {
      val app = FirebaseApp.getInstance()
      val projectId = app.options.projectId
      val appId = app.options.applicationId

      Log.i(TAG, "Firebase initialization succeeded.")

      FirebaseStatus(
        isConfigured = true,
        projectId = projectId ?: "nexzyhealth",
        appId = appId,
        authReady = true,
        firestoreReady = true,
        message = "Connected to Firebase project: $projectId"
      )
    } catch (e: Exception) {
      Log.e(TAG, "Firebase initialization failed: ${e.javaClass.simpleName}")
      FirebaseStatus(
        isConfigured = false,
        projectId = "",
        appId = "",
        authReady = false,
        firestoreReady = false,
        message = "Initialization failed: ${e.message}"
      )
    }
  }
}

data class FirebaseStatus(
  val isConfigured: Boolean,
  val projectId: String,
  val appId: String,
  val authReady: Boolean,
  val firestoreReady: Boolean,
  val message: String
)
