package com.paycross.sdk

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every result crosses a process boundary on its way back to the host app: the
 * payment sheet is a separate activity and the outcome travels as a parcelled
 * extra. A member that does not survive the trip is silently null on arrival,
 * which for [PayCrossResult.Pending] would mean a merchant losing the id they
 * need to reconcile with. Instrumented rather than local because a real
 * [Parcel] only exists on a device.
 */
@RunWith(AndroidJUnit4::class)
class PayCrossResultParcelTest {

    private fun roundTrip(result: PayCrossResult): PayCrossResult {
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(result, 0)
            parcel.setDataPosition(0)
            @Suppress("DEPRECATION")
            return requireNotNull(parcel.readParcelable(PayCrossResult::class.java.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun successCarriesTheSavedCardTokenAcrossTheParcel() {
        val restored = roundTrip(
            PayCrossResult.Success(
                transactionId = "tx-9",
                status = "success",
                amount = 9999,
                currency = "EUR",
                savedCardToken = "tok_abc123"
            )
        ) as PayCrossResult.Success

        assertEquals("tok_abc123", restored.savedCardToken)
    }

    @Test
    fun successWithoutASavedCardTokenSurvivesTheParcel() {
        val restored = roundTrip(
            PayCrossResult.Success("tx-9", "success", 9999, "EUR")
        ) as PayCrossResult.Success

        assertNull(restored.savedCardToken)
        assertEquals("tx-9", restored.transactionId)
    }

    @Test
    fun pendingSurvivesAParcelRoundTrip() {
        val restored = roundTrip(
            PayCrossResult.Pending(transactionId = "tx-7", reason = PendingReason.POLL_TIMEOUT)
        ) as PayCrossResult.Pending

        assertEquals("tx-7", restored.transactionId)
        assertEquals(PendingReason.POLL_TIMEOUT, restored.reason)
    }

    @Test
    fun pendingSurvivesAParcelRoundTripWithoutATransactionId() {
        val restored = roundTrip(
            PayCrossResult.Pending(transactionId = null, reason = PendingReason.SERVER_VERIFY)
        ) as PayCrossResult.Pending

        assertNull(restored.transactionId)
        assertEquals(PendingReason.SERVER_VERIFY, restored.reason)
    }
}
