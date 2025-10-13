package code.api.util

import code.api.util.APIUtil.OBPReturnType
import code.api.util.ErrorUtil.apiFailure
import code.api.util.newstyle.RegulatedEntityNewStyle.getRegulatedEntitiesNewStyle
import code.api.{ObpApiFailure, RequestHeader}
import code.consumer.Consumers
import code.model.Consumer
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{RegulatedEntityTrait, User}
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.util.Helpers

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security._
import java.security.cert.{CertificateFactory, X509Certificate}
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.{Base64, Date, UUID}
import scala.concurrent.Future
import scala.util.matching.Regex

object BerlinGroupSigning extends MdcLoggable {

  lazy val p12Path = APIUtil.getPropsValue("truststore.path.tpp_signature")
    .or(APIUtil.getPropsValue("truststore.path")).getOrElse("")
  lazy val p12Password = APIUtil.getPropsValue("truststore.password.tpp_signature", "")
  lazy val alias = APIUtil.getPropsValue("truststore.alias.tpp_signature", "")
  // Load the private key and certificate from the keystore
  lazy val (privateKey, certificate) = P12StoreUtil.loadPrivateKey(
    p12Path = p12Path, // Replace with the actual file path
    p12Password = p12Password, // Replace with the keystore password
    alias = alias // Replace with the key alias
  )

  // Define a regular expression to extract the value of CN, allowing for optional spaces around '='
  val cnPattern: Regex = """CN\s*=\s*([^,]+)""".r
  // Define a regular expression to extract the value of EMAILADDRESS, allowing for optional spaces around '='
  val emailPattern: Regex = """EMAILADDRESS\s*=\s*([^,]+)""".r
  // Define a regular expression to extract the value of Organization, allowing for optional spaces around '='
  val organisationlPattern: Regex = """O\s*=\s*([^,]+)""".r

  // Step 1: Calculate Digest (SHA-256 Hash of the Body)
  def generateDigest(body: String): String = {
    val sha256Digest = MessageDigest.getInstance("SHA-256")
    val digest = sha256Digest.digest(body.getBytes("UTF-8"))
    val base64Digest = Base64.getEncoder.encodeToString(digest)
    s"SHA-256=$base64Digest"
  }

  // Step 2: Create Signing String (Concatenation of required headers)
  def createSigningString(headers: Map[String, String]): String = {
    // headers=”digest date x-request-id tpp-redirect-uri”
    val orderedKeys = List(
      RequestHeader.Digest,
      RequestHeader.Date,
      RequestHeader.`X-Request-ID`,
      //RequestHeader.`TPP-Redirect-URI`,
    ) // Example fields to be signed
    orderedKeys.flatMap(key => headers.get(key).map(value => s"${key.toLowerCase()}: $value")).mkString("\n")
  }

  // Step 3: Generate Signature using RSA Private Key
  def signString(signingString: String, privateKeyPem: String): String = {
    val privateKey: PrivateKey = loadPrivateKey(privateKeyPem)
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initSign(privateKey)
    signature.update(signingString.getBytes(StandardCharsets.UTF_8))
    Base64.getEncoder.encodeToString(signature.sign())
  }

  // Load RSA Private Key from PEM String
  def loadPrivateKey(pem: String): PrivateKey = {
    val keyString = pem
      .replaceAll("-----BEGIN .*?PRIVATE KEY-----", "") // Remove headers
      .replaceAll("-----END .*?PRIVATE KEY-----", "")
      .replaceAll("\\s", "") // Remove all whitespace and new lines

    val decodedKey = Base64.getDecoder.decode(keyString)
    val keySpec = new PKCS8EncodedKeySpec(decodedKey)
    KeyFactory.getInstance("RSA").generatePrivate(keySpec)
  }

  // Step 4: Attach Certificate (Load from PEM String)


  // Step 5: Verify Request on ASPSP Side
  def verifySignature(signingString: String, signatureStr: String, publicKey: PublicKey): Boolean = {
    logger.debug(s"signingString: $signingString")
    logger.debug(s"signatureStr: $signatureStr")
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initVerify(publicKey)
    signature.update(signingString.getBytes(StandardCharsets.UTF_8))
    signature.verify(Base64.getDecoder.decode(signatureStr))
  }

  def parseCertificate(certString: String): X509Certificate = {
    // Decode Base64 certificate
    val certBytes = Base64.getDecoder.decode(
      certString.replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .replaceAll("\\s", "")
    )

    // Parse certificate
    val certFactory = CertificateFactory.getInstance("X.509")
    val certificate = certFactory.generateCertificate(new java.io.ByteArrayInputStream(certBytes)).asInstanceOf[X509Certificate]
    certificate
  }

  def getRegulatedEntityByCertificate(certificate: X509Certificate, callContext: Option[CallContext]): Future[List[RegulatedEntityTrait]] = {
    val issuerCN = cnPattern.findFirstMatchIn(certificate.getIssuerDN.getName)
      .map(_.group(1).trim)
      .getOrElse("CN not found")

    val serialNumber = certificate.getSerialNumber.toString

    for {
      (entities, _) <- getRegulatedEntitiesNewStyle(callContext)
    } yield {
      entities.filter { entity =>
        val attrs = entity.attributes.getOrElse(Nil)

        // Extract serial number and CA name from attributes
        val serialOpt = attrs.collectFirst { case a if a.name.equalsIgnoreCase("CERTIFICATE_SERIAL_NUMBER") => a.value.trim }
        val caNameOpt = attrs.collectFirst { case a if a.name.equalsIgnoreCase("CERTIFICATE_CA_NAME") => a.value.trim }

        val serialMatches = serialOpt.contains(serialNumber)
        val caNameMatches = caNameOpt.exists(_.equalsIgnoreCase(issuerCN))

        val isMatch = serialMatches && caNameMatches

        // Log everything for debugging
        val serialLog = serialOpt.getOrElse("N/A")
        val caNameLog = caNameOpt.getOrElse("N/A")
        val allAttrsLog = attrs.map(a => s"${a.name}='${a.value}'").mkString(", ")

        if (isMatch)
          logger.debug(s"[MATCH] Entity '${entity.entityName}' (Code: ${entity.entityCode}) matches CN='$issuerCN', Serial='$serialNumber' " +
            s"(Attributes found: Serial='$serialLog', CA Name='$caNameLog', All Attributes: [$allAttrsLog])")

        isMatch
      }
    }
  }


  /**
   * Verifies Signed Request. It assumes that Customers has a sored certificate.
   *
   * @param body          of the signed request
   * @param verb          GET, POST, DELETE, etc.
   * @param url           of the the signed request. For example: /berlin-group/v1.3/payments/sepa-credit-transfers
   * @param reqHeaders    All request headers of the signed request
   * @param forwardResult Propagated result of calling function
   * @return Propagated result of calling function or signing request error
   */
  def verifySignedRequest(body: Box[String], verb: String, url: String, reqHeaders: List[HTTPParam], forwardResult: (Box[User], Option[CallContext])): (Box[User], Option[CallContext]) = {
    def checkRequestIsSigned(requestHeaders: List[HTTPParam]): Boolean = {
      requestHeaders.exists(_.name.toLowerCase() == RequestHeader.`TPP-Signature-Certificate`.toLowerCase()) &&
      requestHeaders.exists(_.name.toLowerCase() == RequestHeader.Signature.toLowerCase()) &&
      requestHeaders.exists(_.name.toLowerCase() == RequestHeader.Digest.toLowerCase())
    }
    checkRequestIsSigned(forwardResult._2.map(_.requestHeaders).getOrElse(Nil)) match {
      case false =>
        forwardResult
      case true =>
        val requestHeaders = forwardResult._2.map(_.requestHeaders).getOrElse(Nil)
        val certificate = getCertificateFromTppSignatureCertificate(requestHeaders)
        X509.validateCertificate(certificate) match {
          case Full(true) => // PEM certificate is ok
            val generatedDigest = generateDigest(body.getOrElse(""))
            val requestHeaderDigest = getHeaderValue(RequestHeader.Digest, requestHeaders)
            if(generatedDigest == requestHeaderDigest) { // Verifying the Hash in the Digest Field
              val signatureHeaderValue = getHeaderValue(RequestHeader.Signature, requestHeaders)
              val signature = parseSignatureHeader(signatureHeaderValue).getOrElse("signature", "NONE")
              val headersToSign = parseSignatureHeader(signatureHeaderValue).getOrElse("headers", "").split(" ").toList
              val sn = parseSignatureHeader(signatureHeaderValue).getOrElse("keyId", "").split(" ").toList
              val headers = headersToSign.map(h =>
                if (h.toLowerCase() == RequestHeader.Digest.toLowerCase()) {
                  s"$h: $generatedDigest"
                } else {
                  s"$h: ${getHeaderValue(h, requestHeaders)}"
                }
              )
              val signingString = headers.mkString("\n")
              val isVerified = verifySignature(signingString, signature, certificate.getPublicKey)
              val isValidated = CertificateVerifier.validateCertificate(certificate)
              val bypassValidation = APIUtil.getPropsAsBoolValue("bypass_tpp_signature_validation", defaultValue = false)
              (isVerified, isValidated) match {
                case (true, true) => forwardResult
                case (true, false) if bypassValidation => forwardResult
                case (true, false) => apiFailure(ErrorMessages.X509PublicKeyCannotBeValidated, 401)(forwardResult)
                case (false, _) => apiFailure(ErrorMessages.X509PublicKeyCannotVerify, 401)(forwardResult)
              }
            } else { // The two DIGEST hashes do NOT match, the integrity of the request body is NOT confirmed.
              logger.debug(s"Generated digest: $generatedDigest")
              logger.debug(s"Request header digest: $requestHeaderDigest")
              apiFailure(ErrorMessages.X509PublicKeyCannotVerify, 401)(forwardResult)
            }
          case Failure(msg, t, c) => (Failure(msg, t, c), forwardResult._2) // PEM certificate is not valid
          case _ => apiFailure(ErrorMessages.X509GeneralError, 401)(forwardResult) // PEM certificate cannot be validated
        }
    }
  }

  def getHeaderValue(name: String, requestHeaders: List[HTTPParam]): String = {
    requestHeaders.find(_.name.toLowerCase() == name.toLowerCase()).map(_.values.mkString)
      .getOrElse(SecureRandomUtil.csprng.nextLong().toString)
  }
  def getCertificateFromTppSignatureCertificate(requestHeaders: List[HTTPParam]): X509Certificate = {
    val certificate = getHeaderValue(RequestHeader.`TPP-Signature-Certificate`, requestHeaders)
    // Decode the Base64 string
    val decodedBytes = Base64.getDecoder.decode(certificate)
    // Convert the bytes to a string (it could be PEM format for public key)
    val decodedString = new String(decodedBytes, StandardCharsets.UTF_8)

    val certificatePemString = getCertificatePem(decodedString)
    parseCertificate(certificatePemString)
  }

  private def getCertificatePem(decodedString: String) = {
    // Extract the certificate portion from the decoded string
    val certStart = "-----BEGIN CERTIFICATE-----"
    val certEnd = "-----END CERTIFICATE-----"

    // Find the start and end indices of the certificate
    val startIndex = decodedString.indexOf(certStart)
    val endIndex = decodedString.indexOf(certEnd, startIndex) + certEnd.length

    if (startIndex >= 0 && endIndex >= 0) {
      // Extract and print the certificate part
      val extractedCert = decodedString.substring(startIndex, endIndex)
      logger.debug("Extracted Certificate:")
      logger.debug(extractedCert)
      extractedCert
    } else {
      logger.debug("Certificate not found in the decoded string.")
      ""
    }
  }
  private def getPrivateKeyPem(decodedString: String) = {
    // Extract the certificate portion from the decoded string
    val certStart = "-----BEGIN PRIVATE KEY-----"
    val certEnd = "-----END PRIVATE KEY-----"

    // Find the start and end indices of the certificate
    val startIndex = decodedString.indexOf(certStart)
    val endIndex = decodedString.indexOf(certEnd, startIndex) + certEnd.length

    if (startIndex >= 0 && endIndex >= 0) {
      // Extract and print the certificate part
      val extractedCert = decodedString.substring(startIndex, endIndex)
      logger.debug("|---> Extracted Private Key:")
      logger.debug(extractedCert)
      extractedCert
    } else {
      logger.debug("|---> Private Key not found in the decoded string.")
      ""
    }
  }

  def parseSignatureHeader(signatureHeader: String): Map[String, String] = {
    val regex = new Regex("""(\w+)\s*=\s*"([^"]*)"""", "key", "value")
    regex.findAllMatchIn(signatureHeader).map(m => m.group("key") -> m.group("value")).toMap
  }

  def getCurrentDate: String = {
    val sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
    sdf.format(new Date())
  }

  def getOrCreateConsumer(requestHeaders: List[HTTPParam], forwardResult: (Box[User], Option[CallContext])): OBPReturnType[Box[User]] = {
    val tppSignatureCert: String = APIUtil.getRequestHeader(RequestHeader.`TPP-Signature-Certificate`, requestHeaders)
    if (tppSignatureCert.isEmpty) {
      Future(forwardResult)
    } else { // Dynamic consumer creation/update works in case that RequestHeader.`TPP-Signature-Certificate` is present
      val certificate = getCertificateFromTppSignatureCertificate(requestHeaders)

      val extractedEmail = emailPattern.findFirstMatchIn(certificate.getSubjectDN.getName).map(_.group(1))
      val extractOrganisation = organisationlPattern.findFirstMatchIn(certificate.getSubjectDN.getName).map(_.group(1))

      for {
        entities <- getRegulatedEntityByCertificate(certificate, forwardResult._2)
      } yield {
        entities match {
          case Nil =>
            (ObpApiFailure(ErrorMessages.RegulatedEntityNotFoundByCertificate, 401, forwardResult._2), forwardResult._2)

          case single :: Nil =>
            val idno = single.entityCode
            val entityName = Option(single.entityName)

            val consumer: Box[Consumer] = Consumers.consumers.vend.getOrCreateConsumer(
              consumerId = None,
              key = Some(Helpers.randomString(40).toLowerCase),
              secret = Some(Helpers.randomString(40).toLowerCase),
              aud = None,
              azp = Some(idno),
              iss = Some(RequestHeader.`TPP-Signature-Certificate`),
              sub = None,
              Some(true),
              name = entityName,
              appType = None,
              description = Some(s"Certificate serial number:${certificate.getSerialNumber}"),
              developerEmail = extractedEmail,
              redirectURL = None,
              createdByUserId = None,
              certificate = None,
              logoUrl = code.api.Constant.consumerDefaultLogoUrl
            )

            consumer match {
              case Full(consumer) =>
                val certificateFromHeader = getHeaderValue(RequestHeader.`TPP-Signature-Certificate`, requestHeaders)
                Consumers.consumers.vend.updateConsumer(
                  id = consumer.id.get,
                  name = entityName,
                  certificate = Some(certificateFromHeader)
                ) match {
                  case Full(updatedConsumer) =>
                    (forwardResult._1, forwardResult._2.map(_.copy(consumer = Full(updatedConsumer))))
                  case error =>
                    logger.debug(error)
                    (Failure(s"${ErrorMessages.CreateConsumerError} Regulated entity: $idno"), forwardResult._2)
                }
              case error =>
                logger.debug(error)
                (Failure(s"${ErrorMessages.CreateConsumerError} Regulated entity: $idno"), forwardResult._2)
            }

          case multiple =>
            val names = multiple.map(e => s"'${e.entityName}' (Code: ${e.entityCode})").mkString(", ")
            (ObpApiFailure(s"${ErrorMessages.RegulatedEntityAmbiguityByCertificate}: multiple TPPs found: $names", 401, forwardResult._2), forwardResult._2)
        }
      }
    }
  }

  // Example Usage
  def main(args: Array[String]): Unit = {
    // Digest for request
    val body = new String(Files.readAllBytes(Paths.get("/path/to/request_body.json")), "UTF-8")
    val digest = generateDigest(body)

    // Generate UUID for X-Request-ID
    val xRequestId = UUID.randomUUID().toString

    // Get current date in RFC 7231 format
    val dateHeader = getCurrentDate


    val redirectUri = "www.redirect-uri.com"
    val headers = Map(
      RequestHeader.Digest -> s"SHA-256=$digest",
      RequestHeader.`X-Request-ID` -> xRequestId,
      RequestHeader.Date -> dateHeader,
      RequestHeader.`TPP-Redirect-URI` -> redirectUri,
    )

    val signingString = createSigningString(headers)

    // Load PEM files as strings
    val certificatePath = "/path/to/certificate.pem"
    val certificateFullString = new String(Files.readAllBytes(Paths.get(certificatePath)))


    val signature = signString(signingString, P12StoreUtil.privateKeyToPEM(privateKey))

    println(s"1) Digest: $digest")
    println(s"2) ${RequestHeader.`X-Request-ID`}: $xRequestId")
    println(s"3) ${RequestHeader.Date}: $dateHeader")
    println(s"4) ${RequestHeader.`TPP-Redirect-URI`}: $redirectUri")
    val signatureHeaderValue =
      s"""keyId="SN=43A, CA=CN=MAIB Prisacaru Sergiu (Test), O=MAIB", algorithm="rsa-sha256", headers="digest date x-request-id", signature="$signature""""
    println(s"5) Signature: $signatureHeaderValue")

    // Convert public certificate to Base64 for Signature-Certificate header
    val certificateBase64 = Base64.getEncoder.encodeToString(certificateFullString.getBytes(StandardCharsets.UTF_8))
    println(s"6.1) TPP-Signature-Certificate: $certificateBase64")
    val certificate2Base64 = Base64.getEncoder.encodeToString(P12StoreUtil.certificateToPEM(certificate).getBytes(StandardCharsets.UTF_8))
    println(s"6.2) TPP-Signature-Certificate 2: ${certificate2Base64}")

    val isVerified = verifySignature(signingString, signature, certificate.getPublicKey)
    println(s"Signature Verification: $isVerified")

    val parsedSignature = parseSignatureHeader(signatureHeaderValue)
    println(s"Parsed Signature Header: $parsedSignature")
  }
}
