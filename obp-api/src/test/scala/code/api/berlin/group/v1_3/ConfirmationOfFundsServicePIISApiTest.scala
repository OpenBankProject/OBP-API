package code.api.berlin.group.v1_3

import org.json4s._
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.ErrorMessagesBG
import com.openbankproject.commons.model.ErrorMessage
import code.api.berlin.group.v1_3.{Http4sBGv13PIIS => APIMethods_ConfirmationOfFundsServicePIISApi}
import code.api.util.APIUtil.OAuth._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{BankAccountNotFound, BankAccountNotFoundByIban, InvalidJsonContent, InvalidJsonFormat}
import code.model.dataAccess.{BankAccountRouting, MappedBankAccount}
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import com.openbankproject.commons.util.json
import org.json4s.native.Serialization.write
import net.liftweb.mapper.By
import org.scalatest.Tag

class ConfirmationOfFundsServicePIISApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object PIIS extends Tag("Confirmation of Funds Service (PIIS)")
  object checkAvailabilityOfFunds extends Tag(nameOf(APIMethods_ConfirmationOfFundsServicePIISApi.checkAvailabilityOfFunds))

  // The example body is a JvalueCaseClass when the ResourceDoc wraps it explicitly, but a raw
  // JValue when built via `json.parse(...)` alone (json4s JValue is already a scala.Product, so
  // the old lift-json-era implicit wrapping never fires). Accept both shapes.
  val checkAvailabilityOfFundsJsonBody: JValue = APIMethods_ConfirmationOfFundsServicePIISApi
    .resourceDocs
    .filter(_.partialFunctionName == "checkAvailabilityOfFunds")
    .head.exampleRequestBody match {
      case JvalueCaseClass(jvalue) => jvalue
      case jvalue: JValue => jvalue
      case other => Extraction.decompose(other)(CustomJsonFormats.formats)
    }
  

  feature(s"BG v1.3 - ${checkAvailabilityOfFunds.name}") {
    scenario("Failed Case, invalid Iban", BerlinGroupV1_3, PIIS, checkAvailabilityOfFunds) {
      val requestPost = (V1_3_BG / "funds-confirmations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(checkAvailabilityOfFundsJsonBody))


      Then("We should get a 404 ")
      response.code should equal(404)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(BankAccountNotFoundByIban)
    }

    scenario("Failed Case, invalid post json", BerlinGroupV1_3, PIIS, checkAvailabilityOfFunds) {
      val requestPost = (V1_3_BG / "funds-confirmations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, "")

      Then("We should get a 400 ")
      response.code should equal(400)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(InvalidJsonFormat)
    }
    
    scenario("Success case - Enough Funds", BerlinGroupV1_3, PIIS, checkAvailabilityOfFunds) {
      val accountsIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val iban = accountsIban.head.accountRouting.address
      
      val checkAvailabilityOfFundsJsonBody = json.parse(
        s"""{
          "instructedAmount" : {
            "amount" : "123",
            "currency" : "EUR"
          },
          "account" : {
            "iban" : "$iban",
          }
         }""")
      
      val requestPost = (V1_3_BG / "funds-confirmations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(checkAvailabilityOfFundsJsonBody))


      Then("We should get a 200 ")
      response.code should equal(200)
      (response.body \ "fundsAvailable").extract[Boolean] should be (true)
    }

    scenario("Success case - Not Enough Funds", BerlinGroupV1_3, PIIS, checkAvailabilityOfFunds) {
      val accountsIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val iban = accountsIban.head.accountRouting.address
      val account = MappedBankAccount.find(
        By(MappedBankAccount.bank, accountsIban.head.bankId.value),
        By(MappedBankAccount.theAccountId, accountsIban.head.accountId.value)).openOrThrowException("Can not be empty here")
      val balance = account.balance
      val laggerbalance = balance +1000

      val checkAvailabilityOfFundsJsonBody = json.parse(
        s"""{
          "instructedAmount" : {
            "amount" : "${laggerbalance.toString}",
            "currency" : "EUR"
          },
          "account" : {
            "iban" : "$iban",
          }
         }""")

      val requestPost = (V1_3_BG / "funds-confirmations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(checkAvailabilityOfFundsJsonBody))


      Then("We should get a 200 ")
      response.code should equal(200)
      (response.body \ "fundsAvailable").extract[Boolean] should be (false)
    }
  }

}