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
import java.security.SecureRandom

/**
 * Cryptographically strong pseudo-random number generator
 * 
 * Standard JDK implementations of java.util.Random use a Linear Congruential Generator (LCG) algorithm for providing random numbers.
 * The problem with this algorithm is that it’s not cryptographically strong. 
 * In other words, the generated values are much more predictable, therefore attackers could use it to compromise our system.
 * To overcome this issue, we should use java.security.SecureRandom in any security decisions. 
 * It produces cryptographically strong random values by using a cryptographically strong pseudo-random number generator (CSPRNG).
 */
object SecureRandomUtil {
  // Obtains random numbers from the underlying native OS. 
  // No assertions are made as to the blocking nature of generating these numbers.
  val csprng = SecureRandom.getInstance("NativePRNG")
  
  def alphanumeric(nrChars: Int = 24): String = {
    new BigInteger(nrChars * 5, csprng).toString(32)
  }  
  def numeric(maxNumber: Int = 99999999): String = {
    csprng.nextInt(maxNumber).toString()
  }
}
