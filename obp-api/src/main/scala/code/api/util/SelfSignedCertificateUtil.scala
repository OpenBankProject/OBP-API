package code.api.util

import java.io.PrintWriter
import java.math.BigInteger
import java.security._
import java.security.cert.{Certificate, CertificateException}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

import org.apache.commons.lang3.tuple.Pair
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509v3CertificateBuilder}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder



object SelfSignedCertificateUtil {

  /**
   * Example of produced output:
   *
   * -----BEGIN RSA PRIVATE KEY-----
   * MIIBOgIBAAJBAK5k4zmaUe/vaNGhC7CVooOs3T/7wCkeZHlD/28MLJ6fdSjTWP9R
   * jG2fRp0iaJZQTrGRpRdjGEXjp9ivLVc+kJUCAwEAAQJAAwlOT7bwS3+LcUUmDOZr
   * ZI71/vuVMBdr/IaGR55MQbGgBFZJPV4qS4a3Qhv81HeN0cASefAonc0sJkYR4kYd
   * UQIhANzXAnThhYgk1mipQm2LazldtOGzMlj365kQdp9s7X/NAiEAyijXQWaIpi/0
   * Jz5K76kaSmTM65+Gz+QIvuY8LvYlO+kCIBkVgv3rr9Mq+/+fWiA/OcqmTilkxU89
   * udEFAbRSFxfxAiEAmvVBQm1Q02owkSArmpcZgurPNNE8KgBWP9YFTc35bnkCIFO7
   * eFJ0emB41KtYAdngtdBoMx4svRsDX+NjT/ZeQ8YN
   * -----END RSA PRIVATE KEY-----
   * -----BEGIN CERTIFICATE-----
   * MIIBJzCB0qADAgECAgYBeIgGjdswDQYJKoZIhvcNAQELBQAwGjEYMBYGA1UEAwwP
   * YXBwLmV4YW1wbGUuY29tMB4XDTIxMDMzMTExMjM1NFoXDTIzMDMzMTExMjM1NFow
   * GjEYMBYGA1UEAwwPYXBwLmV4YW1wbGUuY29tMFwwDQYJKoZIhvcNAQEBBQADSwAw
   * SAJBAK5k4zmaUe/vaNGhC7CVooOs3T/7wCkeZHlD/28MLJ6fdSjTWP9RjG2fRp0i
   * aJZQTrGRpRdjGEXjp9ivLVc+kJUCAwEAATANBgkqhkiG9w0BAQsFAANBAKw8GjAM
   * w/S6hzC19IoOfLrTTXjXrge9lXedtFHpzLTylJi40aoNJnAtdHF9u95EjD9smglu
   * 2NEZO4X0J4mjAac=
   * -----END CERTIFICATE-----
   */
  def main(args: Array[String]): Unit = {
    Security.addProvider(new BouncyCastleProvider)
    val pair = generateSelfSignedCert("app.example.com")
    val pemWriter = new JcaPEMWriter(new PrintWriter(System.out))
    pemWriter.writeObject(pair.getLeft) // Create PEM representation of private kek
    pemWriter.writeObject(pair.getRight) // Create PEM representation of certificate
    pemWriter.flush() // Print result to the standard out
    pemWriter.close()
  }

  private val bcProvider = BouncyCastleProvider.PROVIDER_NAME

  @throws[NoSuchProviderException]
  @throws[NoSuchAlgorithmException]
  @throws[OperatorCreationException]
  @throws[CertificateException]
  def generateSelfSignedCert(commonName: String): Pair[PrivateKey, Certificate] = {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA", bcProvider) // Define RSA as the algorithm for a key creation
    keyPairGenerator.initialize(2048) // Define 2048 as the size of the key
    val keyPair = keyPairGenerator.generateKeyPair
    val dnName = new X500Name("CN=" + commonName)
    val certSerialNumber = BigInteger.valueOf(System.currentTimeMillis)
    val signatureAlgorithm = "SHA256WithRSA"
    val contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate)
    val startDate = Instant.now
    val endDate = startDate.plus(2 * 365, ChronoUnit.DAYS)
    val certBuilder = new JcaX509v3CertificateBuilder(dnName, certSerialNumber, Date.from(startDate), Date.from(endDate), dnName, keyPair.getPublic)
    val certificate = new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(certBuilder.build(contentSigner))
    Pair.of(keyPair.getPrivate, certificate)
  }
}
