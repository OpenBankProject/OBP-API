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


import code.api.{CertificateConstants, PrivateKeyConstants}

import java.io.FileInputStream
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey}
import java.util.Base64
object P12StoreUtil {

  /**
   * Loads a private key and its certificate from a .p12 (PKCS#12) keystore file.
   *
   * @param p12Path     Path to the .p12 file
   * @param p12Password Password for the keystore
   * @param alias       Alias under which the key is stored
   * @return A tuple of (PrivateKey, X509Certificate)
   */
  def loadPrivateKey(p12Path: String, p12Password: String, alias: String): (PrivateKey, X509Certificate) = {
    // Create an instance of a KeyStore of type PKCS12
    val keyStore = KeyStore.getInstance("PKCS12")

    // Open an input stream to the .p12 file
    val fis = new FileInputStream(p12Path)

    // Load the keystore with the password
    keyStore.load(fis, p12Password.toCharArray)

    // Always close the input stream when done
    fis.close()

    // Retrieve the private key from the keystore
    val key = keyStore.getKey(alias, p12Password.toCharArray).asInstanceOf[PrivateKey]

    // Retrieve the certificate associated with the key (optional but often useful)
    val cert = keyStore.getCertificate(alias).asInstanceOf[X509Certificate]

    // Return both the key and the certificate
    (key, cert)
  }

  def privateKeyToPEM(privateKey: PrivateKey): String = {
    val base64 = Base64.getEncoder.encodeToString(privateKey.getEncoded)

    // Format as PEM with BEGIN/END markers and line breaks every 64 characters
    val formatted = base64.grouped(64).mkString("\n")

    s"""${PrivateKeyConstants.BEGIN_KEY}
       |$formatted
       |${PrivateKeyConstants.END_KEY}""".stripMargin
  }

  def certificateToPEM(cert: X509Certificate): String = {
    val base64 = Base64.getEncoder.encodeToString(cert.getEncoded)

    // Format as PEM with BEGIN/END markers and line breaks every 64 characters
    val formatted = base64.grouped(64).mkString("\n")

    s"""${CertificateConstants.BEGIN_CERT}
       |$formatted
       |${CertificateConstants.END_CERT}""".stripMargin
  }


  def main(args: Array[String]): Unit = {
    val p12Path = APIUtil.getPropsValue("truststore.path.tpp_signature")
      .or(APIUtil.getPropsValue("truststore.path")).getOrElse("")
    val p12Password = APIUtil.getPropsValue("truststore.password.tpp_signature", "")
    // Load the private key and certificate from the keystore
    val (privateKey, cert) = loadPrivateKey(
      p12Path = p12Path, // Replace with the actual file path
      p12Password = p12Password, // Replace with the keystore password
      alias = "bnm test" // Replace with the key alias
    )

    // Print information to confirm successful loading
    println(s"Private key algorithm: ${privateKey.getAlgorithm}")
    println(s"Certificate subject: ${cert.getSubjectDN}")
  }


}
