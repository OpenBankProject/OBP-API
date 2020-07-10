package code.api.util

import java.math.BigInteger

object HashUtil {
  def Sha256Hash(in: String): String = {
    import java.security.MessageDigest
    // java.security.MessageDigest#digest gives a byte array.
    // To create the hex, use String.format
    val hashedValue = String.format("%032x", new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(in.getBytes("UTF-8"))))
    hashedValue
  }

  def main(args: Array[String]): Unit = {
    // You can verify hash with command line tool in linux, unix:
    // $ echo -n "123" | openssl dgst -sha256
    val password = "123"
    val hashedPassword = Sha256Hash(password)
    println("Password: " + password)
    println("Hashed password: " + hashedPassword)
  }
}
