package com.codigitech.belay.data.repository

import com.codigitech.belay.core.LocalDataReset
import com.codigitech.belay.domain.auth.SignupValidationError
import com.codigitech.belay.domain.auth.validateSignup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Firebase UID (see docs/DATA_MODEL.md `users.user_id` and TECH_STACK.md §10 for why auth moved off Catalyst). */
sealed interface AuthOutcome {
  data class Success(val userId: String) : AuthOutcome

  data class ValidationFailed(val reason: SignupValidationError) : AuthOutcome

  data class Failure(val message: String) : AuthOutcome
}

/** PRD §6.3: in-app account deletion, required for Play Store release. */
sealed interface AccountDeletionResult {
  data object Success : AccountDeletionResult

  data class Failure(val message: String) : AccountDeletionResult
}

interface AuthRepository {
  suspend fun signUp(email: String, password: String): AuthOutcome

  suspend fun logIn(email: String, password: String): AuthOutcome

  fun currentUserId(): String?

  fun currentUserEmail(): String?

  suspend fun logOut()

  /** Deletes the account and its data server-side (backend/functions `deleteAccount`), then clears local state. */
  suspend fun deleteAccount(): AccountDeletionResult
}

class FirebaseAuthRepositoryImpl
@Inject
constructor(
  private val firebaseAuth: FirebaseAuth,
  private val firebaseFunctions: FirebaseFunctions,
  private val localDataReset: LocalDataReset,
) : AuthRepository {

  override suspend fun signUp(email: String, password: String): AuthOutcome {
    validateSignup(email, password)?.let { return AuthOutcome.ValidationFailed(it) }
    return try {
      val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
      AuthOutcome.Success(result.user?.uid.orEmpty())
    } catch (e: Exception) {
      AuthOutcome.Failure(e.message ?: "Sign up failed")
    }
  }

  override suspend fun logIn(email: String, password: String): AuthOutcome {
    validateSignup(email, password)?.let { return AuthOutcome.ValidationFailed(it) }
    return try {
      val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
      AuthOutcome.Success(result.user?.uid.orEmpty())
    } catch (e: Exception) {
      AuthOutcome.Failure(e.message ?: "Log in failed")
    }
  }

  override fun currentUserId(): String? = firebaseAuth.currentUser?.uid

  override fun currentUserEmail(): String? = firebaseAuth.currentUser?.email

  override suspend fun logOut() {
    // Room otherwise keeps mirroring the previous account's data — the next person who signs in
    // on this device would see it until it's overwritten.
    withContext(Dispatchers.IO) { localDataReset.clearAll() }
    firebaseAuth.signOut()
  }

  override suspend fun deleteAccount(): AccountDeletionResult =
    try {
      // Deletes Firestore data (challenges/habits/check-ins/interactions/recaps/pairings) and the
      // Auth user server-side, with admin privileges — see backend/functions/index.js deleteAccount.
      firebaseFunctions.getHttpsCallable("deleteAccount").call().await()
      withContext(Dispatchers.IO) { localDataReset.clearAll() }
      firebaseAuth.signOut()
      AccountDeletionResult.Success
    } catch (e: Exception) {
      AccountDeletionResult.Failure(e.message ?: "Couldn't delete account")
    }
}
