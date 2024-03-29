package code.api.util

import java.math.BigInteger
import net.liftweb.common.Box

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
  }
}
