package code.api.berlin.group.signing

import java.nio.charset.StandardCharsets
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, MessageDigest, PrivateKey, Signature}
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.{Base64, UUID}
import scala.util.{Failure, Success, Try}

/**
 * PSD2 Request Signer for Berlin Group API calls
 * 
 * This utility provides cryptographic signing for Berlin Group PSD2 API requests.
 * It follows the HTTP signature standard required by PSD2 regulations.
 * 
 * Usage:
 *   val signer = new PSD2RequestSigner(privateKeyPem, certificatePem)
 *   val headers = signer.signRequest(requestBody)
 */
class PSD2RequestSigner(
  privateKeyPem: String,
  certificatePem: String,
  keyId: String = "SN=1082, CA=CN=MAIB Prisacaru Sergiu (Test), O=MAIB"
) {

  // Parse private key once during initialization
  private val privateKey: PrivateKey = parsePrivateKey(privateKeyPem) match {
    case Success(key) => key
    case Failure(ex) => throw new IllegalArgumentException(s"Invalid private key: ${ex.getMessage}", ex)
  }

  // Encode certificate once during initialization
  private val certificateBase64: String = Base64.getEncoder.encodeToString(
    certificatePem.getBytes(StandardCharsets.UTF_8)
  )

  /**
   * Sign a Berlin Group API request and return headers
   * 
   * @param requestBody The JSON request body as string
   * @param psuDeviceId Optional PSU device ID (default: "device-1234567890")
   * @param psuDeviceName Optional PSU device name (default: "Kalina-PC")
   * @param psuIpAddress Optional PSU IP address (default: "psu-service.local")
   * @param tppRedirectUri Optional TPP redirect URI (default: "tppapp://example.com/redirect")
   * @param tppNokRedirectUri Optional TPP error redirect URI (default: "https://example.com/redirect")
   * @return Map of HTTP headers for the signed request
   */
  def signRequest(
    requestBody: String,
    psuDeviceId: String = "device-1234567890",
    psuDeviceName: String = "Kalina-PC", 
    psuIpAddress: String = "psu-service.local", // Use DNS/hostname instead of raw IP
    tppRedirectUri: String = "tppapp://example.com/redirect",
    tppNokRedirectUri: String = "https://example.com/redirect"
  ): Map[String, String] = {

    // Generate required header values
    val xRequestId = UUID.randomUUID().toString
    val dateHeader = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)
    val digestHeader = createDigestHeader(requestBody)

    // Create signature string according to PSD2 specification
    val dataToSign = s"digest: $digestHeader\ndate: $dateHeader\nx-request-id: $xRequestId"
    val signature = signData(dataToSign)

    // Create signature header
    val signatureHeader = s"""keyId="$keyId", algorithm="rsa-sha256", headers="digest date x-request-id", signature="$signature""""

    // Return complete headers map
    Map(
      "Content-Type" -> "application/json",
      "Date" -> dateHeader,
      "X-Request-ID" -> xRequestId,
      "Digest" -> digestHeader,
      "Signature" -> signatureHeader,
      "TPP-Signature-Certificate" -> certificateBase64,
      "PSU-Device-ID" -> psuDeviceId,
      "PSU-Device-Name" -> psuDeviceName,
      "PSU-IP-Address" -> psuIpAddress,
      "TPP-Redirect-URI" -> tppRedirectUri,
      "TPP-Nok-Redirect-URI" -> tppNokRedirectUri
    )
  }

  /**
   * Create SHA-256 digest header for request body
   */
  private def createDigestHeader(requestBody: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8))
    val base64Hash = Base64.getEncoder.encodeToString(hashBytes)
    s"SHA-256=$base64Hash"
  }

  /**
   * Sign data using RSA-SHA256 algorithm
   */
  private def signData(dataToSign: String): String = {
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initSign(privateKey)
    signature.update(dataToSign.getBytes(StandardCharsets.UTF_8))
    val signatureBytes = signature.sign()
    Base64.getEncoder.encodeToString(signatureBytes)
  }

  /**
   * Parse PEM-formatted private key
   */
  private def parsePrivateKey(privateKeyPem: String): Try[PrivateKey] = Try {
    val cleanedPem = privateKeyPem
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replace("-----BEGIN RSA PRIVATE KEY-----", "")
      .replace("-----END RSA PRIVATE KEY-----", "")
      .replaceAll("\\s", "")

    val keyBytes = Base64.getDecoder.decode(cleanedPem)
    val keySpec = new PKCS8EncodedKeySpec(keyBytes)
    val keyFactory = KeyFactory.getInstance("RSA")
    keyFactory.generatePrivate(keySpec)
  }
}



/**
 * Simple trait for mixing into test classes to provide PSD2 signing capabilities
 */
trait PSD2SigningSupport {

  /**
   * Override these in your test class to provide actual certificate content
   */
  def berlinGroupPrivateKey: String = throw new NotImplementedError("berlinGroupPrivateKey must be implemented")
  def berlinGroupCertificate: String = throw new NotImplementedError("berlinGroupCertificate must be implemented")
  def berlinGroupKeyId: String = "SN=1082, CA=CN=MAIB Prisacaru Sergiu (Test), O=MAIB"

  private lazy val psd2Signer = new PSD2RequestSigner(berlinGroupPrivateKey, berlinGroupCertificate, berlinGroupKeyId)

  /**
   * Sign a Berlin Group request and return headers
   */
  def signPSD2Request(requestBody: String): Map[String, String] = {
    psd2Signer.signRequest(requestBody)
  }

  /**
   * Sign a Berlin Group request with custom PSU parameters
   */
  def signPSD2Request(
                       requestBody: String,
                       psuDeviceId: String,
                       psuDeviceName: String,
                       psuIpAddress: String,
                       tppRedirectUri: String = "tppapp://example.com/redirect",
                       tppNokRedirectUri: String = "https://example.com/redirect"
                     ): Map[String, String] = {
    psd2Signer.signRequest(requestBody, psuDeviceId, psuDeviceName, psuIpAddress, tppRedirectUri, tppNokRedirectUri)
  }
}