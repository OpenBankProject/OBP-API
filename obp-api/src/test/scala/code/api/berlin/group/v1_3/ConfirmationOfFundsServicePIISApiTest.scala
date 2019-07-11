package code.api.berlin.group.v1_3

import code.api.ErrorMessage
import code.api.builder.ConfirmationOfFundsServicePIISApi.APIMethods_ConfirmationOfFundsServicePIISApi
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{BankAccountNotFound, InvalidJsonContent, InvalidJsonFormat}
import code.model.dataAccess.MappedBankAccount
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class ConfirmationOfFundsServicePIISApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object PIIS extends Tag("Confirmation of Funds Service (PIIS)")
  object checkAvailabilityOfFunds extends Tag(nameOf(APIMethods_ConfirmationOfFundsServicePIISApi.checkAvailabilityOfFunds))

  val checkAvailabilityOfFundsJsonBody = APIMethods_ConfirmationOfFundsServicePIISApi
    .resourceDocs
    .filter( _.partialFunction == APIMethods_ConfirmationOfFundsServicePIISApi.checkAvailabilityOfFunds)
    .head.exampleRequestBody.asInstanceOf[JvalueCaseClass] //All the Json String convert to JvalueCaseClass implicitly 
    .jvalueToCaseclass
  

  feature(s"BG v1.3 - ${checkAvailabilityOfFunds.name}") {
    scenario("Failed Case, invalid Iban", BerlinGroupV1_3, PIIS, checkAvailabilityOfFunds) {
      val requestPost = (V1_3_BG / "funds-confirmations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(checkAvailabilityOfFundsJsonBody))


      Then("We should get a 400 ")
      response.code should equal(400)
      response.body.extract[ErrorMessage]
        .message should startWith(s"${BankAccountNotFound.replaceAll("BANK_ID and ACCOUNT_ID. ", "IBAN.")}")
    }

    scenario("Failed Case, invalid post json", BerlinGroupV1_3, PIIS, checkAvailabilityOfFunds) {
      val requestPost = (V1_3_BG / "funds-confirmations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, "")

      Then("We should get a 400 ")
      response.code should equal(400)
      response.body.extract[ErrorMessage]
        .message should startWith(InvalidJsonFormat)
    }
    
    scenario("Success case - Enough Funds", BerlinGroupV1_3, PIIS, checkAvailabilityOfFunds) {
      val accounts = MappedBankAccount.findAll().map(_.accountIban.get).filter(_ != null)
      val iban = accounts.head
      
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
      val accounts = MappedBankAccount.findAll().filter(_.accountIban.get != null)
      val iban = accounts.head.accountIban.get
      val balance = accounts.head.balance
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