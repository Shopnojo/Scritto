package com.internship.scritto.data.auth

import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.internship.scritto.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.providers.IDToken
import io.github.jan.supabase.auth.providers.builtin.Google
import io.github.jan.supabase.createSupabaseClient
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.first

/**
 * Native Google sign-in backed by Supabase Auth.
 *
 * Only the Supabase URL/publishable key and Google Web OAuth client ID are
 * read from BuildConfig. No server-side secret belongs in this Android app.
 */
object ScrittoGoogleAuth {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    private val supabase by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "Missing SUPABASE_URL. Add it to the root .env file."
        }
        require(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Missing SUPABASE_PUBLISHABLE_KEY. Add it to the root .env file."
        }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth)
        }
    }

    data class GoogleAccount(
        val displayName: String,
        val email: String,
        val photoUrl: String?
    )

    /**
     * Prompts with Android Credential Manager, exchanges Google's ID token
     * with Supabase, and returns the actual Google profile details.
     */
    suspend fun signIn(context: Context): GoogleAccount {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        require(webClientId.isNotBlank()) {
            "Missing GOOGLE_WEB_CLIENT_ID. Add your Web OAuth client ID to the root .env file."
        }

        val rawNonce = createNonce()
        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(sha256(rawNonce))
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credentialManager = CredentialManager.create(context)
        val response = credentialManager.getCredential(context, request)
        val credential = response.credential

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Google did not return a supported ID-token credential.")
        }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)

        supabase.auth.signInWith(IDToken) {
            idToken = googleCredential.idToken
            provider = Google
            nonce = rawNonce
        }

        return GoogleAccount(
            displayName = googleCredential.displayName
                ?.takeIf { it.isNotBlank() }
                ?: googleCredential.givenName
                ?: googleCredential.email,
            email = googleCredential.email,
            photoUrl = googleCredential.profilePictureUri?.toString()
        )
    }

    /** Waits for Supabase to restore persisted auth state before the UI chooses a start screen. */
    suspend fun hasExistingSession(): Boolean {
        if (!isConfigured) return false
        val status = supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        return status is SessionStatus.Authenticated
    }

    suspend fun signOut(context: Context) {
        supabase.auth.signOut()
        // Clear provider-side state so Change account can offer another Google account.
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun createNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
