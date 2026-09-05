package code.api.v6_0_0

import code.api.RequestHeader
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.Consent
import code.api.util.ErrorMessages._
import code.api.v3_1_0.{ConsentJsonV310, PostConsentChallengeJsonV310}
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * Personal ("my") dynamic entity endpoints and consent users. ON_BEHALF_OF_USER_ID_PLAN.md Phase 2 and
 * ideas/CONSENT_MY_RESOURCES.md (the block is accepted by the v6.0.0 create-consent endpoint):
 *  - rows written with a Consent belong to the User who granted it (DynamicDataUser attribution in the provider);
 *  - a consent user may use a /my endpoint only if its Consent lists the entity in my_resources with the needed
 *    action; the entity role is required in addition only when personal_requires_role is true.
 */
class DynamicEntityConsentUserTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ConsentUserTag extends Tag("DynamicEntityConsentUser")

  private val entityName = "test_consent_personal"
  private val roleEntityName = "test_consent_personal_role"
  private val createRole = s"CanCreateDynamicEntity_System$roleEntityName"

  private def definition(name: String, personalRequiresRole: Boolean): JValue =
    ("entity_name" -> name) ~
    ("has_personal_entity" -> true) ~
    ("personal_requires_role" -> personalRequiresRole) ~
    ("schema" -> parse(
      """{"description": "Personal entity for consent-user tests.", "required": ["name"],
        | "properties": {"name": {"type": "string", "maxLength": 40, "minLength": 1, "example": "Test"}}}""".stripMargin))

  private val consumerKeyHeader = List((RequestHeader.`Consumer-Key`, user1.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")))

  private def createSystemEntity(name: String, personalRequiresRole: Boolean = false): String = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
    val response = makePostRequest((v6_0_0_Request / "management" / "system-dynamic-entities").POST <@ (user1), write(definition(name, personalRequiresRole)))
    response.code should equal(201)
    (response.body \ "dynamic_entity_id").extract[String]
  }

  private def deleteSystemEntity(dynamicEntityId: String): Unit = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
    makeDeleteRequest((v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@ (user1))
  }

  private def personalEntity(name: String, actions: List[String]): JValue =
    ("bank_id" -> "") ~ ("entity_name" -> name) ~ ("actions" -> actions)

  private def consentBody(roleNames: List[String], myResources: Option[JValue]): JValue = {
    val base: JObject =
      ("everything" -> false) ~
      ("views" -> JArray(Nil)) ~
      ("entitlements" -> roleNames.map(role => ("bank_id" -> "") ~ ("role_name" -> role))) ~
      ("consumer_id" -> testConsumer.consumerId.get) ~
      ("time_to_live" -> 3600)
    myResources.map(mr => base ~ ("my_resources" -> mr)).getOrElse(base)
  }

  /** POST a consent as user1 carrying `roleNames` and the given my_resources block; returns the raw response. */
  private def postConsent(roleNames: List[String], myResources: Option[JValue]) = {
    setPropsValues("consents.allowed" -> "true", "consumer_validation_method_for_consent" -> "CONSUMER_KEY_VALUE")
    roleNames.foreach(role => Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role))
    makePostRequest((v6_0_0_Request / "my" / "consents" / "IMPLICIT").POST <@ (user1), write(consentBody(roleNames, myResources)), consumerKeyHeader)
  }

  /** A consent granted by user1, answered, as request headers. */
  private def consentHeaders(roleNames: List[String], myResources: Option[JValue]): List[(String, String)] = {
    val created = postConsent(roleNames, myResources)
    created.code should equal(201)
    val consent = created.body.extract[ConsentJsonV310]
    val answered = makePostRequest(
      (v5_1_0_Request / "banks" / testBankId1.value / "consents" / consent.consent_id / "challenge").POST <@ (user1),
      write(PostConsentChallengeJsonV310(answer = Consent.challengeAnswerAtTestEnvironment)))
    answered.code should equal(201)
    List((RequestHeader.`Consent-JWT`, consent.jwt)) ::: consumerKeyHeader
  }

  private val record: JValue = ("name" -> "written with a consent")

  private def idsOf(listResponse: JValue, name: String): List[String] =
    (listResponse \ s"${name}_list").extract[List[JObject]].map(o => (o \ s"${name}_id").extract[String])

  feature("Personal dynamic entity endpoints and consent users (my_resources)") {

    scenario("a User acting alone needs no role and no consent when personal_requires_role is false", VersionOfApi, ConsentUserTag) {
      val dynamicEntityId = createSystemEntity(entityName)
      try {
        val response = makePostRequest((dynamicEntity_Request / "my" / entityName).POST <@ (user1), write(record))
        response.code should equal(201)
      } finally deleteSystemEntity(dynamicEntityId)
    }

    scenario("a consent that does not list the entity is refused on /my, roles or not", VersionOfApi, ConsentUserTag) {
      val dynamicEntityId = createSystemEntity(entityName)
      try {
        val headers = consentHeaders(List(s"CanCreateDynamicEntity_System$entityName", s"CanGetDynamicEntity_System$entityName"), None)
        val create = makePostRequest((dynamicEntity_Request / "my" / entityName).POST, write(record), headers)
        create.code should equal(403)
        create.body.extract[ErrorMessage].message should include(ConsentMyResourcesMissing)
        create.body.extract[ErrorMessage].message should include(entityName)
        val list = makeGetRequest((dynamicEntity_Request / "my" / entityName).GET, headers)
        list.code should equal(403)
        list.body.extract[ErrorMessage].message should include(ConsentMyResourcesMissing)
      } finally deleteSystemEntity(dynamicEntityId)
    }

    scenario("a consent listing the entity with read and write acts on the granting User's rows, with no role", VersionOfApi, ConsentUserTag) {
      val dynamicEntityId = createSystemEntity(entityName)
      try {
        val headers = consentHeaders(Nil, Some(("personal_dynamic_entities" -> List(personalEntity(entityName, List("read", "write"))))))
        val create = makePostRequest((dynamicEntity_Request / "my" / entityName).POST, write(record), headers)
        create.code should equal(201)
        val rowId = (create.body \ entityName \ s"${entityName}_id").extract[String]

        And("the consent user reads the row it wrote")
        val asConsent = makeGetRequest((dynamicEntity_Request / "my" / entityName).GET, headers)
        asConsent.code should equal(200)
        idsOf(asConsent.body, entityName) should contain(rowId)

        And("the granting User sees the same row as their own")
        val asHuman = makeGetRequest((dynamicEntity_Request / "my" / entityName).GET <@ (user1))
        asHuman.code should equal(200)
        idsOf(asHuman.body, entityName) should contain(rowId)

        And("the stored row names the granting User, not the consent user")
        val stored = code.DynamicData.DynamicDataProvider.connectorMethodProvider.vend.get(None, entityName, rowId, Some(resourceUser1.userId), isPersonalEntity = true)
        stored.map(_.userId).openOrThrowException("expected the row") should equal(Some(resourceUser1.userId))

        And("the consent's my_resources block is echoed by GET /users/current")
        val current = makeGetRequest((v6_0_0_Request / "users" / "current").GET, headers)
        current.code should equal(200)
        ((current.body \ "my_resources" \ "personal_dynamic_entities")(0) \ "entity_name").extract[String] should equal(entityName)
      } finally deleteSystemEntity(dynamicEntityId)
    }

    scenario("a consent listing the entity read-only may read but not write", VersionOfApi, ConsentUserTag) {
      val dynamicEntityId = createSystemEntity(entityName)
      try {
        val headers = consentHeaders(Nil, Some(("personal_dynamic_entities" -> List(personalEntity(entityName, List("read"))))))
        val create = makePostRequest((dynamicEntity_Request / "my" / entityName).POST, write(record), headers)
        create.code should equal(403)
        create.body.extract[ErrorMessage].message should include(ConsentMyResourcesMissing)
        val list = makeGetRequest((dynamicEntity_Request / "my" / entityName).GET, headers)
        list.code should equal(200)
      } finally deleteSystemEntity(dynamicEntityId)
    }

    scenario("when personal_requires_role is true the consent must list the entity AND carry the role", VersionOfApi, ConsentUserTag) {
      val dynamicEntityId = createSystemEntity(roleEntityName, personalRequiresRole = true)
      try {
        val listed = Some(("personal_dynamic_entities" -> List(personalEntity(roleEntityName, List("read", "write")))): JValue)
        val withoutRole = consentHeaders(Nil, listed)
        val refused = makePostRequest((dynamicEntity_Request / "my" / roleEntityName).POST, write(record), withoutRole)
        refused.code should equal(403)
        refused.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
        val withRole = consentHeaders(List(createRole), listed)
        val accepted = makePostRequest((dynamicEntity_Request / "my" / roleEntityName).POST, write(record), withRole)
        accepted.code should equal(201)
      } finally deleteSystemEntity(dynamicEntityId)
    }

    scenario("creating a consent with an invalid my_resources entry is refused", VersionOfApi, ConsentUserTag) {
      val dynamicEntityId = createSystemEntity(entityName)
      try {
        val unknownEntity = postConsent(Nil, Some(("personal_dynamic_entities" -> List(personalEntity("no_such_entity", List("read"))))))
        unknownEntity.code should equal(400)
        unknownEntity.body.extract[ErrorMessage].message should include(ConsentMyResourcesInvalid)
        val badAction = postConsent(Nil, Some(("personal_dynamic_entities" -> List(personalEntity(entityName, List("delete"))))))
        badAction.code should equal(400)
        badAction.body.extract[ErrorMessage].message should include(ConsentMyResourcesInvalid)
        val noAction = postConsent(Nil, Some(("personal_dynamic_entities" -> List(personalEntity(entityName, Nil)))))
        noAction.code should equal(400)
      } finally deleteSystemEntity(dynamicEntityId)
    }
  }
}
