package code.api.util

import java.security.PublicKey
import java.security.cert.{CertificateExpiredException, CertificateNotYetValidException, X509Certificate}
import java.security.interfaces.RSAPublicKey

import com.nimbusds.jose.util.X509CertUtils
import net.liftweb.common.{Box, Failure, Full}

object X509 {
  
  def validate(encodedCert: String): Box[Boolean] = {
    val cert: X509Certificate = X509CertUtils.parse(encodedCert)
    if (cert == null) {
      // Parsing failed
      Failure(ErrorMessages.X509ParsingFailed)
    } else {
      try {
        cert.checkValidity
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

  def getRSAPublicKey(encodedCert: String): Box[PublicKey] = {
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
        // There is no the key, should not happen
        Failure(ErrorMessages.X509CannotGetRSAPublicKey)
      }
    }
  }
}
