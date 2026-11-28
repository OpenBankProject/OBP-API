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

package code.api.v6_0_0

import org.json4s.jvalue2extractable
import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.attributedefinition.AttributeDefinitionDI
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.transactionattribute.TransactionAttributeX
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{BankId, TransactionId}
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType, TransactionAttributeType}
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

class TransactionsForBankAccountTest extends V600ServerSetup {

  // this suite reads the transactions created before each scenario
  override protected def needsTransactionData: Boolean = true

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getTransactionsForBankAccount))

  feature(s"Get Transactions for Account - $VersionOfApi") {

    // an attribute is shown on a view only when its Attribute Definition lists that view
    scenario("Only attributes defined as seen on the view are returned", ApiEndpoint1, VersionOfApi) {
      val bankId = testBankId1.value
      val accountId = testAccountId1.value
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / SYSTEM_OWNER_VIEW_ID / "transactions").GET <@ (user1)

      Given("a transaction on the account")
      val before = makeGetRequest(request)
      before.code should equal(200)
      val transactionId = before.body.extract[TransactionsJsonV600].transactions.head.transaction_id

      And("two attributes on it, one defined as seen on another view, one on the owner view")
      val hiddenName = "HIDDEN_" + APIUtil.generateUUID().take(8)
      val shownName = "SHOWN_" + APIUtil.generateUUID().take(8)
      Await.result(AttributeDefinitionDI.attributeDefinition.vend.createOrUpdateAttributeDefinition(
        BankId(bankId), hiddenName, AttributeCategory.Transaction, AttributeType.STRING, "", "", List("accountant"), true), 10.seconds)
      Await.result(AttributeDefinitionDI.attributeDefinition.vend.createOrUpdateAttributeDefinition(
        BankId(bankId), shownName, AttributeCategory.Transaction, AttributeType.STRING, "", "", List(SYSTEM_OWNER_VIEW_ID), true), 10.seconds)
      Await.result(TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
        BankId(bankId), TransactionId(transactionId), None, hiddenName, TransactionAttributeType.STRING, "secret"), 10.seconds)
      Await.result(TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
        BankId(bankId), TransactionId(transactionId), None, shownName, TransactionAttributeType.STRING, "visible"), 10.seconds)

      When("we get the transactions through the owner view")
      val response = makeGetRequest(request)

      Then("we get a 200 with only the attribute defined for the owner view")
      response.code should equal(200)
      val transaction = response.body.extract[TransactionsJsonV600].transactions.find(_.transaction_id == transactionId)
      transaction.isDefined should equal(true)
      val attributeNames = transaction.get.transaction_attributes.map(_.name)
      attributeNames should contain (shownName)
      attributeNames should not contain (hiddenName)
    }
  }
}
