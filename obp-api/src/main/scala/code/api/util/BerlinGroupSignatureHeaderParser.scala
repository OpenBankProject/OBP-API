package code.api.util

import code.util.Helper.MdcLoggable

object BerlinGroupSignatureHeaderParser extends MdcLoggable {

  case class ParsedKeyId(sn: String, ca: String, cn: String, o: String)

  case class ParsedSignature(keyId: ParsedKeyId, algorithm: String, headers: List[String], signature: String)

  def parseQuotedValue(value: String): String =
    value.stripPrefix("\"").stripSuffix("\"").trim

  def parseKeyIdField(value: String): Either[String, ParsedKeyId] = {
    val parts = value.split(",").map(_.trim)
    val kvMap: Map[String, String] = parts.flatMap { part =>
      part.split("=", 2) match {
        case Array(k, v) => Some(k.trim -> v.trim)
        case _ => None
      }
    }.toMap

    // mandatory fields
    val snOpt = kvMap.get("SN")
    val oOpt = kvMap.get("O")

    val caOpt = kvMap.get("CA")
    val cnOpt = kvMap.get("CN")

    val (caFinal, cnFinal): (String, String) = (caOpt, cnOpt) match {
      case (Some(caRaw), Some(cnRaw)) =>
        // Both CA and CN are present: use them as-is
        (caRaw, cnRaw)

      case (Some(caRaw), None) if caRaw.startsWith("CN=") =>
        // Special case: CA=CN=... → set both CA and CN to value after CN=
        val value = caRaw.stripPrefix("CN=")
        (value, value)

      case _ =>
        return Left(s"Missing mandatory 'CN' field or invalid CA format: found keys ${kvMap.keys.mkString(", ")}")
    }

    (snOpt, oOpt) match {
      case (Some(sn), Some(o)) =>
        Right(ParsedKeyId(sn, caFinal, cnFinal, o))
      case _ =>
        Left(s"Missing mandatory 'SN' or 'O' field: found keys ${kvMap.keys.mkString(", ")}")
    }
  }

  def parseSignatureHeader(header: String): Either[String, ParsedSignature] = {
    val fields = header.split(",(?=\\s*(keyId|headers|algorithm|signature)=)").map(_.trim)

    val kvMap = fields.flatMap { field =>
      field.split("=", 2) match {
        case Array(k, v) => Some(k.trim -> parseQuotedValue(v.trim))
        case _ => None
      }
    }.toMap

    for {
      keyIdStr <- kvMap.get("keyId").toRight("Missing 'keyId' field")
      keyId <- parseKeyIdField(keyIdStr)
      headers <- kvMap.get("headers").map(_.split("\\s+").toList).toRight("Missing 'headers' field")
      sig <- kvMap.get("signature").toRight("Missing 'signature' field")
      algorithm <- kvMap.get("algorithm").toRight("Missing 'algorithm' field")
    } yield ParsedSignature(keyId, algorithm, headers, sig)
  }

  /**
   * Detect and match incoming SN as decimal or hex against certificate serial number.
   */
  def doesSerialNumberMatch(incomingSn: String, certSerial: java.math.BigInteger): Boolean = {
    try {
      val incomingAsDecimal = new java.math.BigInteger(incomingSn, 10)
      if (incomingAsDecimal == certSerial) {
        logger.debug(s"SN matched in decimal")
        return true
      }
    } catch {
      case _: NumberFormatException =>
        logger.debug(s"Incoming SN is not valid decimal: $incomingSn")
    }

    try {
      val incomingAsHex = new java.math.BigInteger(incomingSn, 16)
      if (incomingAsHex == certSerial) {
        logger.debug(s"SN matched in hex")
        return true
      }
    } catch {
      case _: NumberFormatException =>
        logger.debug(s"Incoming SN is not valid hex: $incomingSn")
    }

    false
  }

  // Example usage
  def main(args: Array[String]): Unit = {
    val testHeaders = List(
      """keyId="CA=CN=MAIB Prisacaru Sergiu (Test), SN=43A, O=MAIB", headers="digest date x-request-id", signature="abc123+=="""",
      """keyId="CA=SomeCAValue, CN=SomeCNValue, SN=43A, O=MAIB", headers="digest date x-request-id", signature="def456+=="""",
      """keyId="CA=MissingCN, SN=43A, O=MAIB", headers="digest date x-request-id", signature="should_fail""""
    )

    testHeaders.foreach { header =>
      println(s"\nParsing header:\n$header")
      parseSignatureHeader(header) match {
        case Right(parsed) =>
          println("Parsed Signature Header:")
          println(s"  SN: ${parsed.keyId.sn}")
          println(s"  CA: ${parsed.keyId.ca}")
          println(s"  CN: ${parsed.keyId.cn}")
          println(s"  O: ${parsed.keyId.o}")
          println(s"  Headers: ${parsed.headers.mkString(", ")}")
          println(s"  Signature: ${parsed.signature}")
        case Left(error) =>
          println(s"Error: $error")
      }
    }
  }
}
