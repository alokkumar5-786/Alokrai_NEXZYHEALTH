package com.example.domain.repository

import com.example.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Domain interface managing user profile persistence in Cloud Firestore.
 */
interface UserRepository {

  /**
   * Retrieves the profile for the given user ID from Firestore.
   */
  suspend fun getUserProfile(uid: String): Result<UserProfile?>

  /**
   * Creates or updates the user profile document in Firestore (users/{uid}).
   */
  suspend fun createOrUpdateUserProfile(profile: UserProfile): Result<Unit>

  /**
   * Updates only the phone verification status and phone number for a user.
   */
  suspend fun updatePhoneVerification(uid: String, phoneNumber: String, isVerified: Boolean): Result<Unit>

  /**
   * Observes the real-time profile of the currently signed-in user.
   */
  fun getUserProfileFlow(uid: String): Flow<UserProfile?>
}
