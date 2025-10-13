package code.bankconnectors.ethereum

import code.api.util.APIUtil._
import code.api.util.{CallContext, ErrorMessages, NewStyle}
import code.api.v6_0_0.TransactionRequestBodyEthereumJsonV600
import code.bankconnectors._
import code.util.AkkaHttpClient._
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._
import net.liftweb.common._
import net.liftweb.json
import net.liftweb.json.JValue

import scala.collection.mutable.ArrayBuffer

/**
  * EthereumConnector_vSept2025
  * Minimal JSON-RPC based connector to send ETH between two addresses.
  *
  * Notes
  *  - Supports two modes:
  *      1) If transactionRequestCommonBody.description is a 0x-hex string, use eth_sendRawTransaction
  *      2) Otherwise fallback to eth_sendTransaction (requires unlocked accounts, e.g. Anvil)
  *  - For public RPC providers, prefer locally signed tx + eth_sendRawTransaction
  *  - BankAccount.accountId.value is expected to hold the 0x Ethereum address
  */
trait EthereumConnector_vSept2025 extends Connector with MdcLoggable {

  implicit override val nameOfConnector = EthereumConnector_vSept2025.toString

  override val messageDocs = ArrayBuffer[MessageDoc]()

  private def rpcUrl: String = getPropsValue("ethereum.rpc.url").getOrElse("http://127.0.0.1:8545")

  private def ethToWeiHex(amountEth: BigDecimal): String = {
    val wei = amountEth.bigDecimal.movePointRight(18).toBigIntegerExact()
    "0x" + wei.toString(16)
  }

  override def makePaymentv210(
    fromAccount: BankAccount,
    toAccount: BankAccount,
    transactionRequestId: TransactionRequestId,
    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
    amount: BigDecimal,
    description: String,
    transactionRequestType: TransactionRequestType,
    chargePolicy: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TransactionId]] = {

    val from = fromAccount.accountId.value
    val to   = toAccount.accountId.value
    val valueHex = ethToWeiHex(amount)

    val maybeRawTx: Option[String] = transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyEthereumJsonV600].params.map(_.trim).filter(s => s.startsWith("0x") && s.length > 2)

    val safeFrom = if (from.length > 10) from.take(10) + "..." else from
    val safeTo   = if (to.length > 10) to.take(10) + "..." else to
    val safeVal  = if (valueHex.length > 14) valueHex.take(14) + "..." else valueHex
    logger.debug(s"EthereumConnector_vSept2025.makePaymentv210 → from=$safeFrom to=$safeTo value=$safeVal url=${rpcUrl}")

    val payload = maybeRawTx match {
      case Some(raw) =>
        s"""
           |{
           |  "jsonrpc":"2.0",
           |  "method":"eth_sendRawTransaction",
           |  "params":["$raw"],
           |  "id":1
           |}
           |""".stripMargin
      case None =>
        s"""
           |{
           |  "jsonrpc":"2.0",
           |  "method":"eth_sendTransaction",
           |  "params":[{
           |    "from":"$from",
           |    "to":"$to",
           |    "value":"$valueHex"
           |  }],
           |  "id":1
           |}
           |""".stripMargin
    }

    for {
      request <- NewStyle.function.tryons(ErrorMessages.UnknownError + " Failed to build HTTP request", 500, callContext) {prepareHttpRequest(rpcUrl, _root_.akka.http.scaladsl.model.HttpMethods.POST, _root_.akka.http.scaladsl.model.HttpProtocol("HTTP/1.1"), payload)
      }

      response <- NewStyle.function.tryons(ErrorMessages.UnknownError + " Failed to call Ethereum RPC", 500, callContext) {
        makeHttpRequest(request)
      }.flatten

      body <- NewStyle.function.tryons(ErrorMessages.UnknownError + " Failed to read Ethereum RPC response", 500, callContext) {
        response.entity.dataBytes.runFold(_root_.akka.util.ByteString(""))(_ ++ _).map(_.utf8String)
      }.flatten

      _ <- Helper.booleanToFuture(ErrorMessages.UnknownError + s" Ethereum RPC returned error: ${response.status.value}", 500, callContext) {
        logger.debug(s"EthereumConnector_vSept2025.makePaymentv210 response: $body")
        response.status.isSuccess()
      }

      txIdBox <- {
        implicit val formats = json.DefaultFormats
        val j: JValue = json.parse(body)
        val errorNode = (j \ "error")
        if (errorNode != json.JNothing && errorNode != json.JNull) {
          val msg = (errorNode \ "message").extractOpt[String].getOrElse("Unknown Ethereum RPC error")
          val code = (errorNode \ "code").extractOpt[BigInt].map(_.toString).getOrElse("?")
          scala.concurrent.Future.successful(Failure(s"Ethereum RPC error(code=$code): $msg"))
        } else {
          NewStyle.function.tryons(ErrorMessages.InvalidJsonFormat + " Failed to parse Ethereum RPC response", 500, callContext) {
            val resultHashOpt: Option[String] =
              (j \ "result").extractOpt[String]
                .orElse((j \ "result" \ "hash").extractOpt[String])
                .orElse((j \ "result" \ "transactionHash").extractOpt[String])
            resultHashOpt match {
              case Some(hash) if hash.nonEmpty => TransactionId(hash)
              case _ => throw new RuntimeException("Empty transaction hash")
            }
          }.map(Full(_))
        }
      }
    } yield {
      (txIdBox, callContext)
    }
  }
}

object EthereumConnector_vSept2025 extends EthereumConnector_vSept2025