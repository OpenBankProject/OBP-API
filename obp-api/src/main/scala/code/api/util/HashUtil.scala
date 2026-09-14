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

import java.math.BigInteger
import net.liftweb.common.Box
import org.iban4j.IbanUtil

object HashUtil {
  def Sha256Hash(in: String): String = {
    import java.security.MessageDigest
    // java.security.MessageDigest#digest gives a byte array.
    // To create the hex, use String.format
    val hashedValue = String.format("%032x", new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(in.getBytes("UTF-8"))))
    hashedValue
  }
  
  // Single Point of Entry in order to calculate ETag
  def calculateETag(url: String, httpBody: Box[String]): String = {
    HashUtil.Sha256Hash(s"${url}${httpBody.getOrElse("")}")
  }

  def main(args: Array[String]): Unit = {
    // You can verify hash with command line tool in linux, unix:
    // $ echo -n "123" | openssl dgst -sha256
    
    val plainText = "123"
    val hashedText = Sha256Hash(plainText)
    println("Password: " + plainText)
    println("Hashed password: " + hashedText)
    println("BBAN: " + IbanUtil.getBban("AT483200000012345864"))
    println("Bank code: " + IbanUtil.getBankCode("AT483200000012345864"))
    println("Country code: " + IbanUtil.getCountryCode("AT483200000012345864"))
  }
}
