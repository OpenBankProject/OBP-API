/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.util

import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.security.cert.{CertificateExpiredException, CertificateNotYetValidException, X509Certificate}
import java.security.interfaces.{ECPublicKey, RSAPublicKey}

import code.api.v5_1_0.CertificateInfoJsonV510
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.util.X509CertUtils
import net.liftweb.common.{Box, Failure, Full, Empty}
import org.bouncycastle.asn1._
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.qualified.QCStatement

object X509 extends MdcLoggable {

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
    logger.debug(psd2Roles.toList)
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
  }  /**
    * The certificate must be validated before it may be used.
    * @param encodedCert PEM (BASE64) encoded certificates, suitable for copy and paste operations.
    * @return Full(true) or an Failure
    */
  def validateCertificate(certificate: X509Certificate): Box[Boolean] = {
    try {
      certificate.checkValidity()
      Full(true)
    }
    catch {
      case _: CertificateExpiredException =>
        Failure(ErrorMessages.X509CertificateExpired)
      case _: CertificateNotYetValidException =>
        Failure(ErrorMessages.X509CertificateNotYetValid)
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
  
  def pemToRsaJwk(encodedCert: String) = {
    // Parse X.509 certificate
    val cert = X509CertUtils.parse(encodedCert)
    // Retrieve public key as RSA JWK
    val rsaJWK = RSAKey.parse(cert)
    rsaJWK
  }


  private def extractCertificateInfo(pem: String): Box[CertificateInfoJsonV510] = {
    // Parse X.509 certificate
    val cert: X509Certificate = X509CertUtils.parse(pem)
    if (cert == null) {
      // Parsing failed
      Failure(ErrorMessages.X509ParsingFailed)
    } else {
      val subjectDN = cert.getSubjectDN().getName()
      val issuerDN = cert.getIssuerDN().getName()
      val notBefore = cert.getNotBefore()
      val notAfter = cert.getNotAfter()
      var roles: Option[List[String]] = None
      var rolesInfo: Option[String] = None
      try {
        val qcstatements = extractQcStatements(cert)
        val asn1encodable = extractPsd2QcStatements(qcstatements)
        roles = Some(getPsd2Roles(asn1encodable: Array[ASN1Encodable]))
      }
      catch {
        case _: Throwable => 
          Failure(ErrorMessages.X509ThereAreNoPsd2Roles)
          rolesInfo = Some("PEM Encoded Certificate does not contain PSD2 roles.")
      }
      val result = CertificateInfoJsonV510(
        subject_domain_name = subjectDN, 
        issuer_domain_name = issuerDN, 
        not_before = APIUtil.formatDate(notBefore), 
        not_after = APIUtil.formatDate(notAfter), 
        roles = roles,
        roles_info = rolesInfo
      )
      Full(result)
    }
  }
  
  def getCertificateInfo(pem: Option[String]): Box[CertificateInfoJsonV510] = {
    pem match {
      case Some(value) => extractCertificateInfo(value)
      case None => Failure(ErrorMessages.X509CannotGetCertificate)
    }
  }

  def getCommonName(pem: Option[String]): Box[String] = {
    getFieldCommon(pem, "CN")
  }
  def getOrganization(pem: Option[String]): Box[String] = {
    getFieldCommon(pem, "O")
  }
  def getOrganizationUnit(pem: Option[String]): Box[String] = {
    getFieldCommon(pem, "OU")
  }
  def getEmailAddress(pem: Option[String]): Box[String] = {
    getFieldCommon(pem, "EMAILADDRESS")
      .or(getFieldCommon(pem, "EMAILADDRESS".toLowerCase()))
  }

  private def getFieldCommon(pem: Option[String], field: String) = {
    pem match {
      case Some(unboxedPem) =>
        extractCertificateInfo(unboxedPem).map { item =>
          val splitByComma: Array[String] = item.subject_domain_name.split(",")
          val splitByKeyValuePair: Array[(String, String)] = splitByComma.map(i => i.split("=")(0).trim -> i.split("=")(1).trim)
          val valuesAsMap: Map[String, List[String]] = splitByKeyValuePair.toList.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }
          val result: String = valuesAsMap.get(field).map(_.mkString).getOrElse("")
          result
        } match {
          case Full(value) if value.isEmpty => Empty
          case everythingElse => everythingElse
        }
      case _ =>
        Empty
    }
  }

}
