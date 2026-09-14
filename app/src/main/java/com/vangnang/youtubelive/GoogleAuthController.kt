package com.vangnang.youtubelive

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Firebase identity sign-in. Streaming permissions are requested separately when needed. */
class GoogleAuthController(context: Context) {
    private val credentialManager = CredentialManager.create(context)
    private val auth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signInEmail(email: String, password: String): FirebaseUser =
        auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Không nhận được tài khoản Firebase.")

    suspend fun createEmailAccount(email: String, password: String): FirebaseUser =
        auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Không tạo được tài khoản Firebase.")

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signIn(activity: Activity): FirebaseUser {
        val credential = try {
            requestCredential(activity, authorizedAccountsOnly = true)
        } catch (_: NoCredentialException) {
            try {
                requestCredential(activity, authorizedAccountsOnly = false)
            } catch (_: NoCredentialException) {
                requestExplicitGoogleSignIn(activity)
            }
        }
        require(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google không trả về thông tin đăng nhập hợp lệ."
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        return auth.signInWithCredential(firebaseCredential).await().user
            ?: error("Không nhận được tài khoản Firebase.")
    }

    private suspend fun requestCredential(
        activity: Activity,
        authorizedAccountsOnly: Boolean
    ) = credentialManager.getCredential(
        context = activity,
        request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(authorizedAccountsOnly)
                    .setServerClientId(activity.getString(R.string.default_web_client_id))
                    .setAutoSelectEnabled(authorizedAccountsOnly)
                    .build()
            )
            .build()
    ).credential

    /**
     * An explicit tap on the Google button must still open Google's account picker when
     * Credential Manager cannot return a saved/authorized account from its bottom sheet.
     */
    private suspend fun requestExplicitGoogleSignIn(activity: Activity) =
        credentialManager.getCredential(
            context = activity,
            request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(
                        activity.getString(R.string.default_web_client_id)
                    ).build()
                )
                .build()
        ).credential

    suspend fun signOut() {
        auth.signOut()
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
