package com.paycross.sdk.internal.wallet

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.SessionData
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Thin boundary around Play Services Wallet. Everything decidable without
 * Google Play services lives in [GooglePayRequests]; this class only converts
 * the JSON requests at the GMS edge and awaits the resulting tasks.
 */
internal object GooglePayClient {

    // The web checkout decouples the Google Pay environment from the API host
    // via an env var; here it derives from the SDK environment instead — one
    // fewer knob, and a staging build can never accidentally open a
    // PRODUCTION sheet against real instruments.
    fun walletEnvironment(environment: PayCrossEnvironment): Int = when (environment) {
        PayCrossEnvironment.STAGING -> WalletConstants.ENVIRONMENT_TEST
        PayCrossEnvironment.PRODUCTION -> WalletConstants.ENVIRONMENT_PRODUCTION
    }

    fun createPaymentsClient(context: Context, environment: PayCrossEnvironment): PaymentsClient? =
        try {
            Wallet.getPaymentsClient(
                context,
                Wallet.WalletOptions.Builder()
                    .setEnvironment(walletEnvironment(environment))
                    .build()
            )
        } catch (e: RuntimeException) {
            // No Google Play services on the device (or a broken installation)
            // simply means no Google Pay button; the card form is unaffected.
            null
        }

    /**
     * Resolves device-level readiness. Any failure — GMS missing, API error,
     * task exception — means the button silently stays hidden.
     */
    suspend fun isReadyToPay(client: PaymentsClient): Boolean {
        val request = try {
            IsReadyToPayRequest.fromJson(GooglePayRequests.buildIsReadyToPayRequest().toString())
        } catch (e: RuntimeException) {
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            client.isReadyToPay(request).addOnCompleteListener { task ->
                val ready = task.isSuccessful && task.result == true
                if (continuation.isActive) continuation.resume(ready)
            }
        }
    }

    fun loadPaymentDataTask(
        client: PaymentsClient,
        claims: JwtClaims,
        sessionData: SessionData?,
        googlePayMerchantId: String?
    ): Task<PaymentData> {
        val json = GooglePayRequests.buildPaymentDataRequest(claims, sessionData, googlePayMerchantId)
        return client.loadPaymentData(PaymentDataRequest.fromJson(json.toString()))
    }
}
