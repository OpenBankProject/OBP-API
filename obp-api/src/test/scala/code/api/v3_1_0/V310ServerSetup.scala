/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.v3_1_0

import org.json4s._
import code.api.util.APIUtil.OAuth.{Consumer, Token, _}
import code.api.util.ApiRole.CanCreateProduct
import code.api.v1_2_1._
import code.api.v2_0_0.BasicAccountsJSON
import code.api.v3_0_0.{TransactionJsonV300, TransactionsJsonV300, ViewsJsonV300}
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import code.setup.OBPReq
import org.json4s.native.Serialization.write

import scala.util.Random.nextInt

trait V310ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v3_1_0_Request: OBPReq = baseRequest / "obp" / "v3.1.0"

  //When new version, this would be the first endpoint to test, to make sure it works well. 
  def getAPIInfo : APIResponse = {
    val request = v3_1_0_Request
    makeGetRequest(request)
    
  }

  def randomBankId : String = {
    def getBanksInfo : APIResponse  = {
      // getBanks of v5.0.0 and below is commented out, so the bank list comes from v5.1.0.
      val request = v5_1_0_Request / "banks"
      makeGetRequest(request)
    }
    val banksJson = getBanksInfo.body.extract[code.api.v4_0_0.BanksJson400]

    logger.trace(s"banksJson.banks.size is ${banksJson.banks.size}")
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }
  def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v3_1_0_Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken)
    makeGetRequest(request)
  }

  def randomPrivateAccountId(bankId : String) : String = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[BasicAccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition).id
  }

  def randomPrivateAccount(bankId : String): AccountJSON = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }

  def randomViewPermalink(bankId: String, account: AccountJSON) : String = {
    // getViewsForBankAccount of v4.0.0 and below is commented out; v5.1.0 serves the path with the
    // v5.0.0 handler, whose views have another shape, so only id and is_public are read.
    val request = v5_1_0_Request / "banks" / bankId / "accounts" / account.id / "views" <@(consumer, token1)
    val reply = makeGetRequest(request)
    val possibleViewsPermalinks = (reply.body \ "views").children
      .filterNot(v => (v \ "is_public").extractOpt[Boolean].contains(true))
      .map(v => (v \ "id").extract[String])
    val randomPosition = nextInt(possibleViewsPermalinks.size)
    possibleViewsPermalinks(randomPosition)
  }

  def getTransactions(bankId : String, accountId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)], params: List[(String, String)] = Nil): APIResponse = {
    val request = v3_1_0_Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" <@(consumerAndToken)
    makeGetRequest(request, params)
  }

  def randomTransaction(bankId : String, accountId : String, viewId: String) : TransactionJsonV300 = {
    val transactionsJson = getTransactions(bankId, accountId, viewId, user1).body.extract[TransactionsJsonV300].transactions
    val randomPosition = nextInt(transactionsJson.size)
    transactionsJson(randomPosition)
  }

  // v3.1.0 addCardForBank is commented out, so cards are created through v5.1.0 (the v5.0.0 handler),
  // whose body adds a brand. Returns the card id.
  def createCardViaKeptVersion(bankId: String, json: CreatePhysicalCardJsonV310): String = {
    val body = code.api.v5_0_0.CreatePhysicalCardJsonV500(
      card_number = json.card_number, card_type = json.card_type, name_on_card = json.name_on_card,
      issue_number = json.issue_number, serial_number = json.serial_number,
      valid_from_date = json.valid_from_date, expires_date = json.expires_date, enabled = json.enabled,
      technology = json.technology, networks = json.networks, allows = json.allows,
      account_id = json.account_id, replacement = json.replacement, pin_reset = json.pin_reset,
      collected = json.collected, posted = json.posted, customer_id = json.customer_id, brand = "Visa")
    val response = makePostRequest((v5_1_0_Request / "management" / "banks" / bankId / "cards").POST <@ (user1), write(body))
    response.code should equal(201)
    (response.body \ "card_id").extract[String]
  }

  // v3.1.0 createProduct is commented out, and no kept version stores category, family,
  // super_family or details. So the product is created through the connector, with every
  // v3.1.0 field, and returned in the v3.1.0 shape.
  def createProduct(bankId: String, code: String, json: PostPutProductJsonV310) = {
    val (created, _) = scala.concurrent.Await.result(
      _root_.code.api.util.NewStyle.function.createOrUpdateProduct(
        bankId = bankId,
        code = code,
        parentProductCode = Some(json.parent_product_code).filter(_.nonEmpty),
        name = json.name,
        category = json.category,
        family = json.family,
        superFamily = json.super_family,
        moreInfoUrl = json.more_info_url,
        termsAndConditionsUrl = null,
        details = json.details,
        description = json.description,
        metaLicenceId = json.meta.license.id,
        metaLicenceName = json.meta.license.name,
        callContext = None),
      scala.concurrent.duration.Duration(30, "seconds"))
    val product = JSONFactory310.createProductJson(created)
    product.code shouldBe code
    product.parent_product_code shouldBe json.parent_product_code
    product.bank_id shouldBe bankId
    product.name shouldBe json.name
    product.category shouldBe json.category
    product.super_family shouldBe json.super_family
    product.family shouldBe json.family
    product.more_info_url shouldBe json.more_info_url
    product.details shouldBe json.details
    product.description shouldBe json.description
    product
  }


  
}