package code.api.berlin.group.v1_3

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CustomJsonFormats
import code.api.util.{ApiTag, NewStyle}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.fx.fx
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.json
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer

object Http4sBGv13PIIS extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats

  // ResourceDoc example bodies are written as `json.parse(...)` (JValue). Since the json4s
  // migration, JValue itself extends scala.Product, so an implicit JValue => JvalueCaseClass
  // conversion never fires. Each example body is therefore wrapped explicitly in
  // JvalueCaseClass(...) so resource-docs serialization takes its special-case path (no field
  // reflection; the jvalueToCaseclass wrapper key is stripped) instead of reflecting on a raw JObject.

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion1
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  val bgV13Prefix = Root / ConstantsBG.berlinGroupVersion1.urlPrefix / ConstantsBG.berlinGroupVersion1.apiShortVersion

  // ── POST /funds-confirmations ─────────────────────────────────────
  // Lift source: code.api.builder.ConfirmationOfFundsServicePIISApi.checkAvailabilityOfFunds
  // Auth: authenticatedAccess → authMode UserOnly (default); user resolved by ResourceDocMiddleware.
  val checkAvailabilityOfFunds: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / "funds-confirmations" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Aisp(callContext)
          checkAvailabilityOfFundsJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CheckAvailabilityOfFundsJson ", 400, callContext) {
            json.parse(cc.httpBody.getOrElse("")).extract[CheckAvailabilityOfFundsJson]
          }

          requestAccountAmount <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${checkAvailabilityOfFundsJson.instructedAmount.amount} ", 400, callContext) {
            BigDecimal(checkAvailabilityOfFundsJson.instructedAmount.amount)
          }

          requestAccountCurrency = checkAvailabilityOfFundsJson.instructedAmount.currency

          _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${requestAccountCurrency}'", cc = callContext) {
            isValidCurrencyISOCode(requestAccountCurrency)
          }

          requestAccountIban = checkAvailabilityOfFundsJson.account.iban
          (bankAccount, callContext) <- NewStyle.function.getBankAccountByIban(requestAccountIban, callContext)
          currentAccountCurrency = bankAccount.currency
          currentAccountBalance = bankAccount.balance

          // From change from requestAccount Currency to currentBankAccount Currency
          rate = fx.exchangeRate(requestAccountCurrency, currentAccountCurrency, Some(bankAccount.bankId.value), callContext)

          _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion (${requestAccountCurrency} to ${currentAccountCurrency}) is not supported.", cc = callContext) {
            rate.isDefined
          }

          requestChangedCurrencyAmount = fx.convert(requestAccountAmount, rate)

          fundsAvailable = (currentAccountBalance >= requestChangedCurrencyAmount)

        } yield {
          com.openbankproject.commons.util.JsonAliases.parse(s"""{
               "fundsAvailable" : $fundsAvailable
             }""")
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(checkAvailabilityOfFunds),
    "POST",
    "/funds-confirmations",
    "Confirmation of Funds Request",
    s"""  ${mockedDataText(false)}
Creates a confirmation of funds request at the ASPSP. Checks whether a specific amount is available at point
of time of the request on an account linked to a given tuple card issuer(TPP)/card number, or addressed by
IBAN and TPP respectively. If the related extended services are used a conditional Consent-ID is contained
in the header. This field is contained but commented out in this specification.     """,
    JvalueCaseClass(json.parse(
      """{
       "instructedAmount" : {
         "amount" : "123",
         "currency" : "EUR"
       },
       "account" : {
         "iban" : "GR12 1234 5123 4511 3981 4475 477",
       }
      }""")),
    JvalueCaseClass(json.parse(
      """{
       "fundsAvailable" : true
      }""")),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Confirmation of Funds Service (PIIS)") :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(checkAvailabilityOfFunds)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    checkAvailabilityOfFunds(req)
  }
}
