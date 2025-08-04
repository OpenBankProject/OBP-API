package code.api.util

import code.util.Helper.MdcLoggable

import java.io.{ByteArrayInputStream, FileInputStream}
import java.security.KeyStore
import java.security.cert._
import java.util.{Base64, Collections}
import javax.net.ssl.TrustManagerFactory
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object CertificateVerifier extends MdcLoggable {

  /**
   * Loads a trust store (`.p12` file) from a configured path.
   *
   * This function:
   * - Reads the trust store password from the application properties (`truststore.path.tpp_signature`).
   * - Uses Java's `KeyStore` class to load the certificates.
   *
   * @return An `Option[KeyStore]` containing the loaded trust store, or `None` if loading fails.
   */
  private def loadTrustStore(): Option[KeyStore] = {
    val trustStorePath = APIUtil.getPropsValue("truststore.path.tpp_signature")
      .or(APIUtil.getPropsValue("truststore.path")).getOrElse("")
    val trustStorePassword = APIUtil.getPropsValue("truststore.password.tpp_signature", "").toCharArray

    Try {
      val trustStore = KeyStore.getInstance("PKCS12")
      val trustStoreInputStream = new FileInputStream(trustStorePath)
      try {
        trustStore.load(trustStoreInputStream, trustStorePassword)
      } finally {
        trustStoreInputStream.close()
      }
      trustStore
    } match {
      case Success(store) =>
        logger.info(s"Loaded trust store from: $trustStorePath")
        Some(store)
      case Failure(e) =>
        logger.info(s"Failed to load trust store: ${e.getMessage}")
        None
    }
  }

  /**
   * Verifies an X.509 certificate against the loaded trust store.
   *
   * This function:
   * - Parses the PEM certificate into an `X509Certificate` using `parsePemToX509Certificate`.
   * - Loads the trust store using `loadTrustStore()`.
   * - Extracts trusted root CAs from the trust store.
   * - Creates PKIX validation parameters and disables revocation checking.
   * - Validates the certificate using Java's `CertPathValidator`.
   *
   * @param pemCertificate The X.509 certificate in PEM format.
   * @return `true` if the certificate is valid and trusted, otherwise `false`.
   */
  def validateCertificate(certificate: X509Certificate): Boolean = {
    Try {
      // Load trust store
      val trustStore = loadTrustStore()
        .getOrElse(throw new Exception("Trust store could not be loaded."))

      val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      trustManagerFactory.init(trustStore)

      // Get trusted CAs from the trust store
      val trustAnchors = trustStore.aliases().asScala
        .filter(trustStore.isCertificateEntry)
        .map(alias => trustStore.getCertificate(alias).asInstanceOf[X509Certificate])
        .map(cert => new TrustAnchor(cert, null))
        .toSet
        .asJava  // Convert Scala Set to Java Set

      if (trustAnchors.isEmpty) throw new Exception("No trusted certificates found in trust store.")

      // Set up PKIX parameters for validation
      val pkixParams = new PKIXParameters(trustAnchors)
      if(APIUtil.getPropsAsBoolValue("use_tpp_signature_revocation_list", defaultValue = true)) {
        pkixParams.setRevocationEnabled(true) // Enable CRL checks
      } else {
        pkixParams.setRevocationEnabled(false) // Disable CRL checks
      }

      // Validate certificate chain
      val certPath = CertificateFactory.getInstance("X.509").generateCertPath(Collections.singletonList(certificate))
      val validator = CertPathValidator.getInstance("PKIX")
      validator.validate(certPath, pkixParams)

      logger.info("Certificate is valid and trusted.")
      true
    } match {
      case Success(_) => true
      case Failure(e: CertPathValidatorException) =>
        logger.info(s"Certificate validation failed: ${e.getMessage}")
        false
      case Failure(e) =>
        logger.info(s"Error: ${e.getMessage}")
        false
    }
  }

  /**
   * Converts a PEM certificate (Base64-encoded) into an `X509Certificate` object.
   *
   * This function:
   * - Removes the PEM header and footer (`-----BEGIN CERTIFICATE-----` and `-----END CERTIFICATE-----`).
   * - Decodes the Base64-encoded certificate data.
   * - Generates and returns an `X509Certificate` object.
   *
   * @param pem The X.509 certificate in PEM format.
   * @return The parsed `X509Certificate` object.
   */
  private def parsePemToX509Certificate(pem: String): X509Certificate = {
    val cleanedPem = pem.replaceAll("-----BEGIN CERTIFICATE-----", "")
      .replaceAll("-----END CERTIFICATE-----", "")
      .replaceAll("\\s", "")

    val decoded = Base64.getDecoder.decode(cleanedPem)
    val certFactory = CertificateFactory.getInstance("X.509")
    certFactory.generateCertificate(new ByteArrayInputStream(decoded)).asInstanceOf[X509Certificate]
  }

  def loadPemCertificateFromFile(filePath: String): Option[String] = {
    Try {
      val source = Source.fromFile(filePath)
      try source.getLines().mkString("\n") // Read entire file into a single string
      finally source.close()
    } match {
      case Success(pem) => Some(pem)
      case Failure(exception) =>
        logger.error(s"Failed to load PEM certificate from file: ${exception.getMessage}")
        None
    }
  }

  def main(args: Array[String]): Unit = {
    // change the following path if using this function to test on your localhost
    val certificatePath = "/path/to/certificate.pem"
    val pemCertificate = loadPemCertificateFromFile(certificatePath)
    val certificate = BerlinGroupSigning.parseCertificate(pemCertificate.getOrElse(""))

    val isValid = validateCertificate(certificate)
    logger.info(s"Certificate verification result: $isValid")

    loadTrustStore().foreach { trustStore =>
      logger.info(s"Trust Store contains ${trustStore.size()} certificates.")
    }
  }
}
