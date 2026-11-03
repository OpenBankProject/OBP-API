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

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, CardAttributeNotFound, UserHasMissingRoles}
import code.cardattribute.MappedCardAttribute
import code.cards.MappedPhysicalCard
import code.entitlement.Entitlement
import code.model.dataAccess.MappedBankAccount
import code.setup.ServerSetupWithTestData
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

class CardAttributeDeleteTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag("deleteCardAttribute")

  def v7 = baseRequest / "obp" / "v7.0.0"

  private def addCard(): String = {
    val account = MappedBankAccount.find(
      By(MappedBankAccount.bank, testBankId1.value),
      By(MappedBankAccount.theAccountId, testAccountId1.value)
    ).openOrThrowException("test account not found")
    MappedPhysicalCard.create
      .mBankId(testBankId1.value)
      .mAccount(account.id.get)
      .mBankCardNumber(randomString(16))
      .mSerialNumber(randomString(10))
      .saveMe()
      .mCardId.get
  }

  private def addAttribute(cardId: String, name: String): String =
    MappedCardAttribute.create
      .mBankId(testBankId1.value)
      .mCardId(cardId)
      .mName(name)
      .mType("STRING")
      .mValue("123")
      .saveMe()
      .mCardAttributeId.get

  private def attributeUrl(cardId: String, attributeId: String) =
    v7 / "management" / "banks" / testBankId1.value / "cards" / cardId / "attributes" / attributeId

  private def attributeExists(attributeId: String): Boolean =
    MappedCardAttribute.find(By(MappedCardAttribute.mCardAttributeId, attributeId)).isDefined

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
  }
}
