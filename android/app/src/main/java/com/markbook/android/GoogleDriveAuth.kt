package com.markbook.android

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

/** Google Play services owns the account session; Markbook never persists OAuth tokens. */
class GoogleDriveAuth(private val context: Context) {
    fun signInIntent(): Intent = GoogleSignIn.getClient(context, signInOptions()).signInIntent

    fun currentAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun isAuthorized(account: GoogleSignInAccount?): Boolean =
        account != null && GoogleSignIn.hasPermissions(account, DRIVE_SCOPE)

    @Throws(ApiException::class)
    fun accountFromResult(data: Intent?): GoogleSignInAccount? =
        GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)

    /** Called only on the background sync worker. The returned token lives only in that call. */
    fun accessToken(account: GoogleSignInAccount): String =
        GoogleAuthUtil.getToken(
            context,
            requireNotNull(account.account) { "Google account is unavailable" },
            "oauth2:$DRIVE_SCOPE_URI"
        )

    private fun signInOptions(): GoogleSignInOptions = GoogleSignInOptions.Builder(
        GoogleSignInOptions.DEFAULT_SIGN_IN
    ).requestEmail().requestScopes(DRIVE_SCOPE).build()

    companion object {
        const val DRIVE_SCOPE_URI = "https://www.googleapis.com/auth/drive"
        private val DRIVE_SCOPE = Scope(DRIVE_SCOPE_URI)
    }
}
