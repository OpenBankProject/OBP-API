package code.api.v5_1_0

import org.json4s._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class RegulatedEntityAttributeTest extends V510ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object Create extends Tag(nameOf(Implementations5_1_0.createRegulatedEntityAttribute))
  object Update extends Tag(nameOf(Implementations5_1_0.updateRegulatedEntityAttribute))
  object Delete extends Tag(nameOf(Implementations5_1_0.deleteRegulatedEntityAttribute))
  object GetAll extends Tag(nameOf(Implementations5_1_0.getAllRegulatedEntityAttributes))
  object GetOne extends Tag(nameOf(Implementations5_1_0.getRegulatedEntityAttributeById))

  lazy val entityId = createMockRegulatedEntity()
  lazy val attributeId = createMockAttribute(entityId)
  
  def createMockRegulatedEntity(): String = {
    val json = regulatedEntityPostJsonV510
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRegulatedEntity.toString)
    val request = (v5_1_0_Request / "regulated-entities").POST <@ user1
    val response = makePostRequest(request, write(json))
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    (response.body.extract[RegulatedEntityJsonV510].entity_id)
  }

  def createMockAttribute(entityId: String): String = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRegulatedEntityAttribute.toString)
    val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes").POST <@ user1
    val response = makePostRequest(request, write(regulatedEntityAttributeRequestJsonV510))
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    response.body.extract[RegulatedEntityAttributeResponseJsonV510].regulated_entity_attribute_id
  }
  
  feature("Create Regulated Entity Attribute") {
    
    scenario("401 Unauthorized", Create, VersionOfApi) {
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes").POST
      val response = makePostRequest(request, write(regulatedEntityAttributeRequestJsonV510))
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("403 Forbidden (no role)", Create, VersionOfApi) {
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes").POST <@ user1
      val response = makePostRequest(request, write(regulatedEntityAttributeRequestJsonV510))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(ErrorMessages.UserHasMissingRoles + CanCreateRegulatedEntityAttribute)
    }

    scenario("201 Success + Field Echo", Create, VersionOfApi) {
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRegulatedEntityAttribute.toString)
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes").POST <@ user1
      val response = makePostRequest(request, write(regulatedEntityAttributeRequestJsonV510))
      response.code should equal(201)
      val created = response.body.extract[RegulatedEntityAttributeResponseJsonV510]
      created.name should equal(regulatedEntityAttributeRequestJsonV510.name)
      created.attribute_type should equal(regulatedEntityAttributeRequestJsonV510.attribute_type)
      created.value should equal(regulatedEntityAttributeRequestJsonV510.value)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }

    scenario("400 Invalid Type", Create, VersionOfApi) {
      val badJson = regulatedEntityAttributeRequestJsonV510.copy(attribute_type = "UNSUPPORTED")
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRegulatedEntityAttribute.toString)
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes").POST <@ user1
      val response = makePostRequest(request, write(badJson))
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should include("field can only accept")
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Update Regulated Entity Attribute") {
    
    scenario("401 Unauthorized", Update, VersionOfApi) {
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes" / attributeId).PUT
      val response = makePutRequest(request, write(regulatedEntityAttributeRequestJsonV510))
      response.code should equal(401)
    }

    scenario("403 Forbidden", Update, VersionOfApi) {
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes" / attributeId).PUT <@ user1
      val response = makePutRequest(request, write(regulatedEntityAttributeRequestJsonV510))
      response.code should equal(403)
    }

    scenario("200 Success", Update, VersionOfApi) {
      lazy val entityId = createMockRegulatedEntity()
      lazy val attributeId = createMockAttribute(entityId)
      
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateRegulatedEntityAttribute.toString)
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes" / attributeId).PUT <@ user1
      val response = makePutRequest(request, write(regulatedEntityAttributeRequestJsonV510))
      response.code should equal(200) 
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Delete Regulated Entity Attribute") {
    lazy val entityId = createMockRegulatedEntity()
    lazy val attributeId = createMockAttribute(entityId)
    scenario("401 Unauthorized", Delete, VersionOfApi) {
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes" / attributeId).DELETE
      val response = makeDeleteRequest(request)
      response.code should equal(401)
    }

    scenario("403 Forbidden", Delete, VersionOfApi) {
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes" / attributeId).DELETE <@ user1
      val response = makeDeleteRequest(request)
      response.code should equal(403)
    }

    scenario("204 Success", Delete, VersionOfApi) {
      lazy val entityId = createMockRegulatedEntity()
      lazy val attributeId = createMockAttribute(entityId)
      
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteRegulatedEntityAttribute.toString)
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes" / attributeId).DELETE <@ user1
      val response = makeDeleteRequest(request)
      response.code should equal(204)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Get All Regulated Entity Attributes") {
    lazy val entityId = createMockRegulatedEntity()
    lazy val attributeId = createMockAttribute(entityId)
    scenario("401 Unauthorized", GetAll, VersionOfApi) {
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes").GET
      val response = makeGetRequest(request)
      response.code should equal(401)
    }

    scenario("403 Forbidden", GetAll, VersionOfApi) {
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes").GET <@ user1
      val response = makeGetRequest(request)
      response.code should equal(403)
    }

    scenario("200 Success", GetAll, VersionOfApi) {
      lazy val entityId = createMockRegulatedEntity()
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetRegulatedEntityAttributes.toString)
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes").GET <@ user1
      val response = makeGetRequest(request)
      response.code should equal(200)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Get Regulated Entity Attribute by ID") {
    lazy val entityId = createMockRegulatedEntity()
    
    scenario("401 Unauthorized", GetOne, VersionOfApi) {
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes" / attributeId).GET
      val response = makeGetRequest(request)
      response.code should equal(401)
    }

    scenario("403 Forbidden", GetOne, VersionOfApi) {
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes" / attributeId).GET <@ user1
      val response = makeGetRequest(request)
      response.code should equal(403)
    }

    scenario("200 Success", GetOne, VersionOfApi) {
      lazy val entityId = createMockRegulatedEntity()
      lazy val attributeId = createMockAttribute(entityId)
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetRegulatedEntityAttribute.toString)
      val request = (v5_1_0_Request / "regulated-entities" / entityId / "attributes" / attributeId).GET <@ user1
      val response = makeGetRequest(request)
      response.code should equal(200)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }
}
