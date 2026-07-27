package top.focess.keystead.client

import java.time.Clock
import java.time.Duration
import java.time.Instant
import top.focess.keystead.crypto.CryptoException
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.model.SecretType
import top.focess.keystead.share.ShareContents
import top.focess.keystead.share.ShareDraft
import top.focess.keystead.share.ShareService

/**
 * Bridges core's single-secret share format with Keystead Server hosting.
 *
 * The share string itself (`keystead-share:v1:...`) is the encrypted payload; the temp
 * passphrase is the only key. This service mints that string via core [ShareService], hosts
 * the opaque blob on the server via [KeysteadServerClient.mintShare], and redeems + opens it
 * via [redeem]. The server never sees the passphrase or plaintext - only the opaque blob.
 *
 * Both the share string's cryptographic expiry and the server's hosting expiry are set from
 * the same [ShareTtl] selection, so a share self-destructs at decrypt time even if the server
 * keeps the blob past its window.
 */
class ShareExchange(
    crypto: DefaultCryptoService = DefaultCryptoService(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val shareService = ShareService(crypto, clock)

    /**
     * The hosting windows offered to the sharer. The server rejects anything past its configured
     * maximum (30 days by default), so the options stay within that ceiling.
     */
    enum class ShareTtl(val label: String, private val duration: Duration) {
        ONE_HOUR("1 hour", Duration.ofHours(1)),
        ONE_DAY("1 day", Duration.ofDays(1)),
        ONE_WEEK("1 week", Duration.ofDays(7)),
        THIRTY_DAYS("30 days", Duration.ofDays(30)),
        ;

        fun expiresAtFrom(now: Instant): Instant = now.plus(duration)
    }

    data class MintedShare(val code: String, val expiresAt: Instant)

    /**
     * Mints a hosted share for [payload] (a free-form note) and returns the server short code.
     *
     * The [passphrase] char array is wiped by core on return (including on failure), so the
     * caller must not reuse it afterwards.
     */
    fun mint(
        client: KeysteadServerClient,
        title: String,
        payload: String,
        passphrase: CharArray,
        ttl: ShareTtl,
        burnAfterReading: Boolean,
    ): MintedShare {
        require(title.isNotBlank()) { "Share title must not be blank" }
        require(payload.isNotEmpty()) { "Share payload must not be empty" }
        val now = clock.instant()
        val expiresAt = ttl.expiresAtFrom(now)
        val draft =
            ShareDraft(
                SecretType.SECURE_NOTE,
                title,
                mapOf("body" to payload),
                null,
                expiresAt,
                0,
            )
        val shareString = shareService.create(draft, passphrase)
        val minted = client.mintShare(shareString, expiresAt, burnAfterReading)
        return MintedShare(minted.code, minted.expiresAt)
    }

    /**
     * Redeems [code] from the server and opens the share string with [passphrase].
     *
     * For burn-after-reading shares the server deletes the blob on redeem, so a wrong passphrase
     * cannot be retried - callers should warn the user before redeeming. The [passphrase] char
     * array is wiped by core on return (including on failure).
     *
     * A wrong passphrase (or a tampered share) fails AEAD verification in core; that low-level
     * [CryptoException] is remapped to a user-facing message so the UI does not surface a raw
     * "could not decrypt payload" diagnostic.
     */
    fun redeem(client: KeysteadServerClient, code: String, passphrase: CharArray): ShareContents {
        require(code.isNotBlank()) { "Share code must not be blank" }
        val shareString = client.redeemShare(code)
        return try {
            shareService.open(shareString, passphrase)
        } catch (e: CryptoException) {
            throw IllegalStateException(
                "Could not open the share; the passphrase is wrong or the share was tampered with.",
                e,
            )
        }
    }

    companion object {
        /**
         * Mirrors core's share passphrase policy so the UI can pre-validate the temp passphrase
         * before mint/redeem: at least 12 characters spanning at least 3 of the lower/upper/digit/
         * symbol classes. Whitespace and control characters do not count toward the symbol class,
         * matching [top.focess.keystead.share.ShareService] exactly so the UI never disables an
         * action for a passphrase core would accept.
         */
        fun meetsPassphrasePolicy(passphrase: String): Boolean {
            if (passphrase.length < 12) {
                return false
            }
            var lower = false
            var upper = false
            var digit = false
            var symbol = false
            for (character in passphrase) {
                when {
                    character.isLowerCase() -> lower = true
                    character.isUpperCase() -> upper = true
                    character.isDigit() -> digit = true
                    !character.isWhitespace() && !character.isISOControl() -> symbol = true
                }
            }
            var classes = 0
            if (lower) classes++
            if (upper) classes++
            if (digit) classes++
            if (symbol) classes++
            return classes >= 3
        }
    }
}
