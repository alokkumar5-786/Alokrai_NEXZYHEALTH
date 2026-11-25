package com.example.data.repository

import android.util.Log
import com.example.domain.repository.UserRepository
import com.example.firebase.FirebaseManager
import com.example.model.ExplanationConstants
import com.example.model.SupportedExplanationLanguage
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale
import kotlinx.coroutines.CancellationException
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
    private val LEGACY_EXPLICIT_NON_ENGLISH_CODES = setOf("hi", "bn", "mr")

    internal fun validateSupportedLanguageCode(languageCode: String): String? {
      val normalized = languageCode.trim().lowercase(Locale.ROOT)
      return if (normalized in ExplanationConstants.SUPPORTED_LANGUAGE_CODES) normalized else null
    }

    internal fun profileFromDocumentFields(
      uid: String,
      storedUid: String?,
      email: String?,
      emailVerified: Boolean?,
      phoneNumber: String?,
      phoneVerified: Boolean?,
      displayName: String?,
      createdAt: Long?,
      updatedAt: Long?,
      accountStatus: String?,
      preferredLanguage: String?,
      preferredLanguageSelected: Boolean?
    ): UserProfile {
      val normalizedStoredLang = SupportedExplanationLanguage.fromCodeOrName(preferredLanguage)?.code ?: "en"
      val resolvedSelected = preferredLanguageSelected
        ?: (normalizedStoredLang in LEGACY_EXPLICIT_NON_ENGLISH_CODES)
      val now = System.currentTimeMillis()
      return UserProfile(
        uid = storedUid ?: uid,
        email = email ?: "",
        emailVerified = emailVerified ?: false,
        phoneNumber = phoneNumber ?: "",
        phoneVerified = phoneVerified ?: false,
        displayName = displayName ?: "",
        createdAt = createdAt ?: now,
        updatedAt = updatedAt ?: now,
        accountStatus = accountStatus ?: "ACTIVE",
        preferredLanguage = normalizedStoredLang,
        preferredLanguageSelected = resolvedSelected
      )
    }

    internal fun buildNewProfileMap(
      profile: UserProfile,
      nowMillis: Long = System.currentTimeMillis()
    ): MutableMap<String, Any> {
      val normalizedLang = SupportedExplanationLanguage.fromCodeOrName(profile.preferredLanguage)?.code ?: "en"
      val isSelected = profile.preferredLanguageSelected || (normalizedLang in LEGACY_EXPLICIT_NON_ENGLISH_CODES)
      return mutableMapOf(
        "uid" to profile.uid,
        "email" to profile.email,
        "emailVerified" to profile.emailVerified,
        "phoneNumber" to profile.phoneNumber,
        "phoneVerified" to profile.phoneVerified,
        "displayName" to profile.displayName,
        "preferredLanguage" to normalizedLang,
        "preferredLanguageSelected" to isSelected,
        "createdAt" to profile.createdAt,
        "updatedAt" to nowMillis,
        "accountStatus" to (if (profile.accountStatus.isNotBlank()) profile.accountStatus else "ACTIVE")
      )
    }

    internal fun buildUpdateProfileMap(
      profile: UserProfile,
      existingPreferredLanguage: String?,
      existingPreferredLanguageSelected: Boolean?,
      nowMillis: Long = System.currentTimeMillis()
    ): MutableMap<String, Any> {
      // Strictly preserve createdAt and accountStatus to comply with Firestore security rule diff().affectedKeys()
      val updateMap = mutableMapOf<String, Any>(
        "uid" to profile.uid,
        "email" to profile.email,
        "emailVerified" to profile.emailVerified,
        "updatedAt" to nowMillis
      )

      if (profile.displayName.isNotBlank()) {
        updateMap["displayName"] = profile.displayName
      }

      if (profile.phoneVerified || profile.phoneNumber.isNotBlank()) {
        updateMap["phoneNumber"] = profile.phoneNumber
        updateMap["phoneVerified"] = profile.phoneVerified
      }

      val existingLangCode = SupportedExplanationLanguage.fromCodeOrName(existingPreferredLanguage)?.code
      val existingHasExplicitPreference = (existingPreferredLanguageSelected == true) ||
        (existingLangCode in LEGACY_EXPLICIT_NON_ENGLISH_CODES)

      val incomingLangCode = SupportedExplanationLanguage.fromCodeOrName(profile.preferredLanguage)?.code
      val incomingIsExplicit = profile.preferredLanguageSelected ||
        (incomingLangCode in LEGACY_EXPLICIT_NON_ENGLISH_CODES)

      if (existingHasExplicitPreference) {
        if (profile.preferredLanguageSelected && incomingLangCode != null) {
          updateMap["preferredLanguage"] = incomingLangCode
          updateMap["preferredLanguageSelected"] = true
        }
      } else {
        if (incomingIsExplicit && incomingLangCode != null) {
          updateMap["preferredLanguage"] = incomingLangCode
          updateMap["preferredLanguageSelected"] = true
        } else if (existingLangCode == null) {
          updateMap["preferredLanguage"] = incomingLangCode ?: "en"
          updateMap["preferredLanguageSelected"] = false
        }
      }

      return updateMap
    }

    internal fun buildPreferredLanguageUpdateMap(
      uid: String,
      normalizedLanguageCode: String,
      nowMillis: Long = System.currentTimeMillis()
    ): Map<String, Any> = mapOf(
      "uid" to uid,
      "preferredLanguage" to normalizedLanguageCode,
      "preferredLanguageSelected" to true,
      "updatedAt" to nowMillis
    )
  }

  override suspend fun getUserProfile(uid: String): Result<UserProfile?> = withContext(Dispatchers.IO) {
    if (uid.isBlank()) return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
    try {
      val doc = firestore.collection(USERS_COLLECTION).document(uid).get().awaitTask()
      if (doc.exists()) {
        val profile = profileFromDocumentFields(
          uid = uid,
          storedUid = doc.getString("uid"),
          email = doc.getString("email"),
          emailVerified = doc.getBoolean("emailVerified"),
          phoneNumber = doc.getString("phoneNumber"),
          phoneVerified = doc.getBoolean("phoneVerified"),
          displayName = doc.getString("displayName"),
          createdAt = doc.getLong("createdAt"),
          updatedAt = doc.getLong("updatedAt"),
          accountStatus = doc.getString("accountStatus"),
          preferredLanguage = doc.getString("preferredLanguage"),
          preferredLanguageSelected = doc.getBoolean("preferredLanguageSelected")
        )
        Result.success(profile)
      } else {
        Result.success(null)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "User profile fetch failed: ${e.javaClass.simpleName}")
      Result.failure(e)
    }
  }

  override suspend fun createOrUpdateUserProfile(profile: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
    if (profile.uid.isBlank()) return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
    try {
      val userDocRef = firestore.collection(USERS_COLLECTION).document(profile.uid)
      val existingDoc = userDocRef.get().awaitTask()

      if (!existingDoc.exists()) {
        val newDocData = buildNewProfileMap(profile)
        userDocRef.set(newDocData).awaitTask()
        Log.i(TAG, "User profile created in Firestore.")
      } else {
        val updateMap = buildUpdateProfileMap(
          profile = profile,
          existingPreferredLanguage = existingDoc.getString("preferredLanguage"),
          existingPreferredLanguageSelected = existingDoc.getBoolean("preferredLanguageSelected")
        )
        userDocRef.set(updateMap, SetOptions.merge()).awaitTask()
        Log.i(TAG, "User profile updated in Firestore.")
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "User profile write failed: ${e.javaClass.simpleName}")
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
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Phone verification profile update failed: ${e.javaClass.simpleName}")
      Result.failure(e)
    }
  }

  override suspend fun updatePreferredLanguage(
    uid: String,
    languageCode: String
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (uid.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
    }
    val validatedCode = validateSupportedLanguageCode(languageCode)
      ?: return@withContext Result.failure(
        IllegalArgumentException("Unsupported language code: $languageCode. Supported codes: en, hi, bn, mr")
      )

    try {
      val updates = buildPreferredLanguageUpdateMap(uid, validatedCode)
      firestore.collection(USERS_COLLECTION).document(uid).set(updates, SetOptions.merge()).awaitTask()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Preferred language profile update failed: ${e.javaClass.simpleName}")
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
          Log.e(TAG, "User profile listener failed: ${error.javaClass.simpleName}")
          trySend(null)
          return@addSnapshotListener
        }

        if (snapshot != null && snapshot.exists()) {
          val profile = profileFromDocumentFields(
            uid = uid,
            storedUid = snapshot.getString("uid"),
            email = snapshot.getString("email"),
            emailVerified = snapshot.getBoolean("emailVerified"),
            phoneNumber = snapshot.getString("phoneNumber"),
            phoneVerified = snapshot.getBoolean("phoneVerified"),
            displayName = snapshot.getString("displayName"),
            createdAt = snapshot.getLong("createdAt"),
            updatedAt = snapshot.getLong("updatedAt"),
            accountStatus = snapshot.getString("accountStatus"),
            preferredLanguage = snapshot.getString("preferredLanguage"),
            preferredLanguageSelected = snapshot.getBoolean("preferredLanguageSelected")
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
