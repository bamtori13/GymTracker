package com.gymtracker.app.data.cloud

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.gymtracker.app.R

/**
 * Credential Manager로 Google ID 토큰을 받아온다. Activity Context가 필요해서
 * (계정 선택 시트를 띄우므로) CloudBackupService와 분리해 둔다.
 *
 * serverClientId는 google-services.json에서 생성되는 문자열 리소스다.
 * Firebase Console에서 Google 공급자를 켠 뒤에 내려받은 JSON이어야 이 리소스가 생긴다.
 */
suspend fun requestGoogleIdToken(activityContext: Context): String {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(activityContext.getString(R.string.default_web_client_id))
        // false = 이 앱에 한 번도 로그인한 적 없는 계정도 목록에 보여준다(첫 로그인에 필요).
        .setFilterByAuthorizedAccounts(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    val response = try {
        CredentialManager.create(activityContext).getCredential(activityContext, request)
    } catch (e: GetCredentialException) {
        // 이 지점에서 실패하면 대개 Firebase Console에 SHA-1 지문이 등록되지 않은 것이다.
        error("Google 로그인에 실패했습니다: ${e.message}")
    }

    val credential = response.credential
    if (credential !is CustomCredential ||
        credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        error("사용 가능한 Google 계정이 없습니다")
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}
