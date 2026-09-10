package code.api.util

import code.accountholders.AccountHolders
import code.api.util.APIUtil.generateUUID
import code.consent.MappedConsent
import code.consent.ConsentStatus
import net.liftweb.util.Helpers.now
import code.model.dataAccess.ResourceUser
import code.setup.ServerSetup
import code.users.{AttributionPolicy, UserReference, Users}
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId}
import net.liftweb.common.{Failure, Full}
import org.json4s.JObject
import org.json4s.JsonDSL._
import org.scalatest.Tag

/**
 * Tests for the agent-on-behalf-of-human delegation primitives:
 *
 *  - LiftUsers.createResourceUser field assignments — pins the 2021 copy-paste bug where
 *    the createdByUserInvitationId None branch wiped CreatedByConsentId (the consent →
 *    agent linkage every delegation query joins through).
 *  - CallContext.onBehalfOfUserId — resolve-up from the authenticated caller (human
 *    or consent-minted agent) to the human the request is really about, including the
 *    branch an HTTP test cannot reach (the agent as the caller).
 */
class AgentDelegationTest extends ServerSetup {

  object AgentDelegationTag extends Tag("AgentDelegation")

  private def createUser(
    createdByConsentId: Option[String] = None,
    createdByUserInvitationId: Option[String] = None
  ): ResourceUser =
    Users.users.vend.createResourceUser(
      provider = "agent-delegation-test-provider",
      providerId = Some(generateUUID()),
      createdByConsentId = createdByConsentId,
      name = Some("agent-delegation-test-user"),
      email = None,
      userId = None,
      createdByUserInvitationId = createdByUserInvitationId,
      company = None,
      lastMarketingAgreementSignedDate = None
    ).openOrThrowException("Expected resource user to be created")

  private def storedField(value: Option[String]): String = value.getOrElse("")

  /**
   * A consent row naming `userId` as its human, or naming nobody when empty.
   *
   * Upstream writes these with `MappedConsent.create.mUserId(...).saveMe()`; MappedConsent is a
   * Doobie store on this branch, so the fixture goes through its insert instead. Only mUserId
   * matters to these scenarios - the resolver reads nothing else off the consent.
   */
  private def createConsent(userId: String = ""): MappedConsent =
    MappedConsent.insert(userId = userId, consumerId = "", status = ConsentStatus.ACCEPTED.toString)

  Feature("createResourceUser stores CreatedByConsentId and CreatedByUserInvitationId independently") {

    Scenario("consent id only — survives the invitation-id None branch", AgentDelegationTag) {
      val consentId = generateUUID()
      val user = createUser(createdByConsentId = Some(consentId))
      storedField(user.createdByConsentId) shouldBe consentId
      storedField(user.createdByUserInvitationId) shouldBe ""
    }

    Scenario("invitation id only", AgentDelegationTag) {
      val invitationId = generateUUID()
      val user = createUser(createdByUserInvitationId = Some(invitationId))
      storedField(user.createdByConsentId) shouldBe ""
      storedField(user.createdByUserInvitationId) shouldBe invitationId
    }

    Scenario("both ids set", AgentDelegationTag) {
      val consentId = generateUUID()
      val invitationId = generateUUID()
      val user = createUser(Some(consentId), Some(invitationId))
      storedField(user.createdByConsentId) shouldBe consentId
      storedField(user.createdByUserInvitationId) shouldBe invitationId
    }

    Scenario("neither id set", AgentDelegationTag) {
      val user = createUser()
      storedField(user.createdByConsentId) shouldBe ""
      storedField(user.createdByUserInvitationId) shouldBe ""
    }
  }

  Feature("CallContext.onBehalfOfUserId resolves the caller to the human the request is about") {

    Scenario("a plain human resolves to themselves", AgentDelegationTag) {
      val human = createUser()
      CallContext(user = Full(human)).onBehalfOfUserId shouldBe human.userId
    }

    Scenario("a consent-minted agent resolves to the granting human", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.insertWithConsentId(generateUUID(), userId = human.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      CallContext(user = Full(agent)).onBehalfOfUserId shouldBe human.userId
    }

    Scenario("an agent with a dangling consent id falls back to itself (fails closed)", AgentDelegationTag) {
      val agent = createUser(createdByConsentId = Some(generateUUID()))
      CallContext(user = Full(agent)).onBehalfOfUserId shouldBe agent.userId
    }

    Scenario("a populated consenter box wins over the DB chain", AgentDelegationTag) {
      val chainHuman = createUser()
      val consent = MappedConsent.insertWithConsentId(generateUUID(), userId = chainHuman.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val consenterHuman = createUser()
      CallContext(user = Full(agent), consenter = Full(consenterHuman))
        .onBehalfOfUserId shouldBe consenterHuman.userId
    }

    Scenario("consentCreator wins over consenter", AgentDelegationTag) {
      val agent = createUser()
      val consenterHuman = createUser()
      val explicitHuman = createUser()
      CallContext(
        user = Full(agent),
        consenter = Full(consenterHuman),
        consentCreator = Full(explicitHuman)
      ).onBehalfOfUserId shouldBe explicitHuman.userId
    }
  }

  Feature("Users.onBehalfOfUserIdOf — the resolver") {

    Scenario("an original user resolves to itself", AgentDelegationTag) {
      val human = createUser()
      Users.users.vend.onBehalfOfUserIdOf(human.userId) shouldBe Full(human.userId)
      Users.users.vend.actsForSelf(human.userId) shouldBe true
    }

    Scenario("a consent user resolves to the consent's user", AgentDelegationTag) {
      val human = createUser()
      val consent = createConsent(human.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      Users.users.vend.onBehalfOfUserIdOf(agent.userId) shouldBe Full(human.userId)
      Users.users.vend.actsForSelf(agent.userId) shouldBe false
    }

    Scenario("a dangling consent id keeps the caller (fails closed)", AgentDelegationTag) {
      val agent = createUser(createdByConsentId = Some(generateUUID()))
      Users.users.vend.onBehalfOfUserIdOf(agent.userId) shouldBe Full(agent.userId)
    }

    Scenario("an unknown user id keeps itself (fails closed)", AgentDelegationTag) {
      val id = generateUUID()
      Users.users.vend.onBehalfOfUserIdOf(id) shouldBe Full(id)
    }

    Scenario("BG-style: consent with no human yet keeps the caller, and is NOT pinned in the cache", AgentDelegationTag) {
      val consent = createConsent()   // mUserId empty until authorisation
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      Users.users.vend.onBehalfOfUserIdOf(agent.userId) shouldBe Full(agent.userId)
      val human = createUser()
      MappedConsent.setUserIdAndLastActionDate(consent.consentId, human.userId, now)          // authorisation binds the human
      Users.users.vend.onBehalfOfUserIdOf(agent.userId) shouldBe Full(human.userId)
    }

    Scenario("invariant: a consent whose user is itself a consent user is refused, not resolved", AgentDelegationTag) {
      val human = createUser()
      val consent1 = createConsent(human.userId)
      val agent1 = createUser(createdByConsentId = Some(consent1.consentId))
      val consent2 = createConsent(agent1.userId)   // names a consent user: data bug
      val agent2 = createUser(createdByConsentId = Some(consent2.consentId))
      Users.users.vend.onBehalfOfUserIdOf(agent2.userId) shouldBe a[Failure]
      // and CallContext falls back to the caller rather than throwing
      CallContext(user = Full(agent2)).onBehalfOfUserId shouldBe agent2.userId
    }
  }

  Feature("Users.attributionOf — the policy-aware entry point") {

    Scenario("KeepUserId stores the caller and does not consult the resolver", AgentDelegationTag) {
      val human = createUser()
      val consent = createConsent(human.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val a = Users.users.vend.attributionOf(agent.userId, UserReference.ConsentEntitlementUser).openOrThrowException("expected Full")
      a.userIdToStore shouldBe agent.userId
      a.onBehalfOfUserId shouldBe agent.userId
      a.isDelegated shouldBe false
      a.consentId shouldBe None
    }

    Scenario("UseOnBehalfOfUserId stores the on-behalf-of user and reports the consent", AgentDelegationTag) {
      val human = createUser()
      val consent = createConsent(human.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val a = Users.users.vend.attributionOf(agent.userId, UserReference.EntitlementUser).openOrThrowException("expected Full")
      a.userId shouldBe agent.userId
      a.onBehalfOfUserId shouldBe human.userId
      a.userIdToStore shouldBe human.userId
      a.isDelegated shouldBe true
      a.consentId shouldBe Some(consent.consentId)
      Users.users.vend.attributedUserId(agent.userId, UserReference.EntitlementUser) shouldBe Full(human.userId)
    }

    Scenario("UseOnBehalfOfUserId for an original user is a no-op with no consent", AgentDelegationTag) {
      val human = createUser()
      val a = Users.users.vend.attributionOf(human.userId, UserReference.AccountHolderUser).openOrThrowException("expected Full")
      a.userIdToStore shouldBe human.userId
      a.isDelegated shouldBe false
      a.consentId shouldBe None
    }

    Scenario("Reject is Full for an original user and Failure for a consent user", AgentDelegationTag) {
      val human = createUser()
      Users.users.vend.attributionOf(human.userId, UserReference.ConsentCreator).map(_.userIdToStore) shouldBe Full(human.userId)
      val consent = createConsent(human.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val rejected = Users.users.vend.attributionOf(agent.userId, UserReference.ConsentCreator)
      rejected shouldBe a[Failure]
      rejected.asInstanceOf[Failure].msg should include(ErrorMessages.InvalidUserId)
    }

    Scenario("the policy file is complete: every reference has a policy, a class and at least one field", AgentDelegationTag) {
      UserReference.all should not be empty
      UserReference.all.map(_.name).distinct.size shouldBe UserReference.all.size
      UserReference.all.foreach { r =>
        r.fields should not be empty
        Class.forName(r.mapperClass) // resolves, or the reference names a class that does not exist
      }
      UserReference.byPolicy(AttributionPolicy.Reject).map(_.name) should contain allOf ("ConsentCreator", "OAuthConsumerCreator")
    }
  }

  Feature("addEntitlement goes through the attribution policy") {

    Scenario("a grant targeting a consent user lands on its on-behalf-of user", AgentDelegationTag) {
      val human = createUser()
      val consent = createConsent(human.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val role = "CanGetConfig"
      val e = code.entitlement.Entitlement.entitlement.vend.addEntitlement("", agent.userId, role).openOrThrowException("expected the grant")
      e.userId shouldBe human.userId
    }

    Scenario("the consent engine's own scope copy stays on the consent user", AgentDelegationTag) {
      val human = createUser()
      val consent = createConsent(human.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val role = "CanGetConfig"
      val e = code.entitlement.Entitlement.entitlement.vend.addEntitlement("", agent.userId, role, createdByProcess = code.api.Constant.consent_user).openOrThrowException("expected the grant")
      e.userId shouldBe agent.userId
    }
  }

  Feature("getOrCreateAccountHolder goes through the attribution policy (AccountHolderUser)") {

    Scenario("an account created by a consent user is held by its on-behalf-of user", AgentDelegationTag) {
      val human = createUser()
      val consent = createConsent(human.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val account = BankIdAccountId(BankId("agent-delegation-bank"), AccountId(generateUUID()))
      val holder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(agent, account).openOrThrowException("expected the holder row")
      holder.userKey shouldBe human.userPrimaryKey.value
      AccountHolders.accountHolders.vend.getAccountHolders(account.bankId, account.accountId).map(_.userId) shouldBe Set(human.userId)
      AccountHolders.accountHolders.vend.getAccountsHeldByUser(agent) should not contain account
      AccountHolders.accountHolders.vend.getAccountsHeldByUser(human) should contain(account)
    }

    Scenario("an account created by an original user is held by that user", AgentDelegationTag) {
      val human = createUser()
      val account = BankIdAccountId(BankId("agent-delegation-bank"), AccountId(generateUUID()))
      val holder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(human, account).openOrThrowException("expected the holder row")
      holder.userKey shouldBe human.userPrimaryKey.value
      AccountHolders.accountHolders.vend.getAccountHolders(account.bankId, account.accountId).map(_.userId) shouldBe Set(human.userId)
    }

    Scenario("a consent user whose consent has no human yet keeps the row on itself (fails closed)", AgentDelegationTag) {
      val consent = createConsent()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val account = BankIdAccountId(BankId("agent-delegation-bank"), AccountId(generateUUID()))
      val holder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(agent, account).openOrThrowException("expected the holder row")
      holder.userKey shouldBe agent.userPrimaryKey.value
    }
  }

  Feature("DynamicData rows go through the attribution policy (DynamicDataUser), reads and writes alike") {

    val entityName = "agent_delegation_note"
    def noteJson(id: String): JObject = (s"${entityName}_id" -> id) ~ ("name" -> "written by an agent")
    def dynamicData = code.DynamicData.DynamicDataProvider.connectorMethodProvider.vend

    Scenario("a personal row written by a consent user belongs to its on-behalf-of user and is read back for both", AgentDelegationTag) {
      val human = createUser()
      val consent = createConsent(human.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val id = generateUUID()
      val saved = dynamicData.save(None, entityName, noteJson(id), Some(agent.userId), isPersonalEntity = true).openOrThrowException("expected the row")
      saved.userId shouldBe Some(human.userId)
      dynamicData.get(None, entityName, id, Some(agent.userId), isPersonalEntity = true).isDefined shouldBe true
      dynamicData.get(None, entityName, id, Some(human.userId), isPersonalEntity = true).isDefined shouldBe true
      dynamicData.getAll(None, entityName, Some(agent.userId), isPersonalEntity = true).flatMap(_.dynamicDataId) should contain(id)
      dynamicData.existsData(None, entityName, Some(agent.userId), isPersonalEntity = true) shouldBe true
      val updated = dynamicData.update(None, entityName, noteJson(id) merge (("name" -> "edited by the agent"): JObject), id, Some(agent.userId), isPersonalEntity = true).openOrThrowException("expected the update")
      updated.userId shouldBe Some(human.userId)
      dynamicData.delete(None, entityName, id, Some(agent.userId), isPersonalEntity = true) shouldBe Full(true)
      dynamicData.get(None, entityName, id, Some(human.userId), isPersonalEntity = true).isDefined shouldBe false
    }

    Scenario("a personal row written by an original user stays on that user", AgentDelegationTag) {
      val human = createUser()
      val id = generateUUID()
      val saved = dynamicData.save(None, entityName, noteJson(id), Some(human.userId), isPersonalEntity = true).openOrThrowException("expected the row")
      saved.userId shouldBe Some(human.userId)
      dynamicData.delete(None, entityName, id, Some(human.userId), isPersonalEntity = true) shouldBe Full(true)
    }

    Scenario("a dynamic entity definition created by a consent user is owned by its on-behalf-of user (DynamicEntityUser)", AgentDelegationTag) {
      val human = createUser()
      val consent = createConsent(human.userId)
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      // DynamicEntityCommons takes the stored shape: one root key named after the entity, flags beside it.
      val definition: JObject =
        (s"agent_delegation_def_${generateUUID().take(8)}" ->
          (("description" -> "definition created by an agent") ~ ("required" -> List("name")) ~
           ("properties" -> ("name" -> (("type" -> "string") ~ ("example" -> "x")))))) ~
        ("hasPersonalEntity" -> true)
      val provider = code.dynamicEntity.DynamicEntityProvider.connectorMethodProvider.vend
      val created = provider.createOrUpdate(code.dynamicEntity.DynamicEntityCommons(definition, None, agent.userId, None)).openOrThrowException("expected the definition")
      try created.userId shouldBe human.userId
      finally provider.delete(created)
    }
  }
}
