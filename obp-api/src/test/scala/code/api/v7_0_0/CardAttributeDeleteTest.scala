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
package code.api.v7_0_0

import org.json4s.jvalue2extractable
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, CardAttributeNotFound, NotImplemented, UserHasMissingRoles}
import code.api.util.APIUtil
import code.cardattribute.DoobieCardAttributeProvider
import code.cards.MappedPhysicalCard
import code.entitlement.Entitlement
import code.methodrouting.{MethodRouting, MethodRoutingCommons, MethodRoutingProvider}
import code.model.dataAccess.MappedBankAccount
import code.setup.ServerSetupWithTestData
import com.openbankproject.commons.model.{BankId, ErrorMessage}
import com.openbankproject.commons.model.enums.CardAttributeType
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration._

class CardAttributeDeleteTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag("deleteCardAttribute")

  def v7 = baseRequest / "obp" / "v7.0.0"

  // The card is written straight to the store with only the fields the scenarios need; the rest take
  // empty values (the store binds every column, so a Mapper default of NULL becomes "" here) and the
  // card id is a fresh UUID, which is what the Mapper field defaulted to.
  private def addCard(): String = {
    val account = MappedBankAccount.find(testBankId1.value, testAccountId1.value)
      .openOrThrowException("test account not found")
    MappedPhysicalCard.insert(
      cardId = APIUtil.generateUUID(), bankId = testBankId1.value, bankCardNumber = randomString(16),
      cardType = "", nameOnCard = "", issueNumber = "", serialNumber = randomString(10),
      validFrom = new Date(), expires = new Date(), enabled = false, cancelled = false, onHotList = false,
      technology = "", networks = "", allows = "", accountKey = account.accountPrimaryKey,
      replacementDate = None, replacementReason = None, collected = None, posted = None,
      customerId = "", cvv = "", brand = ""
    ).cardId
  }

  private def addAttribute(cardId: String, name: String, bankId: String = testBankId1.value): String =
    Await.result(DoobieCardAttributeProvider.createOrUpdateCardAttribute(
      Some(BankId(bankId)), Some(cardId), None, name, CardAttributeType.STRING, "123"), 30.seconds)
      .openOrThrowException("expected the card attribute to be created")
      .cardAttributeId.getOrElse(fail("the created card attribute has no id"))

  private def attributeUrl(cardId: String, attributeId: String) =
    v7 / "management" / "banks" / testBankId1.value / "cards" / cardId / "attributes" / attributeId

  private def attributeExists(attributeId: String): Boolean =
    Await.result(DoobieCardAttributeProvider.getCardAttributeById(attributeId), 30.seconds).isDefined

  feature("Delete Card Attribute") {
    scenario("Without a logged-in user", ApiEndpoint, VersionOfApi) {
      val response = makeDeleteRequest(attributeUrl("any-card-id", "any-attribute-id").DELETE)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should startWith(AuthenticatedUserIsRequired)
    }

    scenario("Without the role", ApiEndpoint, VersionOfApi) {
      val cardId = addCard()
      val attributeId = addAttribute(cardId, "TAX_NUMBER")
      val response = makeDeleteRequest(attributeUrl(cardId, attributeId).DELETE <@ (user2))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should (startWith(UserHasMissingRoles) and include(ApiRole.canDeleteCardsForBank.toString))
      attributeExists(attributeId) should equal(true)
    }

    scenario("With the role the attribute is deleted and the card's other attributes are kept", ApiEndpoint, VersionOfApi) {
      val cardId = addCard()
      val attributeId = addAttribute(cardId, "TAX_NUMBER")
      val otherAttributeId = addAttribute(cardId, "SEGMENT")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canDeleteCardsForBank.toString)

      val response = makeDeleteRequest(attributeUrl(cardId, attributeId).DELETE <@ (user1))

      response.code should equal(204)
      attributeExists(attributeId) should equal(false)
      attributeExists(otherAttributeId) should equal(true)
    }

    scenario("An unknown attribute answers 404", ApiEndpoint, VersionOfApi) {
      val cardId = addCard()
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canDeleteCardsForBank.toString)

      val response = makeDeleteRequest(attributeUrl(cardId, "does-not-exist").DELETE <@ (user1))

      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(CardAttributeNotFound)
    }

    scenario("An attribute of another card is not deleted through this card", ApiEndpoint, VersionOfApi) {
      val cardId = addCard()
      val otherCardId = addCard()
      val otherAttributeId = addAttribute(otherCardId, "TAX_NUMBER")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canDeleteCardsForBank.toString)

      val response = makeDeleteRequest(attributeUrl(cardId, otherAttributeId).DELETE <@ (user1))

      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(CardAttributeNotFound)
      attributeExists(otherAttributeId) should equal(true)
    }

    scenario("An attribute held for another bank is not deleted through this bank", ApiEndpoint, VersionOfApi) {
      val cardId = addCard()
      val attributeId = addAttribute(cardId, "TAX_NUMBER", bankId = testBankId2.value)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canDeleteCardsForBank.toString)

      val response = makeDeleteRequest(attributeUrl(cardId, attributeId).DELETE <@ (user1))

      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(CardAttributeNotFound)
      attributeExists(attributeId) should equal(true)
    }

    scenario("The role at another bank does not allow it", ApiEndpoint, VersionOfApi) {
      val cardId = addCard()
      val attributeId = addAttribute(cardId, "TAX_NUMBER")
      Entitlement.entitlement.vend.addEntitlement(testBankId2.value, resourceUser1.userId, ApiRole.canDeleteCardsForBank.toString)

      val response = makeDeleteRequest(attributeUrl(cardId, attributeId).DELETE <@ (user1))

      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
      attributeExists(attributeId) should equal(true)
    }

    scenario("The delete goes to the connector the method is routed to", ApiEndpoint, VersionOfApi) {
      val cardId = addCard()
      val attributeId = addAttribute(cardId, "TAX_NUMBER")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canDeleteCardsForBank.toString)
      // A connector that does not delete card attributes: what it answers is what the caller gets.
      val routing = MethodRoutingProvider.connectorMethodProvider.vend.createOrUpdate(MethodRoutingCommons(
        "deleteCardAttribute", "cardano_vJun2025", isBankIdExactMatch = false, bankIdPattern = Some(MethodRouting.bankIdPatternMatchAny)
      )).openOrThrowException("expected the method routing to be created")
      try {
        val response = makeDeleteRequest(attributeUrl(cardId, attributeId).DELETE <@ (user1))

        response.code should equal(400)
        response.body.extract[ErrorMessage].message should (include(NotImplemented.trim) and include("deleteCardAttribute"))
        attributeExists(attributeId) should equal(true)
      } finally MethodRoutingProvider.connectorMethodProvider.vend.delete(routing.methodRoutingId.get)
    }
  }
}
