package code.api.berlin.group.signing

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.{BasicConstraints, Extension, KeyUsage, SubjectPublicKeyInfo}
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509v3CertificateBuilder}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

import java.io.{ByteArrayOutputStream, StringWriter}
import java.math.BigInteger
import java.nio.file.{Files, Path}
import java.security._
import java.security.cert.X509Certificate
import java.time.{LocalDateTime, ZoneOffset}
import java.util.Date
import scala.util.Try

/**
 * Utility class for generating test certificates and keystores on the fly
 * Used for Berlin Group PSD2 testing without relying on external certificate files
 */
object TestCertificateGenerator {
  
  // Add BouncyCastle provider
  Security.addProvider(new BouncyCastleProvider())
  
  case class CertificateData(
    privateKey: PrivateKey,
    publicKey: PublicKey,
    certificate: X509Certificate,
    privateKeyPem: String,
    certificatePem: String,
    p12Data: Array[Byte],
    serialNumber: BigInteger
  )
  
  /**
   * Generate a self-signed test certificate with private key
   */
  def generateTestCertificate(
    commonName: String = "Test TPP Certificate",
    organizationName: String = "Test Organization", 
    keySize: Int = 2048,
    validityDays: Int = 365,
    password: String = "password",
    alias: String = "test-alias"
  ): Try[CertificateData] = {
    
    Try {
      // Generate key pair
      val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
      keyPairGenerator.initialize(keySize)
      val keyPair = keyPairGenerator.generateKeyPair()
      
      val privateKey = keyPair.getPrivate
      val publicKey = keyPair.getPublic
      
      // Create certificate
      val now = new Date()
      val notBefore = now
      val notAfter = Date.from(LocalDateTime.now().plusDays(validityDays).toInstant(ZoneOffset.UTC))
      
      val dnName = new X500Name(s"CN=$commonName, O=$organizationName, C=US")
      val certSerialNumber = BigInteger.valueOf(System.currentTimeMillis())
      
      val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded)
      
      val certBuilder = new JcaX509v3CertificateBuilder(
        dnName, // issuer
        certSerialNumber,
        notBefore,
        notAfter,
        dnName, // subject (same as issuer for self-signed)
        publicKey
      )
      
      // Add extensions
      certBuilder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false))
      certBuilder.addExtension(Extension.keyUsage, false, 
        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment | KeyUsage.nonRepudiation))
      
      // Sign the certificate
      val contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(privateKey)
      val certHolder = certBuilder.build(contentSigner)
      
      val certificateConverter = new JcaX509CertificateConverter()
      val certificate = certificateConverter.getCertificate(certHolder)
      
      // Convert to PEM format
      val privateKeyPem = convertPrivateKeyToPem(privateKey)
      val certificatePem = convertCertificateToPem(certificate)
      
      // Create P12 data
      val p12Data = createP12KeyStore(privateKey, certificate, alias, password)
      
      CertificateData(
        privateKey = privateKey,
        publicKey = publicKey,
        certificate = certificate,
        privateKeyPem = privateKeyPem,
        certificatePem = certificatePem,
        p12Data = p12Data,
        serialNumber = certSerialNumber
      )
    }
  }
  
  /**
   * Convert private key to PEM format string
   */
  private def convertPrivateKeyToPem(privateKey: PrivateKey): String = {
    val stringWriter = new StringWriter()
    val pemWriter = new JcaPEMWriter(stringWriter)
    try {
      pemWriter.writeObject(privateKey)
      pemWriter.flush()
      stringWriter.toString
    } finally {
      pemWriter.close()
      stringWriter.close()
    }
  }
  
  /**
   * Convert certificate to PEM format string
   */
  private def convertCertificateToPem(certificate: X509Certificate): String = {
    val stringWriter = new StringWriter()
    val pemWriter = new JcaPEMWriter(stringWriter)
    try {
      pemWriter.writeObject(certificate)
      pemWriter.flush()
      stringWriter.toString
    } finally {
      pemWriter.close()
      stringWriter.close()
    }
  }
  
  /**
   * Create a PKCS12 keystore with the private key and certificate
   * Also adds the certificate as a trusted certificate entry for truststore validation
   */
  private def createP12KeyStore(
    privateKey: PrivateKey, 
    certificate: X509Certificate, 
    alias: String, 
    password: String
  ): Array[Byte] = {
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(null, null)
    
    // Add key entry (private key + certificate chain)
    val certChain = Array[java.security.cert.Certificate](certificate)
    keyStore.setKeyEntry(alias, privateKey, password.toCharArray, certChain)
    
    // Add trusted certificate entry for truststore validation
    keyStore.setCertificateEntry(s"trusted-$alias", certificate)
    
    val outputStream = new ByteArrayOutputStream()
    try {
      keyStore.store(outputStream, password.toCharArray)
      outputStream.toByteArray
    } finally {
      outputStream.close()
    }
  }
  
  /**
   * Write P12 data to a temporary file and return the path
   */
  def writeP12ToTempFile(p12Data: Array[Byte], prefix: String = "test-keystore"): Try[Path] = {
    Try {
      val tempFile = Files.createTempFile(prefix, ".p12")
      Files.write(tempFile, p12Data)
      // Mark for deletion on exit
      tempFile.toFile.deleteOnExit()
      tempFile
    }
  }
  
  /**
   * Generate a complete test certificate setup with temporary files
   */
  def generateTestCertificateWithTempFiles(
    commonName: String = "Test Berlin Group TPP",
    organizationName: String = "Test Bank",
    password: String = "testpassword123",
    alias: String = "test-tpp-alias"
  ): Try[(CertificateData, Path)] = {
    for {
      certData <- generateTestCertificate(commonName, organizationName, password = password, alias = alias)
      tempP12Path <- writeP12ToTempFile(certData.p12Data, "berlin-group-test")
    } yield (certData, tempP12Path)
  }
  
  /**
   * Default certificate data for Berlin Group tests
   */
  lazy val defaultBerlinGroupTestCertificate: Try[CertificateData] = {
    generateTestCertificate(
      commonName = "Berlin Group Test TPP Certificate",
      organizationName = "MAIB Test Bank"
    )
  }
}