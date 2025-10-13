package code.bankconnectors.ethereum

import java.math.BigInteger
import org.web3j.crypto.{Hash, RawTransaction, TransactionDecoder, Sign, SignedRawTransaction}
import org.web3j.utils.{Numeric => W3Numeric}
import net.liftweb.json._

object DecodeRawTx {

  //  Legacy (type 0)
  //  Mandatory: nonce, gasPrice, gasLimit, value (can be 0), data (can be 0x), v, r, s
  //  Conditional: to (present for transfers/calls; omitted for contract creation where data is init code)
  //  Optional/Recommended: chainId (EIP-155 replay protection; legacy pre‑155 may omit)
  //  EIP-2930 (type 1)
  //  Mandatory: chainId, nonce, gasPrice, gasLimit, accessList (can be empty []), v/r/s (or yParity+r+s)
  //  Conditional: to (omit for contract creation), value (can be 0), data (can be 0x)
  //  EIP-1559 (type 2)
  //  Mandatory: chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, accessList (can be empty []), v/r/s (or yParity+r+s)
  //  Conditional: to (omit for contract creation), value (can be 0), data (can be 0x)
  //  Derived (not part of signed payload)
  //  hash: derived from raw tx
  //    from: recovered from signature
  //  estimatedFee: computed (gasLimit × gasPrice or gasUsed × price at execution)
  //  type: 0/1/2 (0 is implicit for legacy)
  //    
  // Case class representing the decoded transaction JSON structure
  case class DecodedTxResponse(
    hash: Option[String],
    `type`: Option[Int],
    chainId: Option[Long],
    nonce: Option[Long],
    gasPrice: Option[String],
    gas: Option[String],
    to: Option[String],
    value: Option[String],
    input: Option[String],
    from: Option[String],
    v: Option[String],
    r: Option[String],
    s: Option[String],
    estimatedFee: Option[String]
  )

  private def fatal(msg: String): Nothing = {
    Console.err.println(msg)
    sys.exit(1)
  }

  // File/stdin helpers removed; input is provided as a function parameter now.

  private def normalizeHex(hex: String): String = {
    val h = Option(hex).getOrElse("").trim
    if (!h.startsWith("0x")) fatal("Input must start with 0x")
    val body = h.drop(2)
    if (!body.matches("[0-9a-fA-F]+")) fatal("Invalid hex characters in input")
    if (body.length % 2 != 0) fatal("Hex string length must be even")
    "0x" + body.toLowerCase
  }

  private def detectType(hex: String): Int = {
    val body = hex.stripPrefix("0x")
    if (body.startsWith("02")) 2
    else if (body.startsWith("01")) 1
    else 0
  }

  // Build EIP-155 v when chainId is available; parity from v-byte (27/28 -> 0/1, otherwise lowest bit)
  private def vToHex(sig: Sign.SignatureData, chainIdOpt: Option[BigInteger]): String = {
    val vb: Int = {
      val arr = sig.getV
      if (arr != null && arr.length > 0) java.lang.Byte.toUnsignedInt(arr(0)) else 0
    }
    chainIdOpt match {
      case Some(cid) if cid.signum() > 0 =>
        val parity = if (vb == 27 || vb == 28) vb - 27 else (vb & 1)
        val v = cid.multiply(BigInteger.valueOf(2)).add(BigInteger.valueOf(35L + parity))
        "0x" + v.toString(16)
      case _ =>
        "0x" + Integer.toHexString(vb)
    }
  }

  /**
   * Decode raw Ethereum transaction hex and return a JSON string summarizing the fields.
   * The input must be a 0x-prefixed hex string; no file or stdin reading is performed.
   *
   * Response is serialized from DecodedTxResponse case class with types:
   *  - type, chainId, nonce, value are numeric (where available)
   *  - gasPrice, gas, v, r, s, estimatedFee are hex strings with 0x prefix (where available)
   *  - input is always a string
   */
  def decodeRawTxToJson(rawIn: String): DecodedTxResponse = {
    implicit val formats: Formats = DefaultFormats
    val rawHex = normalizeHex(rawIn)
    val txType = Some(detectType(rawHex))

    val decoded: RawTransaction =
      try TransactionDecoder.decode(rawHex)
      catch {
        case e: Throwable => fatal(s"decode failed: ${e.getMessage}")
      }

    val (fromOpt, chainIdOpt, vHexOpt, rHexOpt, sHexOpt):
      (Option[String], Option[BigInteger], Option[String], Option[String], Option[String]) = decoded match {
      case srt: SignedRawTransaction =>
        val from = Option(srt.getFrom)
        val cid: Option[BigInteger] = try {
          val c = srt.getChainId // long in web3j 4.x; -1 if absent
          if (c > 0L) Some(BigInteger.valueOf(c)) else None
        } catch {
          case _: Throwable => None
        }
        val sig = srt.getSignatureData
        val vH = vToHex(sig, cid)
        val rH = W3Numeric.toHexString(sig.getR)
        val sH = W3Numeric.toHexString(sig.getS)
        (from, cid, Some(vH), Some(rH), Some(sH))
      case _ =>
        (None, None, None, None, None)
    }

    val hash = Hash.sha3(rawHex)
    val gasPriceHexOpt: Option[String] = Option(decoded.getGasPrice).map(W3Numeric.toHexStringWithPrefix)
    val gasLimitHexOpt: Option[String] = Option(decoded.getGasLimit).map(W3Numeric.toHexStringWithPrefix)
    // Convert value from WEI (BigInt) to ETH (BigDecimal) with 18 decimals
    val valueDecOpt: Option[String] = Option(decoded.getValue).map { wei =>
      (BigDecimal(wei) / BigDecimal("1000000000000000000")).toString()
    }
    val nonceDecOpt: Option[Long] = Option(decoded.getNonce).map(_.longValue())
    val toAddrOpt: Option[String] = Option(decoded.getTo)
    val inputData = Option(decoded.getData).getOrElse("0x")

    val estimatedFeeHexOpt =
      for {
        gp <- Option(decoded.getGasPrice)
        gl <- Option(decoded.getGasLimit)
      } yield W3Numeric.toHexStringWithPrefix(gp.multiply(gl))

    // Fallback: derive chainId from v when not provided by decoder (legacy EIP-155)
    val chainIdNumOpt: Option[Long] = chainIdOpt.map(_.longValue()).orElse {
      vHexOpt.flatMap { vh =>
        val hex = vh.stripPrefix("0x")
        if (hex.nonEmpty) {
          val vBI = new BigInteger(hex, 16)
          if (vBI.compareTo(BigInteger.valueOf(35)) >= 0) {
            val parity = if (vBI.testBit(0)) 1L else 0L
            Some(
              vBI
                .subtract(BigInteger.valueOf(35L + parity))
                .divide(BigInteger.valueOf(2L))
                .longValue()
            )
          } else None
        } else None
      }
    }

    DecodedTxResponse(
      hash = Some(hash),
      `type` = txType,
      chainId = chainIdNumOpt,
      nonce = nonceDecOpt,
      gasPrice = gasPriceHexOpt,
      gas = gasLimitHexOpt,
      to = toAddrOpt,
      value = valueDecOpt,
      input = Some(inputData),
      from = fromOpt,
      v = vHexOpt,
      r = rHexOpt,
      s = sHexOpt,
      estimatedFee = estimatedFeeHexOpt
    )

  }

}