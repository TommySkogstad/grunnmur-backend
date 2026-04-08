package no.grunnmur

import kotlinx.serialization.Serializable

/**
 * Resultat fra TOTP-oppsett.
 *
 * @param secret Kryptert hemmelighet (for lagring i database)
 * @param qrUri otpauth://-URI for QR-kode-generering
 */
@Serializable
data class TotpSetupResult(
    val secret: String,
    val qrUri: String
)

/**
 * Resultat fra TOTP-bekreftelse.
 *
 * @param backupCodes Engangskoder for gjenoppretting
 */
@Serializable
data class TotpConfirmResult(
    val backupCodes: List<String>
)
