package code.api.util

import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.security.cert.{CertificateExpiredException, CertificateNotYetValidException, X509Certificate}
import java.security.interfaces.{ECPublicKey, RSAPublicKey}

import com.github.dwickern.macros.NameOf
import com.nimbusds.jose.jwk.{JWK, RSAKey}
import com.nimbusds.jose.util.X509CertUtils
import net.liftweb.common.{Box, Failure, Full}
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.qualified.QCStatement
import org.bouncycastle.asn1._

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
  
  def pemToRsaJwk(encodedCert: String) = {
    // Parse X.509 certificate
    val cert = X509CertUtils.parse(encodedCert)
    // Retrieve public key as RSA JWK
    val rsaJWK = RSAKey.parse(cert)
    rsaJWK
  }

  // PEM-encoded private RSA key - This is a test only Private Key
  lazy val  pemEncodedRSAPrivateKey: String = """-----BEGIN RSA PRIVATE KEY-----
                                                |MIIEpAIBAAKCAQEAyaWz5PDC+WAjzKVni66t0aB6UcMeaLScdospNgT32GmE2jfT
                                                |zUGes0OWV4C6JN1XQXeeTwgzqX0n3IR+fQCs+3o1G3Cu0c3f8as7TQZv9Gdy41re
                                                |HfYNtmz8pxGO5e/tVyyIsU2J+IZRjn+6glL+00vegf/SDvGaZuJrs2LRPnmUgypX
                                                |5KcUTRM+XjR3tFpFaFm3k1ns9qn+6lunDszgLwAQ4OFSuoq0w457TOrStdvQxRjw
                                                |Aa/2XxgDX8qU/FjTfm1Shjmh4vO0nGYDyvJnLKo5/Q/txTIt4gOCxg/I67pdY8AS
                                                |FxeBaq70sebjMSBt2a++ig5XIQraW6VmJSCl7QIDAQABAoIBAAyQdhaQR93I90IT
                                                |llGWRz9WC/kXOshUZKFgR2eVxKmn3X7JVrml2pEZ5365Rx/v6LVsEiGjhbCMW1T6
                                                |rnT0e1LKCRAWI9ZvyQHiZPYGLiig34A6E7fzMmSJAu8YAXrjSbsSS8wcZDnniKJj
                                                |5Aelyzn4MruP6JNEy5WYixRo1lfZmC52M4WBwyzbRyzmPlnYKJTJ6l9z3CXgvUmw
                                                |gvPSahgIyxALgqhNRh9ngMBJQG7Hkag2MsLUz+2fncHoKUHYEmdAjb2cwXYSClzg
                                                |VLYeBe2m/vuWafD0v53DAkyIJv4knHuaUUC5adJ/cQzoAWNAglWkWxtskNlPfZHx
                                                |4VwqR7MCgYEA5AWjOfHC5erY+205ukYkcRyrPBFOLSSr4RUE/NDsd2w+cryYeQZg
                                                |SvsbFvOZH2IOnMCyfcUNe3ExNwaRFP7hCI9H449F0AwUFcQs604K++a2y/7TvlnZ
                                                |q+/Ahu7SueVUHdi+9eL4jwyuxVUJg8UhHYHD9oqW4Af/bHvJYy0IYucCgYEA4mOc
                                                |vx1456kSW0EiqJyJ3yUAUNrVY75AfwprWCBjgIYkEYEAHaQ/EyHnRGO5ySRYGYs6
                                                |GAWuYBHyPzKUnIuxpCutaU/hWjw2w/6T9hLYzyEn5NG7EhaDMiO/pEIpX0BZ29mx
                                                |yIoXc8SEDZzuiB+tVuqjW24rLyLNn3+ppr9RqgsCgYEAusLbVGRuC771BcoKlEVL
                                                |J9Ihdkt+Sn9UwEBlG2VLqOzhoTxTbh0I1aEiKQRQkGHSMhWqnFS/nDGz66vXPOke
                                                |C9K/QOVieurJsKJDYF9Fo9juM9t+NtSE8symVl5Z/qSU5vVWQzMp/pCWvU3PQzw8
                                                |yVw100LkHI6waHxjEHYb/lUCgYAbGXt48Sk46ec1nz1r25kxafd4tklW8D4+NtwU
                                                |p4Phra0Bn2SJJ9EZFDTf3eQubLhTDnR8zalK/Lr3z7E0cBBqq4PNmG9MYurXWVES
                                                |4ryrRrfEz0pKZwF7bgYRvo2/Ri+7fnqmm8kk5YA9NOzkxI32Wo4FctGeidb9YcXI
                                                |HRzEcwKBgQDKqCBgSnf414PP+KzIhQYUXCbXetwcNGwnPh3VkB0DXiROnpg/x5VJ
                                                |VqB2rXZWLnCgp36H+fF+hvFzcIGiSloSREAhPhDMdC6vCP39CR2b7Nik3txVuqK3
                                                |oF4inGgJYgKfsLaxEhpo64l1IPXCB2zmE2eNeZt+0YE82T+ad+XIUw==
                                                |-----END RSA PRIVATE KEY-----""".stripMargin

  lazy val pemEncodedCertificate =
    """-----BEGIN CERTIFICATE-----
      |MIICsjCCAZqgAwIBAgIGAXiEVYd0MA0GCSqGSIb3DQEBCwUAMBoxGDAWBgNVBAMM
      |D2FwcC5leGFtcGxlLmNvbTAeFw0yMTAzMzAxODExNDFaFw0yMzAzMzAxODExNDFa
      |MBoxGDAWBgNVBAMMD2FwcC5leGFtcGxlLmNvbTCCASIwDQYJKoZIhvcNAQEBBQAD
      |ggEPADCCAQoCggEBAMmls+TwwvlgI8ylZ4uurdGgelHDHmi0nHaLKTYE99hphNo3
      |081BnrNDlleAuiTdV0F3nk8IM6l9J9yEfn0ArPt6NRtwrtHN3/GrO00Gb/RncuNa
      |3h32DbZs/KcRjuXv7VcsiLFNifiGUY5/uoJS/tNL3oH/0g7xmmbia7Ni0T55lIMq
      |V+SnFE0TPl40d7RaRWhZt5NZ7Pap/upbpw7M4C8AEODhUrqKtMOOe0zq0rXb0MUY
      |8AGv9l8YA1/KlPxY035tUoY5oeLztJxmA8ryZyyqOf0P7cUyLeIDgsYPyOu6XWPA
      |EhcXgWqu9LHm4zEgbdmvvooOVyEK2lulZiUgpe0CAwEAATANBgkqhkiG9w0BAQsF
      |AAOCAQEAUZ5BqGFl+ce2skZD6Wf3PnCsdos9HnSFg/WogcDbeTVLS3+bn5Z+VbOK
      |m4yroOB9VCfhZ6msrBKKcELmj85jRGw4bnip9AjOrYgowePr0f0kq/BMIkZkSB9J
      |98PusxUXgfVdeygbfLyBhQkkGKYaIEIsWSyFxJ/grIoZSwdIt2wkD2VfGakdq9UI
      |1IY8BK9NiILGL3HaQdoHFavuXq6/M6/5hrIRtpXncZTO93i4/YhLuaPWlXjGqijL
      |dfo4IrZChEBioZ/sGmatrqoQUdeuG1loMWFPRN6/6AdXv33QAL7uiKTWpFOJkMys
      |Waq0JntfTgPHs/yvCjzdKrOKS1uUgQ==
      |-----END CERTIFICATE-----""".stripMargin
  
  def main(args: Array[String]): Unit = {
    
    // Parse PEM-encoded key to RSA public / private JWK
    val jwkPrivate: JWK = JWK.parseFromPEMEncodedObjects(pemEncodedRSAPrivateKey)
    org.scalameta.logger.elem(jwkPrivate.getKeyType())
    org.scalameta.logger.elem(jwkPrivate.isPrivate())
    
    validate(pemEncodedCertificate)
    val jwk: JWK = JWK.parseFromPEMEncodedObjects(pemEncodedCertificate)
    org.scalameta.logger.elem(jwk.getKeyType())
    org.scalameta.logger.elem(jwk.isPrivate())
  }
}
