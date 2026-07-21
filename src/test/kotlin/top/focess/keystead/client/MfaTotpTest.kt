package top.focess.keystead.client

import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MfaTotpTest {

    @Test
    fun parsesDigitsAndPeriodFromOtpAuthUri() {
        val uri = "otpauth://totp/Keystead%3Aalice?secret=GEZDGNBVGY3TQOJQ&issuer=Keystead&algorithm=SHA1&digits=8&period=60"
        assertEquals(8, MfaTotp.digits(uri))
        assertEquals(60, MfaTotp.period(uri))
    }

    @Test
    fun defaultsDigitsAndPeriodWhenUriIsNull() {
        assertEquals(6, MfaTotp.digits(null))
        assertEquals(30, MfaTotp.period(null))
    }

    @Test
    fun defaultsDigitsAndPeriodWhenUriOmitsParameters() {
        val uri = "otpauth://totp/Keystead%3Aalice?secret=GEZDGNBVGY3TQOJQ"
        assertEquals(6, MfaTotp.digits(uri))
        assertEquals(30, MfaTotp.period(uri))
    }

    @Test
    fun generatesRfc6238CodeAtSixDigits() {
        val seed = rfcBase32Seed()
        val uri = "otpauth://totp/Keystead%3Aalice?secret=$seed&digits=6&period=30"
        // RFC 6238 / 4226: counter 1 (t=59, 30s period) -> 287082 at six digits.
        assertEquals("287082", MfaTotp.currentCode(seed, uri, Instant.ofEpochSecond(59L)))
    }

    @Test
    fun generatesRfc6238CodeAtEightDigits() {
        val seed = rfcBase32Seed()
        val uri = "otpauth://totp/Keystead%3Aalice?secret=$seed&digits=8&period=30"
        assertEquals("94287082", MfaTotp.currentCode(seed, uri, Instant.ofEpochSecond(59L)))
        assertEquals("89005924", MfaTotp.currentCode(seed, uri, Instant.ofEpochSecond(1234567890L)))
    }

    @Test
    fun returnsNullForBlankSeed() {
        assertNull(MfaTotp.currentCode("  ", null, Instant.EPOCH))
    }

    @Test
    fun secondsRemainingCountsDownWithinPeriod() {
        assertEquals(1, MfaTotp.secondsRemaining(30, Instant.ofEpochSecond(59L)))
        assertEquals(30, MfaTotp.secondsRemaining(30, Instant.ofEpochSecond(60L)))
        assertEquals(15, MfaTotp.secondsRemaining(30, Instant.ofEpochSecond(45L)))
    }

    private fun rfcBase32Seed(): String {
        val secret = "12345678901234567890".toByteArray(StandardCharsets.US_ASCII)
        return base32Encode(secret)
    }

    /** Mirrors DefaultMfaSecretGenerator's Base32 encoder for the RFC test seed. */
    private fun base32Encode(input: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val output = StringBuilder((input.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (value in input) {
            buffer = (buffer shl 8) or (value.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                output.append(alphabet[(buffer shr (bitsLeft - 5)) and 0x1f])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            output.append(alphabet[(buffer shl (5 - bitsLeft)) and 0x1f])
        }
        return output.toString()
    }
}
