package com.paycross.sdk.internal.ui

import org.junit.Assert.*
import org.junit.Test

class ThreeDsFormTest {

    @Test
    fun `challenge cReq is normalized to lowercase creq`() {
        assertEquals("creq=abc123", encodeFormData(mapOf("cReq" to "abc123")))
        assertEquals("creq=abc123", encodeFormData(mapOf("CReq" to "abc123")))
        assertEquals("creq=abc123", encodeFormData(mapOf("creq" to "abc123")))
    }

    @Test
    fun `fingerprint threeDSMethodData passes through and snake variant is normalized`() {
        assertEquals(
            "threeDSMethodData=eyJ0",
            encodeFormData(mapOf("threeDSMethodData" to "eyJ0"))
        )
        assertEquals(
            "threeDSMethodData=eyJ0",
            encodeFormData(mapOf("threeds_method_data" to "eyJ0"))
        )
    }

    @Test
    fun `empty values are dropped and other keys pass through url-encoded`() {
        assertEquals("", encodeFormData(mapOf("cReq" to "")))
        assertEquals("", encodeFormData(null))
        assertEquals("a+b=c%26d", encodeFormData(mapOf("a b" to "c&d")))
    }

    @Test
    fun `initial action load on a paycross host does not complete the step`() {
        val sandboxAction = "https://checkout-api.test-pay-cross.com/api/sandbox/fingerprint?tx=tx-1"
        assertFalse(isCompletionUrl(sandboxAction, sandboxAction))
        assertFalse(isCompletionUrl("$sandboxAction/", sandboxAction))
        assertTrue(
            isCompletionUrl(
                "https://checkout-api.test-pay-cross.com/api/notification/sandbox/3ds_fingerprint/tx-1",
                sandboxAction
            )
        )
        assertFalse(isCompletionUrl("https://acs.bank.com/3ds/next", sandboxAction))
    }

    @Test
    fun `return url detection matches paycross hosts only`() {
        assertTrue(isReturnUrl("https://checkout-api.test-pay-cross.com/api/notification/nuvei/3ds_challenge/tx-1"))
        assertTrue(isReturnUrl("https://checkout.pay-cross.com/api/notification/x"))
        assertTrue(isReturnUrl("https://pay-cross.com/done"))
        assertFalse(isReturnUrl("https://acs.bank.com/3ds/challenge"))
        assertFalse(isReturnUrl("https://evil-pay-cross.com/phish"))
        assertFalse(isReturnUrl("not a url"))
    }
}
