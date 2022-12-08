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
