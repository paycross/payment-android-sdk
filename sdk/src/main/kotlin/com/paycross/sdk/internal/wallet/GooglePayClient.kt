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

internal object GooglePayClient {
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
            // Without working Google Play services the Google Pay button is not shown.
            null
        }

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
        googlePayMerchantId: String?,
        totalPriceLabel: String
    ): Task<PaymentData> {
        val json = GooglePayRequests.buildPaymentDataRequest(
            claims,
            sessionData,
            googlePayMerchantId,
            totalPriceLabel
        )
        return client.loadPaymentData(PaymentDataRequest.fromJson(json.toString()))
    }
}
