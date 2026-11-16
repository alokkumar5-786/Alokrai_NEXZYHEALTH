package com.example.data.repository

import android.util.Log
import com.example.domain.repository.UserRepository
import com.example.firebase.FirebaseManager
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class FirestoreUserRepository(
  firestoreInstance: FirebaseFirestore? = null
) : UserRepository {

  private val firestore: FirebaseFirestore by lazy {
    firestoreInstance ?: FirebaseManager.firestore
  }

  companion object {
    private const val TAG = "NexzyUserRepo"
    private const val USERS_COLLECTION = "users"
  }

  override suspend fun getUserProfile(uid: String): Result<UserProfile?> = withContext(Dispatchers.IO) {
    if (uid.isBlank()) return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
    try {
      val doc = firestore.collection(USERS_COLLECTION).document(uid).get().awaitTask()
      if (doc.exists()) {
        val profile = UserProfile(
          uid = doc.getString("uid") ?: uid,
          email = doc.getString("email") ?: "",
          emailVerified = doc.getBoolean("emailVerified") ?: false,
          phoneNumber = doc.getString("phoneNumber") ?: "",
          phoneVerified = doc.getBoolean("phoneVerified") ?: false,
          displayName = doc.getString("displayName") ?: "",
          createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
          updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
          accountStatus = doc.getString("accountStatus") ?: "ACTIVE",
          preferredLanguage = doc.getString("preferredLanguage") ?: "en"
        )
        Result.success(profile)
      } else {
        Result.success(null)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching user profile: ${e.message}", e)
      Result.failure(e)
    }
  }

  override suspend fun createOrUpdateUserProfile(profile: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
    if (profile.uid.isBlank()) return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
    try {
      val userDocRef = firestore.collection(USERS_COLLECTION).document(profile.uid)
      val existingDoc = userDocRef.get().awaitTask()

      val dataMap = mutableMapOf<String, Any>(
        "uid" to profile.uid,
        "email" to profile.email,
        "emailVerified" to profile.emailVerified,
        "phoneNumber" to profile.phoneNumber,
        "phoneVerified" to profile.phoneVerified,
        "displayName" to profile.displayName,
        "updatedAt" to System.currentTimeMillis(),
        "accountStatus" to (if (profile.accountStatus.isNotBlank()) profile.accountStatus else "ACTIVE"),
        "preferredLanguage" to (if (profile.preferredLanguage.isNotBlank()) profile.preferredLanguage else "en")
      )

      if (!existingDoc.exists()) {
        // First time creation - set all initial fields including createdAt
        val newDocData = mutableMapOf<String, Any>(
          "uid" to profile.uid,
          "email" to profile.email,
          "emailVerified" to profile.emailVerified,
          "phoneNumber" to profile.phoneNumber,
          "phoneVerified" to profile.phoneVerified,
          "displayName" to profile.displayName,
          "preferredLanguage" to (if (profile.preferredLanguage.isNotBlank()) profile.preferredLanguage else "en"),
          "createdAt" to profile.createdAt,
          "updatedAt" to System.currentTimeMillis(),
          "accountStatus" to (if (profile.accountStatus.isNotBlank()) profile.accountStatus else "ACTIVE")
        )
        userDocRef.set(newDocData).awaitTask()
        Log.i(TAG, "Created new user profile in Firestore for uid: ${profile.uid}")
      } else {
        // Updating existing document:
        // Strictly preserve createdAt to comply with Firestore security rule diff().affectedKeys()
        val updateMap = mutableMapOf<String, Any>(
          "uid" to profile.uid,
          "email" to profile.email,
          "emailVerified" to profile.emailVerified,
          "updatedAt" to System.currentTimeMillis()
        )

        // Only update displayName if non-blank in incoming profile, otherwise preserve existing
        if (profile.displayName.isNotBlank()) {
          updateMap["displayName"] = profile.displayName
        }

        // Only update phoneNumber / phoneVerified if incoming profile has phoneVerified = true or non-blank phoneNumber
        // Otherwise preserve existing phone number and verification status!
        if (profile.phoneVerified || profile.phoneNumber.isNotBlank()) {
          updateMap["phoneNumber"] = profile.phoneNumber
          updateMap["phoneVerified"] = profile.phoneVerified
        }

        if (profile.preferredLanguage.isNotBlank()) {
          updateMap["preferredLanguage"] = profile.preferredLanguage
        }

        userDocRef.set(updateMap, SetOptions.merge()).awaitTask()
        Log.i(TAG, "Updated existing user profile in Firestore for uid: ${profile.uid}")
      }

      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error writing user profile: ${e.message}", e)
      Result.failure(e)
    }
  }

  override suspend fun updatePhoneVerification(
    uid: String,
    phoneNumber: String,
    isVerified: Boolean
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (uid.isBlank()) return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
    try {
      val updates = mapOf(
        "uid" to uid,
        "phoneNumber" to phoneNumber,
        "phoneVerified" to isVerified,
        "updatedAt" to System.currentTimeMillis()
      )
      firestore.collection(USERS_COLLECTION).document(uid).set(updates, SetOptions.merge()).awaitTask()
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error updating phone verification: ${e.message}", e)
      Result.failure(e)
    }
  }

  override fun getUserProfileFlow(uid: String): Flow<UserProfile?> = callbackFlow {
    if (uid.isBlank()) {
      trySend(null)
      close()
      return@callbackFlow
    }

    val subscription = firestore.collection(USERS_COLLECTION).document(uid)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e(TAG, "Listen failed for user profile: ${error.message}", error)
          trySend(null)
          return@addSnapshotListener
        }

        if (snapshot != null && snapshot.exists()) {
          val profile = UserProfile(
            uid = snapshot.getString("uid") ?: uid,
            email = snapshot.getString("email") ?: "",
            emailVerified = snapshot.getBoolean("emailVerified") ?: false,
            phoneNumber = snapshot.getString("phoneNumber") ?: "",
            phoneVerified = snapshot.getBoolean("phoneVerified") ?: false,
            displayName = snapshot.getString("displayName") ?: "",
            createdAt = snapshot.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis(),
            accountStatus = snapshot.getString("accountStatus") ?: "ACTIVE",
            preferredLanguage = snapshot.getString("preferredLanguage") ?: "en"
          )
          trySend(profile)
        } else {
          trySend(null)
        }
      }

    awaitClose {
      subscription.remove()
    }
  }
}
