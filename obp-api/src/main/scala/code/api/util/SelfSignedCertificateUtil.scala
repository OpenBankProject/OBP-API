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
  
  def main(args: Array[String]): Unit = {
    Security.addProvider(new BouncyCastleProvider)
    val pair = generateSelfSignedCert("app.example.com")
    val pemWriter = new JcaPEMWriter(new PrintWriter(System.out))
    pemWriter.writeObject(pair._1) // Create PEM representation of private kek
    pemWriter.writeObject(pair._2) // Create PEM representation of certificate
    pemWriter.flush() // Print result to the standard out
    pemWriter.close()
  }

  private val bcProvider = BouncyCastleProvider.PROVIDER_NAME

  @throws[NoSuchProviderException]
  @throws[NoSuchAlgorithmException]
  @throws[OperatorCreationException]
  @throws[CertificateException]
  def generateSelfSignedCert(commonName: String): (PrivateKey, Certificate) = {
    Security.addProvider(new BouncyCastleProvider)
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
    (keyPair.getPrivate, certificate)
  }
}
