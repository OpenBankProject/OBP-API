package code.api.util

import java.security.PublicKey
import java.security.cert.{CertificateExpiredException, CertificateNotYetValidException, X509Certificate}
import java.security.interfaces.{ECPublicKey, RSAPublicKey}
import java.io.ByteArrayInputStream

import com.github.dwickern.macros.NameOf
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.util.X509CertUtils
import net.liftweb.common.{Box, Failure, Full}
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.{ASN1Encodable, ASN1InputStream, ASN1ObjectIdentifier, ASN1Sequence, DEROctetString}
import org.bouncycastle.asn1.x509.qualified.QCStatement

object X509 {

  object OID {
    lazy val role = "2.5.4.72"
    lazy val etsiPsd2QcStatement = new ASN1ObjectIdentifier("0.4.0.19495.2")
  }

  case class SubjectAttribute(key: String, value: String)

  private def extractSubjectAttributes(encodedCert: String): List[SubjectAttribute] = {
    // Parse X.509 certificate
    val cert: X509Certificate = X509CertUtils.parse(encodedCert)
    if (cert == null) {
      // Parsing failed
      Nil
    } else {
      cert.getSubjectDN().getName().split(",").toList.map { attribute =>
       val Array(key, value) = attribute.trim.split("=")
       SubjectAttribute(key, value)
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


  def extractQcStatements(cert: X509Certificate): ASN1Sequence = {
    val qcStatementBytes: Array[Byte] = cert.getExtensionValue(Extension.qCStatements.getId)
    val inputStream = new ASN1InputStream(new ByteArrayInputStream(qcStatementBytes))
    val dEROctetString = inputStream.readObject().asInstanceOf[DEROctetString]
    val qcInputStream = new ASN1InputStream(dEROctetString.getOctets)
    val qcStatements  = qcInputStream.readObject().asInstanceOf[ASN1Sequence]
    qcStatements
  }

  def extractPsd2QcStatements(qcstatements: ASN1Sequence) = {
    val encodable: Array[ASN1Encodable] = qcstatements.toArray.filter(QCStatement.getInstance(_).getStatementId.getId.equals(X509.OID.etsiPsd2QcStatement.getId))
    encodable
  }

  def getPsd2Roles(asn1encodable: Array[ASN1Encodable]): List[String] = {
    var psd2Roles: Set[String] = Set()
    for (i <- asn1encodable.indices) {
      val psd2Sequence = ASN1Sequence.getInstance(asn1encodable(i))
      val psd2TypesEncodable: ASN1Encodable = psd2Sequence.getObjectAt(1)
      val psd2TypesSequence =  ASN1Sequence.getInstance(psd2TypesEncodable)
      val psd2RolesEncodable: ASN1Encodable = psd2TypesSequence.getObjectAt(0)
      val psd2RolesSequence = ASN1Sequence.getInstance(psd2RolesEncodable)
      for (y <- 0 until (psd2RolesSequence.size() - 1)){
        val psd2RoleEncodable = psd2RolesSequence.getObjectAt(y)
        val psd2RoleSequence = ASN1Sequence.getInstance(psd2RoleEncodable)
        psd2Roles += (psd2RoleSequence.getObjectAt(1).toASN1Primitive.toString)
      }
    }
    org.scalameta.logger.elem(psd2Roles.toList)
    psd2Roles.toList
  }

  def extractPsd2Roles(pem: String): Box[List[String]] = {
    // Parse X.509 certificate
    val cert: X509Certificate = X509CertUtils.parse(pem)
    if (cert == null) {
      // Parsing failed
      Failure(ErrorMessages.X509ParsingFailed)
    } else {
      try {
        val qcstatements = extractQcStatements(cert)
        val asn1encodable = extractPsd2QcStatements(qcstatements)
        Full(getPsd2Roles(asn1encodable: Array[ASN1Encodable]))
      }
      catch {
        case _: Throwable => Failure(ErrorMessages.X509ThereAreNoPsd2Roles)
      }
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
