package com.codigitech.belay.ui.auth

import com.codigitech.belay.data.repository.AuthOutcome
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.domain.auth.SignupValidationError
import com.codigitech.belay.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private class FakeAuthRepository(
  private var currentUserId: String? = null,
  private val signUpResult: AuthOutcome = AuthOutcome.Success("uid-1"),
  private val logInResult: AuthOutcome = AuthOutcome.Success("uid-1"),
) : AuthRepository {
  var loggedOut = false

  override suspend fun signUp(email: String, password: String): AuthOutcome = signUpResult

  override suspend fun logIn(email: String, password: String): AuthOutcome = logInResult

  override fun currentUserId(): String? = currentUserId

  override fun logOut() {
    loggedOut = true
    currentUserId = null
  }
}

class AuthViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun `starts signed out when the repository has no current user`() = runTest {
    val viewModel = AuthViewModel(FakeAuthRepository(currentUserId = null))

    assertNull(viewModel.uiState.value.signedInUserId)
  }

  @Test
  fun `starts signed in when the repository already has a current user`() = runTest {
    val viewModel = AuthViewModel(FakeAuthRepository(currentUserId = "uid-existing"))

    assertEquals("uid-existing", viewModel.uiState.value.signedInUserId)
  }

  @Test
  fun `editing email or password clears any prior error`() = runTest {
    val viewModel = AuthViewModel(FakeAuthRepository(signUpResult = AuthOutcome.Failure("nope")))
    viewModel.onEmailChange("arun@example.com")
    viewModel.onPasswordChange("password1")
    viewModel.submit(AuthMode.SignUp)

    viewModel.onEmailChange("arun2@example.com")

    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `successful sign up sets the signed-in user and clears loading`() = runTest {
    val viewModel = AuthViewModel(FakeAuthRepository(signUpResult = AuthOutcome.Success("uid-42")))
    viewModel.onEmailChange("arun@example.com")
    viewModel.onPasswordChange("password1")

    viewModel.submit(AuthMode.SignUp)

    assertEquals("uid-42", viewModel.uiState.value.signedInUserId)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `validation failure surfaces a friendly message and does not sign in`() = runTest {
    val viewModel =
      AuthViewModel(FakeAuthRepository(signUpResult = AuthOutcome.ValidationFailed(SignupValidationError.PasswordTooShort)))
    viewModel.onEmailChange("arun@example.com")
    viewModel.onPasswordChange("abc")

    viewModel.submit(AuthMode.SignUp)

    assertEquals("Password must be at least 6 characters", viewModel.uiState.value.errorMessage)
    assertNull(viewModel.uiState.value.signedInUserId)
  }

  @Test
  fun `firebase failure surfaces its message`() = runTest {
    val viewModel = AuthViewModel(FakeAuthRepository(logInResult = AuthOutcome.Failure("The password is invalid.")))
    viewModel.onEmailChange("arun@example.com")
    viewModel.onPasswordChange("wrongpass")

    viewModel.submit(AuthMode.LogIn)

    assertEquals("The password is invalid.", viewModel.uiState.value.errorMessage)
    assertNull(viewModel.uiState.value.signedInUserId)
  }
}
