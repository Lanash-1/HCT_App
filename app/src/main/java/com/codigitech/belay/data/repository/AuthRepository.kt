package com.codigitech.belay.data.repository

import com.codigitech.belay.domain.auth.SignupValidationError
import com.codigitech.belay.domain.auth.validateSignup
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/** Firebase UID (see docs/DATA_MODEL.md `users.user_id` and TECH_STACK.md §10 for why auth moved off Catalyst). */
sealed interface AuthOutcome {
  data class Success(val userId: String) : AuthOutcome

  data class ValidationFailed(val reason: SignupValidationError) : AuthOutcome

  data class Failure(val message: String) : AuthOutcome
}

interface AuthRepository {
  suspend fun signUp(email: String, password: String): AuthOutcome

  suspend fun logIn(email: String, password: String): AuthOutcome

  fun currentUserId(): String?

  fun currentUserEmail(): String?

  fun logOut()
}

class FirebaseAuthRepositoryImpl
@Inject
constructor(private val firebaseAuth: FirebaseAuth) : AuthRepository {

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

  override fun logOut() {
    firebaseAuth.signOut()
  }
}
