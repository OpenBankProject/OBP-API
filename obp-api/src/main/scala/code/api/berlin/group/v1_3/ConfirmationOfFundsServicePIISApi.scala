package code.api.builder.ConfirmationOfFundsServicePIISApi

import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.berlin.group.v1_3.{JvalueCaseClass, OBP_BERLIN_GROUP_1_3}
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiTag, NewStyle}
import code.fx.fx
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

object APIMethods_ConfirmationOfFundsServicePIISApi extends RestHelper {
    val apiVersion =  OBP_BERLIN_GROUP_1_3.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      checkAvailabilityOfFunds ::
      Nil

            
     resourceDocs += ResourceDoc(
       checkAvailabilityOfFunds,
       apiVersion,
       nameOf(checkAvailabilityOfFunds),
       "POST",
       "/funds-confirmations",
       "Confirmation of Funds Request",
       s"""  ${mockedDataText(false)}
Creates a confirmation of funds request at the ASPSP. Checks whether a specific amount is available at point
of time of the request on an account linked to a given tuple card issuer(TPP)/card number, or addressed by 
IBAN and TPP respectively. If the related extended services are used a conditional Consent-ID is contained 
in the header. This field is contained but commented out in this specification.     """,
       json.parse(
         """{
          "instructedAmount" : {
            "amount" : "123",
            "currency" : "EUR"
          },
          "account" : {
            "iban" : "GR12 1234 5123 4511 3981 4475 477",
          }
         }"""),
       json.parse(
         """{
          "fundsAvailable" : true
         }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Confirmation of Funds Service (PIIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val checkAvailabilityOfFunds : OBPEndpoint = {
       case "funds-confirmations" ::  Nil JsonPost json -> _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             checkAvailabilityOfFundsJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CheckAvailabilityOfFundsJson ", 400, callContext) {
               json.extract[CheckAvailabilityOfFundsJson]
             }

             requestAccountAmount <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${checkAvailabilityOfFundsJson.instructedAmount.amount} ", 400, callContext) {
               BigDecimal(checkAvailabilityOfFundsJson.instructedAmount.amount)
             }

             requestAccountCurrency = checkAvailabilityOfFundsJson.instructedAmount.currency

             _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${ requestAccountCurrency}'") {
               isValidCurrencyISOCode(requestAccountCurrency)
             }

             requestAccountIban = checkAvailabilityOfFundsJson.account.iban
             (bankAccount, callContext) <- NewStyle.function.getBankAccountByIban(requestAccountIban, callContext)
             currentAccountCurrency = bankAccount.currency
             currentAccountBalance = bankAccount.balance


             //From change from requestAccount Currency to currentBankAccount Currency
             rate = fx.exchangeRate(requestAccountCurrency, currentAccountCurrency, Some(bankAccount.bankId.value))

             _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion (${requestAccountCurrency} to ${currentAccountCurrency}) is not supported.") {
               rate.isDefined
             }

             requestChangedCurrencyAmount = fx.convert(requestAccountAmount, rate)

             fundsAvailable = (currentAccountBalance >= requestChangedCurrencyAmount)
            
             } yield {
             (net.liftweb.json.parse(s"""{
                  "fundsAvailable" : $fundsAvailable
                }"""), 
               callContext)
           }
         }
       }

}



