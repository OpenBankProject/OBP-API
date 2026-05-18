package code.management

import cats.effect.IO
import code.api.util.{APIUtil, CustomJsonFormats}
import code.api.util.ErrorMessages._
import code.bankconnectors.LocalMappedConnectorInternal
import code.management.ImporterAPI._
import code.tesobe.ErrorMessage
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.Transaction
import net.liftweb.common.Full
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.{JArray, JValue}
import net.liftweb.util.Helpers._
import org.http4s.{Charset, HttpRoutes, MediaType, Request, Response, Status}
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`

/**
 * Native http4s route for the legacy `POST /obp_transactions_saver/api/transactions` endpoint.
 *
 * Mirrors `code.management.ImporterAPI`'s Lift `serve` block. Replaces
 * `LiftRules.statelessDispatch.append(ImporterAPI)` in `Boot.scala`.
 *
 * Status-code parity (preserved verbatim from the Lift handler):
 *   - secret query param missing               → 400 "secret missing"
 *   - secret wrong                             → 401 "wrong secret"
 *   - `importer_secret` prop not set on server → 400 "importer_secret not set on the server."
 *   - secret correct                           → 200 with JArray of inserted transactions
 *   - actor returns no envelopes               → 500
 */
object ImporterAPIRoutes extends MdcLoggable {

  private val jsonContentType: `Content-Type` =
    `Content-Type`(MediaType.application.json, Charset.`UTF-8`)

  private def errorResponse(message: String, httpCode: Int): IO[Response[IO]] = {
    val body = net.liftweb.json.compactRender(
      Extraction.decompose(ErrorMessage(message))(CustomJsonFormats.formats))
    IO.pure(
      Response[IO](Status.fromInt(httpCode).getOrElse(Status.BadRequest))
        .withEntity(body)
        .withContentType(jsonContentType)
    )
  }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "obp_transactions_saver" / "api" / "transactions" =>
      req.uri.query.params.get("secret") match {
        case None => errorResponse("secret missing", 400)
        case Some(provided) =>
          APIUtil.getPropsValue("importer_secret") match {
            case Full(expected) if expected == provided => saveTransactions(req)
            case Full(_)                                => errorResponse("wrong secret", 401)
            case _                                      => errorResponse("importer_secret not set on the server.", 400)
          }
      }
  }

  private def saveTransactions(req: Request[IO]): IO[Response[IO]] = {
    val ipAddress = req.remoteAddr.map(_.toUriString).getOrElse("")
    req.bodyText.compile.string.flatMap { bodyText =>
      IO.blocking(processBody(bodyText, ipAddress))
    }
  }

  // Synchronous: parses JSON, sends to TransactionInserter LiftActor via `!?`
  // (blocking ask), then updates account balance / last-updated timestamps.
  // Mirrors the Lift handler body in ImporterAPI.scala so behaviour
  // (status codes, response shape, side-effects) is preserved verbatim.
  private def processBody(bodyText: String, ipAddress: String): Response[IO] = {
    val parsedJson: JValue =
      scala.util.Try(net.liftweb.json.parse(bodyText)).getOrElse(JArray(Nil))
    val rawTransactions = parsedJson.children

    logger.info(
      "Received " + rawTransactions.size +
        " json transactions to insert from ip address " + ipAddress)

    val losslessFormats = CustomJsonFormats.losslessFormats
    val mf = implicitly[Manifest[ImporterTransaction]]
    val importerTransactions =
      rawTransactions.flatMap(j => j.extractOpt[ImporterTransaction](losslessFormats, mf))

    logger.info(
      "Received " + importerTransactions.size +
        " valid json transactions to insert from ip address " + ipAddress)

    if (importerTransactions.isEmpty) logger.warn("no transactions found to insert")

    val toInsert = TransactionsToInsert(importerTransactions)
    val createdEnvelopes = TransactionInserter !? (3.minutes, toInsert)

    createdEnvelopes match {
      case Full(inserted: InsertedTransactions) =>
        val insertedTs = inserted.l
        logger.info("inserted " + insertedTs.size + " transactions")
        updateBankAccountBalance(insertedTs)
        if (insertedTs.isEmpty && importerTransactions.nonEmpty) {
          // refresh account lastUpdate in case transactions were duplicates
          val mostRecentTransaction =
            importerTransactions.maxBy(t => t.obp_transaction.details.completed)
          val account = mostRecentTransaction.obp_transaction.this_account
          LocalMappedConnectorInternal
            .setBankAccountLastUpdated(account.bank.national_identifier, account.number, now)
            .openOrThrowException(attemptedToOpenAnEmptyBox)
        }
        val jsonList = insertedTs.map(whenAddedJson)
        Response[IO](Status.Ok)
          .withEntity(net.liftweb.json.compactRender(JArray(jsonList)))
          .withContentType(jsonContentType)
      case _ =>
        logger.warn("no envelopes inserted")
        Response[IO](Status.InternalServerError)
    }
  }

  private def updateBankAccountBalance(insertedTransactions: List[Transaction]): Unit = {
    if (insertedTransactions.nonEmpty) {
      val mostRecentTransaction = insertedTransactions.maxBy(t => t.finishDate)
      LocalMappedConnectorInternal
        .updateAccountBalance(
          mostRecentTransaction.bankId,
          mostRecentTransaction.accountId,
          mostRecentTransaction.balance)
        .openOrThrowException(attemptedToOpenAnEmptyBox)
    }
  }
}
