package code.api.util

object BerlinGroupSignatureHeaderParser {

  case class ParsedKeyId(sn: String, ca: String, o: String)

  case class ParsedSignature(keyId: ParsedKeyId, headers: List[String], signature: String)

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

    val caValue = kvMap.get("CA").map(_.stripPrefix("CN="))

    (kvMap.get("SN"), caValue, kvMap.get("O")) match {
      case (Some(sn), Some(ca), Some(o)) =>
        Right(ParsedKeyId(sn, ca, o))
      case _ =>
        Left(s"Invalid or missing fields in keyId: found keys ${kvMap.keys.mkString(", ")}")
    }
  }

  def parseSignatureHeader(header: String): Either[String, ParsedSignature] = {
    val fields = header.split(",(?=\\s*(keyId|headers|signature)=)").map(_.trim)

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
    } yield ParsedSignature(keyId, headers, sig)
  }

  // Example usage
  def main(args: Array[String]): Unit = {
    val header =
      """keyId="CA=CN=MAIB Prisacaru Sergiu (Test), SN=43A, O=MAIB", headers="digest date x-request-id", signature="abc123+==" """

    parseSignatureHeader(header) match {
      case Right(parsed) =>
        println("Parsed Signature Header:")
        println(s"SN: ${parsed.keyId.sn}")
        println(s"CA: ${parsed.keyId.ca}")
        println(s"O: ${parsed.keyId.o}")
        println(s"Headers: ${parsed.headers.mkString(", ")}")
        println(s"Signature: ${parsed.signature}")
      case Left(error) =>
        println(s"Error: $error")
    }
  }
}
