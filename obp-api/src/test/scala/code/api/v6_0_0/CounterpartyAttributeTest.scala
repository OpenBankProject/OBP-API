package code.api.v6_0_0

import org.json4s._
import java.util.UUID
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class CounterpartyAttributeTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object Create extends Tag(nameOf(Implementations6_0_0.createCounterpartyAttribute))
  object Update extends Tag(nameOf(Implementations6_0_0.updateCounterpartyAttribute))
  object Delete extends Tag(nameOf(Implementations6_0_0.deleteCounterpartyAttribute))
  object GetAll extends Tag(nameOf(Implementations6_0_0.getAllCounterpartyAttributes))
  object GetOne extends Tag(nameOf(Implementations6_0_0.getCounterpartyAttributeById))

  val bankId = testBankId1.value
  val accountId = testAccountId1.value
  lazy val counterpartyId = createMockCounterparty()
  lazy val attributeId = createMockAttribute(counterpartyId)

  def createMockCounterparty(): String = {
    val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString)
    counterparty.counterpartyId
  }

  def createMockAttribute(counterpartyId: String): String = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCounterpartyAttribute.toString)
    val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes").POST <@ user1
    val response = makePostRequest(request, write(counterpartyAttributeRequestJsonV600))
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    response.body.extract[CounterpartyAttributeResponseJsonV600].counterparty_attribute_id
  }

  feature("Create Counterparty Attribute") {

    scenario("401 Unauthorized", Create, VersionOfApi) {
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes").POST
      val response = makePostRequest(request, write(counterpartyAttributeRequestJsonV600))
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("403 Forbidden (no role)", Create, VersionOfApi) {
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes").POST <@ user1
      val response = makePostRequest(request, write(counterpartyAttributeRequestJsonV600))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(ErrorMessages.UserHasMissingRoles + CanCreateCounterpartyAttribute)
    }

    scenario("201 Success + Field Echo", Create, VersionOfApi) {
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCounterpartyAttribute.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes").POST <@ user1
      val response = makePostRequest(request, write(counterpartyAttributeRequestJsonV600))
      response.code should equal(201)
      val created = response.body.extract[CounterpartyAttributeResponseJsonV600]
      created.name should equal(counterpartyAttributeRequestJsonV600.name)
      created.attribute_type should equal(counterpartyAttributeRequestJsonV600.attribute_type)
      created.value should equal(counterpartyAttributeRequestJsonV600.value)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }

    scenario("400 Invalid Type", Create, VersionOfApi) {
      val badJson = counterpartyAttributeRequestJsonV600.copy(attribute_type = "UNSUPPORTED")
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCounterpartyAttribute.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes").POST <@ user1
      val response = makePostRequest(request, write(badJson))
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should include("field can only accept")
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Update Counterparty Attribute") {

    scenario("401 Unauthorized", Update, VersionOfApi) {
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes" / attributeId).PUT
      val response = makePutRequest(request, write(counterpartyAttributeRequestJsonV600))
      response.code should equal(401)
    }

    scenario("403 Forbidden", Update, VersionOfApi) {
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes" / attributeId).PUT <@ user1
      val response = makePutRequest(request, write(counterpartyAttributeRequestJsonV600))
      response.code should equal(403)
    }

    scenario("200 Success", Update, VersionOfApi) {
      lazy val counterpartyId = createMockCounterparty()
      lazy val attributeId = createMockAttribute(counterpartyId)

      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateCounterpartyAttribute.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes" / attributeId).PUT <@ user1
      val response = makePutRequest(request, write(counterpartyAttributeRequestJsonV600))
      response.code should equal(200)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Delete Counterparty Attribute") {
    lazy val counterpartyId = createMockCounterparty()
    lazy val attributeId = createMockAttribute(counterpartyId)
    scenario("401 Unauthorized", Delete, VersionOfApi) {
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes" / attributeId).DELETE
      val response = makeDeleteRequest(request)
      response.code should equal(401)
    }

    scenario("403 Forbidden", Delete, VersionOfApi) {
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes" / attributeId).DELETE <@ user1
      val response = makeDeleteRequest(request)
      response.code should equal(403)
    }

    scenario("204 Success", Delete, VersionOfApi) {
      lazy val counterpartyId = createMockCounterparty()
      lazy val attributeId = createMockAttribute(counterpartyId)

      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteCounterpartyAttribute.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes" / attributeId).DELETE <@ user1
      val response = makeDeleteRequest(request)
      response.code should equal(204)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Get All Counterparty Attributes") {
    lazy val counterpartyId = createMockCounterparty()
    lazy val attributeId = createMockAttribute(counterpartyId)
    scenario("401 Unauthorized", GetAll, VersionOfApi) {
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes").GET
      val response = makeGetRequest(request)
      response.code should equal(401)
    }

    scenario("403 Forbidden", GetAll, VersionOfApi) {
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes").GET <@ user1
      val response = makeGetRequest(request)
      response.code should equal(403)
    }

    scenario("200 Success", GetAll, VersionOfApi) {
      lazy val counterpartyId = createMockCounterparty()
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCounterpartyAttributes.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes").GET <@ user1
      val response = makeGetRequest(request)
      response.code should equal(200)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Get Counterparty Attribute by ID") {
    lazy val counterpartyId = createMockCounterparty()

    scenario("401 Unauthorized", GetOne, VersionOfApi) {
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes" / attributeId).GET
      val response = makeGetRequest(request)
      response.code should equal(401)
    }

    scenario("403 Forbidden", GetOne, VersionOfApi) {
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes" / attributeId).GET <@ user1
      val response = makeGetRequest(request)
      response.code should equal(403)
    }

    scenario("200 Success", GetOne, VersionOfApi) {
      lazy val counterpartyId = createMockCounterparty()
      lazy val attributeId = createMockAttribute(counterpartyId)
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCounterpartyAttribute.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "accounts" / accountId / "counterparties" / counterpartyId / "attributes" / attributeId).GET <@ user1
      val response = makeGetRequest(request)
      response.code should equal(200)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }
}
