package code.api.util

import java.security.PublicKey
import java.security.cert.{CertificateExpiredException, CertificateNotYetValidException, X509Certificate}
import java.security.interfaces.{ECPublicKey, RSAPublicKey}

import com.github.dwickern.macros.NameOf
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.util.X509CertUtils
import net.liftweb.common.{Box, Failure, Full}

object X509 {
  
  object OID {
    lazy val role = "2.5.4.72"
  }
  
  case class SubjectAttribute(key: String, value: String)
  
  private def extractSubjectAttributes(encodedCert: String): List[SubjectAttribute] = {
    // Parse X.509 certificate
    val cert: X509Certificate = X509CertUtils.parse(encodedCert)
    if (cert == null) {
      // Parsing failed
      Nil
    } else {
        cert.getSubjectDN().getName().split(",").toList.map {
        attribute => attribute.trim.split("=").toList match {
          case key :: value :: Nil => SubjectAttribute(key, value)
        }
      }
    }
  }
  
  def getRoles(encodedCert: String): String = {
    extractSubjectAttributes(encodedCert).filter{
      attribute => attribute.key.contains(OID.role) || attribute.key.contains(NameOf.nameOf(OID.role))
    } match {
      case x :: Nil => x.value
      case _ => ""
    }
  }
  
  

  /**
    * The certificate must be validated before it may be used.
    * @param encodedCert PEM (BASE64) encoded certificates, suitable for copy and paste operations.
    * @return Full(true) or an Failure
    */
  def validate(encodedCert: String): Box[Boolean] = {
    // Parse X.509 certificate
    val cert: X509Certificate = X509CertUtils.parse(encodedCert)
    if (cert == null) {
      // Parsing failed
      Failure(ErrorMessages.X509ParsingFailed)
    } else {
      try {
        cert.checkValidity()
        Full(true)
      }
      catch {
        case _: CertificateExpiredException =>
          Failure(ErrorMessages.X509CertificateExpired)
        case _: CertificateNotYetValidException =>
          Failure(ErrorMessages.X509CertificateNotYetValid)
      }
    }
  }
  
  /**
    * If the certificate passed validation and can be trusted, 
    * you can proceed by extracting the public key (RSA or EC) that comes with it, 
    * for example to a verify a JWS signature.
    * @param encodedCert PEM (BASE64) encoded certificates, suitable for copy and paste operations.
    * @return RSA public key
    */
  def getRSAPublicKey(encodedCert: String): Box[PublicKey] = {
    // Parse X.509 certificate
    val cert: X509Certificate = X509CertUtils.parse(encodedCert)
    if (cert == null) {
      // Parsing failed
      Failure(ErrorMessages.X509ParsingFailed)
    } else {
      val pubKey: PublicKey  = cert.getPublicKey()
      if (pubKey.isInstanceOf[RSAPublicKey]) {
        // We have an RSA public key
        Full(pubKey)
      } else {
        // There is no an RSA public key, should not happen
        Failure(ErrorMessages.X509CannotGetRSAPublicKey)
      }
    }
  }

  /**
    * If the certificate passed validation and can be trusted, 
    * you can proceed by extracting the public key (RSA or EC) that comes with it, 
    * for example to a verify a JWS signature.
    * @param encodedCert PEM (BASE64) encoded certificates, suitable for copy and paste operations.
    * @return EC public key
    */
  def getECPublicKey(encodedCert: String): Box[PublicKey] = {
    // Parse X.509 certificate
    val cert: X509Certificate = X509CertUtils.parse(encodedCert)
    if (cert == null) {
      // Parsing failed
      Failure(ErrorMessages.X509ParsingFailed)
    } else {
      val pubKey: PublicKey  = cert.getPublicKey()
      if (pubKey.isInstanceOf[ECPublicKey]) {
        // We have an EC public key
        Full(pubKey)
      } else {
        // There is no an EC public key, should not happen
        Failure(ErrorMessages.X509CannotGetECPublicKey)
      }
    }
  }

  /**
    * Method for extracting the public key of an X.509 certificate in JWK format
    * @param encodedCert PEM (BASE64) encoded certificates, suitable for copy and paste operations.
    * @return certificate in JWK format
    */
  def convertToJWK(encodedCert: String): Box[RSAKey] = {
    // Parse X.509 certificate
    val cert: X509Certificate = X509CertUtils.parse(encodedCert)
    if (cert == null) {
      // Parsing failed
      Failure(ErrorMessages.X509ParsingFailed)
    } else {
      // Retrieve public key as RSA JWK
      val rsaJWK: RSAKey  = RSAKey.parse(cert)
      Some(rsaJWK)
    }
  }
}
