package code.api.util

import code.api.util.APIUtil.generateUUID
import code.consent.MappedConsent
import code.model.dataAccess.ResourceUser
import code.setup.ServerSetup
import code.users.Users
import net.liftweb.common.Full
import org.scalatest.Tag

/**
 * Tests for the agent-on-behalf-of-human delegation primitives:
 *
 *  - LiftUsers.createResourceUser field assignments — pins the 2021 copy-paste bug where
 *    the createdByUserInvitationId None branch wiped CreatedByConsentId (the consent →
 *    agent linkage every delegation query joins through).
 *  - CallContext.accountableUserId — resolve-up from the authenticated caller (human
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

  private def storedField(value: String): String = Option(value).getOrElse("")

  feature("createResourceUser stores CreatedByConsentId and CreatedByUserInvitationId independently") {

    scenario("consent id only — survives the invitation-id None branch", AgentDelegationTag) {
      val consentId = generateUUID()
      val user = createUser(createdByConsentId = Some(consentId))
      storedField(user.CreatedByConsentId.get) shouldBe consentId
      storedField(user.CreatedByUserInvitationId.get) shouldBe ""
    }

    scenario("invitation id only", AgentDelegationTag) {
      val invitationId = generateUUID()
      val user = createUser(createdByUserInvitationId = Some(invitationId))
      storedField(user.CreatedByConsentId.get) shouldBe ""
      storedField(user.CreatedByUserInvitationId.get) shouldBe invitationId
    }

    scenario("both ids set", AgentDelegationTag) {
      val consentId = generateUUID()
      val invitationId = generateUUID()
      val user = createUser(Some(consentId), Some(invitationId))
      storedField(user.CreatedByConsentId.get) shouldBe consentId
      storedField(user.CreatedByUserInvitationId.get) shouldBe invitationId
    }

    scenario("neither id set", AgentDelegationTag) {
      val user = createUser()
      storedField(user.CreatedByConsentId.get) shouldBe ""
      storedField(user.CreatedByUserInvitationId.get) shouldBe ""
    }
  }

  feature("CallContext.accountableUserId resolves the caller to the human the request is about") {

    scenario("a plain human resolves to themselves", AgentDelegationTag) {
      val human = createUser()
      CallContext(user = Full(human)).accountableUserId shouldBe human.userId
    }

    scenario("a consent-minted agent resolves to the granting human", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      CallContext(user = Full(agent)).accountableUserId shouldBe human.userId
    }

    scenario("an agent with a dangling consent id falls back to itself (fails closed)", AgentDelegationTag) {
      val agent = createUser(createdByConsentId = Some(generateUUID()))
      CallContext(user = Full(agent)).accountableUserId shouldBe agent.userId
    }

    scenario("a populated consenter box wins over the DB chain", AgentDelegationTag) {
      val chainHuman = createUser()
      val consent = MappedConsent.create.mUserId(chainHuman.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val consenterHuman = createUser()
      CallContext(user = Full(agent), consenter = Full(consenterHuman))
        .accountableUserId shouldBe consenterHuman.userId
    }

    scenario("onBehalfOfUser wins over consenter", AgentDelegationTag) {
      val agent = createUser()
      val consenterHuman = createUser()
      val explicitHuman = createUser()
      CallContext(
        user = Full(agent),
        consenter = Full(consenterHuman),
        onBehalfOfUser = Full(explicitHuman)
      ).accountableUserId shouldBe explicitHuman.userId
    }
  }
}
