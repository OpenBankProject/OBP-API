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

package code.api.util

import java.util.Date

import code.accountholders.AccountHolders
import code.api.util.APIUtil.generateUUID
import code.api.util.{Consent, ConsentLinkedCustomers, ConsentMyResources}
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_1_0.TransactionRequestBodySandBoxTanJSON
import code.bankconnectors.LocalMappedConnector
import code.metadata.counterparties.{MappedCounterparty, MapperCounterparties}
import code.consent.MappedConsent
import code.model.dataAccess.ResourceUser
import code.setup.ServerSetup
import code.model.dataAccess.MappedBank
import code.transactionrequests.{MappedTransactionRequest, MappedTransactionRequestProvider}
import code.users.{AttributionPolicy, UserReference, Users}
import com.openbankproject.commons.model.{AccountId, AmountOfMoney, AmountOfMoneyJsonV121, BankAccount, BankAccountCommons, BankId, BankIdAccountId, TransactionRequestCharge, TransactionRequestId, TransactionRequestType}
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper.By
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

  private def storedField(value: String): String = Option(value).getOrElse("")

  /** A BankAccount value the transaction-request provider can read without a database row behind
    * it: of an account it only reads the ids, routings, name and attributes. */
  private def testAccount(): BankAccount = BankAccountCommons(
    accountId = AccountId(generateUUID()),
    accountType = "CURRENT",
    balance = BigDecimal("0"),
    currency = "EUR",
    name = "agent delegation test account",
    label = "agent delegation test account",
    number = "1",
    bankId = BankId("agent-delegation-bank"),
    lastUpdate = new Date(),
    branchId = "",
    accountRoutings = Nil,
    accountRules = Nil,
    accountHolder = ""
  )

  /** Writes one transaction request through the provider as `user` and returns the stored row.
    * The Box result is ignored on purpose: the row is saved before it is converted back, and it
    * is the two stored columns these scenarios are about. */
  private def storedTransactionRequestFor(user: ResourceUser): MappedTransactionRequest = {
    val transactionRequestId = TransactionRequestId(generateUUID())
    val toAccount = testAccount()
    MappedTransactionRequestProvider.createTransactionRequestImpl210(
      transactionRequestId = transactionRequestId,
      transactionRequestType = TransactionRequestType("SANDBOX_TAN"),
      fromAccount = testAccount(),
      toAccount = toAccount,
      transactionRequestCommonBody = TransactionRequestBodySandBoxTanJSON(
        to = TransactionRequestAccountJsonV140(toAccount.bankId.value, toAccount.accountId.value),
        value = AmountOfMoneyJsonV121("EUR", "10.00"),
        description = "agent delegation test"),
      details = "{}",
      status = "INITIATED",
      charge = TransactionRequestCharge("agent delegation test charge", AmountOfMoney("EUR", "0.00")),
      chargePolicy = "SHARED",
      paymentService = None,
      berlinGroupPayments = None,
      apiStandard = None,
      apiVersion = None,
      callContext = Some(CallContext(user = Full(user)))
    )
    MappedTransactionRequest
      .find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
      .openOrThrowException("expected the transaction request row to have been written")
  }

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

  feature("CallContext.onBehalfOfUserId resolves the caller to the human the request is about") {

    scenario("a plain human resolves to themselves", AgentDelegationTag) {
      val human = createUser()
      CallContext(user = Full(human)).onBehalfOfUserId shouldBe human.userId
    }

    scenario("a consent-minted agent resolves to the granting human", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      CallContext(user = Full(agent)).onBehalfOfUserId shouldBe human.userId
    }

    scenario("an agent with a dangling consent id falls back to itself (fails closed)", AgentDelegationTag) {
      val agent = createUser(createdByConsentId = Some(generateUUID()))
      CallContext(user = Full(agent)).onBehalfOfUserId shouldBe agent.userId
    }

    scenario("a populated consenter box wins over the DB chain", AgentDelegationTag) {
      val chainHuman = createUser()
      val consent = MappedConsent.create.mUserId(chainHuman.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val consenterHuman = createUser()
      CallContext(user = Full(agent), consenter = Full(consenterHuman))
        .onBehalfOfUserId shouldBe consenterHuman.userId
    }

    scenario("consentCreator wins over consenter", AgentDelegationTag) {
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

  feature("Users.onBehalfOfUserIdOf — the resolver") {

    scenario("an original user resolves to itself", AgentDelegationTag) {
      val human = createUser()
      Users.users.vend.onBehalfOfUserIdOf(human.userId) shouldBe Full(human.userId)
      Users.users.vend.actsForSelf(human.userId) shouldBe true
    }

    scenario("a consent user resolves to the consent's user", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      Users.users.vend.onBehalfOfUserIdOf(agent.userId) shouldBe Full(human.userId)
      Users.users.vend.actsForSelf(agent.userId) shouldBe false
    }

    scenario("a dangling consent id keeps the caller (fails closed)", AgentDelegationTag) {
      val agent = createUser(createdByConsentId = Some(generateUUID()))
      Users.users.vend.onBehalfOfUserIdOf(agent.userId) shouldBe Full(agent.userId)
    }

    scenario("an unknown user id keeps itself (fails closed)", AgentDelegationTag) {
      val id = generateUUID()
      Users.users.vend.onBehalfOfUserIdOf(id) shouldBe Full(id)
    }

    scenario("BG-style: consent with no human yet keeps the caller, and is NOT pinned in the cache", AgentDelegationTag) {
      val consent = MappedConsent.create.saveMe()   // mUserId empty until authorisation
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      Users.users.vend.onBehalfOfUserIdOf(agent.userId) shouldBe Full(agent.userId)
      val human = createUser()
      consent.mUserId(human.userId).saveMe()          // authorisation binds the human
      Users.users.vend.onBehalfOfUserIdOf(agent.userId) shouldBe Full(human.userId)
    }

    scenario("invariant: a consent whose user is itself a consent user is refused, not resolved", AgentDelegationTag) {
      val human = createUser()
      val consent1 = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent1 = createUser(createdByConsentId = Some(consent1.consentId))
      val consent2 = MappedConsent.create.mUserId(agent1.userId).saveMe()   // names a consent user: data bug
      val agent2 = createUser(createdByConsentId = Some(consent2.consentId))
      Users.users.vend.onBehalfOfUserIdOf(agent2.userId) shouldBe a[Failure]
      // and CallContext falls back to the caller rather than throwing
      CallContext(user = Full(agent2)).onBehalfOfUserId shouldBe agent2.userId
    }
  }

  feature("Users.attributionOf — the policy-aware entry point") {

    scenario("UseAuthenticatedUserId stores the caller and does not consult the resolver", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val a = Users.users.vend.attributionOf(agent.userId, UserReference.EntitlementUserIdConsentScope).openOrThrowException("expected Full")
      a.userIdToStore shouldBe agent.userId
      a.onBehalfOfUserId shouldBe agent.userId
      a.isDelegated shouldBe false
      a.consentId shouldBe None
    }

    scenario("UseOnBehalfOfUserId stores the on-behalf-of user and reports the consent", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val a = Users.users.vend.attributionOf(agent.userId, UserReference.EntitlementUserId).openOrThrowException("expected Full")
      a.userId shouldBe agent.userId
      a.onBehalfOfUserId shouldBe human.userId
      a.userIdToStore shouldBe human.userId
      a.isDelegated shouldBe true
      a.consentId shouldBe Some(consent.consentId)
      Users.users.vend.attributedUserId(agent.userId, UserReference.EntitlementUserId) shouldBe Full(human.userId)
    }

    scenario("UseOnBehalfOfUserId for an original user is a no-op with no consent", AgentDelegationTag) {
      val human = createUser()
      val a = Users.users.vend.attributionOf(human.userId, UserReference.AccountHoldersUser).openOrThrowException("expected Full")
      a.userIdToStore shouldBe human.userId
      a.isDelegated shouldBe false
      a.consentId shouldBe None
    }

    scenario("Reject is Full for an original user and Failure for a consent user", AgentDelegationTag) {
      val human = createUser()
      Users.users.vend.attributionOf(human.userId, UserReference.ConsentUserId).map(_.userIdToStore) shouldBe Full(human.userId)
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val rejected = Users.users.vend.attributionOf(agent.userId, UserReference.ConsentUserId)
      rejected shouldBe a[Failure]
      rejected.asInstanceOf[Failure].msg should include(ErrorMessages.InvalidUserId)
    }

    scenario("the policy file is complete: every reference has a policy, a class and at least one field", AgentDelegationTag) {
      UserReference.all should not be empty
      UserReference.all.map(_.name).distinct.size shouldBe UserReference.all.size
      UserReference.all.foreach { r =>
        r.fields should not be empty
        Class.forName(r.mapperClass) // resolves, or the reference names a class that does not exist
      }
      UserReference.byPolicy(AttributionPolicy.Reject).map(_.name) should contain allOf ("ConsentUserId", "ConsumerCreatedByUserId")
    }
  }

  feature("addEntitlement goes through the attribution policy") {

    scenario("a grant targeting a consent user lands on its on-behalf-of user", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val role = "CanGetConfig"
      val e = code.entitlement.Entitlement.entitlement.vend.addEntitlement("", agent.userId, role).openOrThrowException("expected the grant")
      e.userId shouldBe human.userId
    }

    scenario("the consent engine's own scope copy stays on the consent user", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val role = "CanGetConfig"
      val e = code.entitlement.Entitlement.entitlement.vend.addEntitlement("", agent.userId, role, createdByProcess = code.api.Constant.consent_user).openOrThrowException("expected the grant")
      e.userId shouldBe agent.userId
    }
  }

  feature("getOrCreateAccountHolder goes through the attribution policy (AccountHoldersUser)") {

    scenario("an account created by a consent user is held by its on-behalf-of user", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val account = BankIdAccountId(BankId("agent-delegation-bank"), AccountId(generateUUID()))
      val holder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(agent, account).openOrThrowException("expected the holder row")
      holder.user.get shouldBe human.userPrimaryKey.value
      AccountHolders.accountHolders.vend.getAccountHolders(account.bankId, account.accountId).map(_.userId) shouldBe Set(human.userId)
      AccountHolders.accountHolders.vend.getAccountsHeldByUser(agent) should not contain account
      AccountHolders.accountHolders.vend.getAccountsHeldByUser(human) should contain(account)
    }

    scenario("an account created by an original user is held by that user", AgentDelegationTag) {
      val human = createUser()
      val account = BankIdAccountId(BankId("agent-delegation-bank"), AccountId(generateUUID()))
      val holder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(human, account).openOrThrowException("expected the holder row")
      holder.user.get shouldBe human.userPrimaryKey.value
      AccountHolders.accountHolders.vend.getAccountHolders(account.bankId, account.accountId).map(_.userId) shouldBe Set(human.userId)
    }

    scenario("a consent user whose consent has no human yet keeps the row on itself (fails closed)", AgentDelegationTag) {
      val consent = MappedConsent.create.mUserId("").saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val account = BankIdAccountId(BankId("agent-delegation-bank"), AccountId(generateUUID()))
      val holder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(agent, account).openOrThrowException("expected the holder row")
      holder.user.get shouldBe agent.userPrimaryKey.value
    }
  }

  feature("DynamicData rows go through the attribution policy (DynamicDataUserId), reads and writes alike") {

    val entityName = "agent_delegation_note"
    def noteJson(id: String): JObject = (s"${entityName}_id" -> id) ~ ("name" -> "written by an agent")
    def dynamicData = code.DynamicData.DynamicDataProvider.connectorMethodProvider.vend

    scenario("a personal row written by a consent user belongs to its on-behalf-of user and is read back for both", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
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

    scenario("a personal row written by an original user stays on that user", AgentDelegationTag) {
      val human = createUser()
      val id = generateUUID()
      val saved = dynamicData.save(None, entityName, noteJson(id), Some(human.userId), isPersonalEntity = true).openOrThrowException("expected the row")
      saved.userId shouldBe Some(human.userId)
      dynamicData.delete(None, entityName, id, Some(human.userId), isPersonalEntity = true) shouldBe Full(true)
    }

    scenario("a dynamic entity definition created by a consent user is owned by its on-behalf-of user (DynamicEntityUserId)", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
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

  feature("User-Customer links go through the attribution policy (UserCustomerLinkUserId)") {

    def links = code.usercustomerlinks.UserCustomerLink.userCustomerLink.vend

    scenario("a link created by a consent user belongs to its on-behalf-of user", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val customerId = generateUUID()
      val link = links.createUserCustomerLink(agent.userId, customerId, new java.util.Date(), true)
        .openOrThrowException("expected the link row")
      link.userId shouldBe human.userId
      links.getUserCustomerLinksByUserId(human.userId).map(_.customerId) should contain(customerId)
      links.getUserCustomerLinksByUserId(agent.userId).map(_.customerId) should not contain customerId
    }

    scenario("a link created by an original user stays on that user", AgentDelegationTag) {
      val human = createUser()
      val customerId = generateUUID()
      val link = links.createUserCustomerLink(human.userId, customerId, new java.util.Date(), true)
        .openOrThrowException("expected the link row")
      link.userId shouldBe human.userId
    }

    scenario("a consent user whose consent has no human yet keeps the row on itself (fails closed)", AgentDelegationTag) {
      val consent = MappedConsent.create.mUserId("").saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val customerId = generateUUID()
      val link = links.createUserCustomerLink(agent.userId, customerId, new java.util.Date(), true)
        .openOrThrowException("expected the link row")
      link.userId shouldBe agent.userId
    }

    // The two-argument lookup is every caller's "already linked?" pre-check, and
    // MappedUserCustomerLink has UniqueIndex(mUserId, mCustomerId). If the create resolved but
    // the lookup did not, the check would pass on the agent and then break the index on the human.
    scenario("the pre-check lookup asks about the row the create would write", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val customerId = generateUUID()
      links.createUserCustomerLink(human.userId, customerId, new java.util.Date(), true)
        .openOrThrowException("expected the human's link row")

      links.getUserCustomerLink(agent.userId, customerId).map(_.userId) shouldBe Full(human.userId)
      val again = links.getOCreateUserCustomerLink(agent.userId, customerId, new java.util.Date(), true)
        .openOrThrowException("expected the existing row, not a second one")
      again.userId shouldBe human.userId
      links.getUserCustomerLinksByUserId(human.userId).count(_.customerId == customerId) shouldBe 1
    }

    // Deliberate asymmetry: this method also serves the admin lookup at
    // GET /banks/BANK_ID/user_customer_links/users/USER_ID, where the id is an explicit target.
    scenario("listing by user id is not redirected, so an explicit target still answers for itself", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val customerId = generateUUID()
      links.createUserCustomerLink(agent.userId, customerId, new java.util.Date(), true)
        .openOrThrowException("expected the link row")
      links.getUserCustomerLinksByUserId(agent.userId) shouldBe empty
    }
  }

  feature("my_resources.linked_customers — the Consent grant a consent user needs to read its human's Customers") {

    import code.api.v6_0_0.{PostConsentLinkedCustomersJson, PostConsentMyResourcesJson}

    def validate(body: PostConsentMyResourcesJson): Box[Unit] =
      scala.concurrent.Await.result(Consent.validateMyResources(Some(body), None), scala.concurrent.duration.Duration(10, "seconds"))

    // booleanToFuture reports a bad body by throwing (fullBoxOrException), so a rejection is an
    // exception carrying the message the caller will see -- assert on that, not on a Failure box.
    def rejectionMessage(body: PostConsentMyResourcesJson): String =
      intercept[Exception](validate(body)).getMessage

    scenario("a grant covers exactly the bank it names, and nothing else", AgentDelegationTag) {
      val claim = ConsentMyResources(Nil, List(ConsentLinkedCustomers("bank-a", List(ConsentMyResources.actionRead))))
      claim.coversLinkedCustomers("bank-a", ConsentMyResources.actionRead) shouldBe true
      claim.coversLinkedCustomers("bank-b", ConsentMyResources.actionRead) shouldBe false
      claim.coversLinkedCustomers("bank-a", ConsentMyResources.actionWrite) shouldBe false
    }

    scenario("a consent with no linked_customers covers nothing", AgentDelegationTag) {
      ConsentMyResources(Nil).coversLinkedCustomers("bank-a", ConsentMyResources.actionRead) shouldBe false
      ConsentMyResources(Nil).linkedCustomerBankIds(ConsentMyResources.actionRead) shouldBe Nil
    }

    scenario("the unscoped read sees only the banks granted for that action", AgentDelegationTag) {
      val claim = ConsentMyResources(Nil, List(
        ConsentLinkedCustomers("bank-a", List(ConsentMyResources.actionRead)),
        ConsentLinkedCustomers("bank-b", List(ConsentMyResources.actionWrite))))
      claim.linkedCustomerBankIds(ConsentMyResources.actionRead) shouldBe List("bank-a")
      claim.linkedCustomerBankIds(ConsentMyResources.actionWrite) shouldBe List("bank-b")
    }

    // The claim travels in the consent JWT, so a lossy round trip silently drops a grant.
    scenario("linked_customers survives the json to claim round trip", AgentDelegationTag) {
      val body = PostConsentMyResourcesJson(None, Some(List(
        PostConsentLinkedCustomersJson("bank-a", List(ConsentMyResources.actionRead)))))
      val claim = ConsentMyResources.fromJson(body)
      claim.linked_customers shouldBe List(ConsentLinkedCustomers("bank-a", List(ConsentMyResources.actionRead)))
      ConsentMyResources.toJson(claim).linked_customers shouldBe body.linked_customers
    }

    scenario("a well formed linked_customers entry validates", AgentDelegationTag) {
      validate(PostConsentMyResourcesJson(None, Some(List(
        PostConsentLinkedCustomersJson("bank-a", List(ConsentMyResources.actionRead)))))).isDefined shouldBe true
    }

    scenario("bank_id is required, because a Customer belongs to a Bank", AgentDelegationTag) {
      val message = rejectionMessage(PostConsentMyResourcesJson(None, Some(List(
        PostConsentLinkedCustomersJson("", List(ConsentMyResources.actionRead))))))
      message should include(ErrorMessages.ConsentMyResourcesInvalid.trim)
      message should include("bank_id is required")
    }

    scenario("actions must be named and known", AgentDelegationTag) {
      rejectionMessage(PostConsentMyResourcesJson(None, Some(List(
        PostConsentLinkedCustomersJson("bank-a", Nil))))) should include("actions must name at least one of")
      rejectionMessage(PostConsentMyResourcesJson(None, Some(List(
        PostConsentLinkedCustomersJson("bank-a", List("delete")))))) should include("unknown actions delete")
    }
  }

  feature("Transaction requests record both ids (UserReference.TransactionRequestUserIdOnBehalfOfUserId)") {

    scenario("a request made by an original user names that user in both columns", AgentDelegationTag) {
      val human = createUser()
      val row = storedTransactionRequestFor(human)
      storedField(row.mUserId.get) shouldBe human.userId
      storedField(row.mOnBehalfOfUserId.get) shouldBe human.userId
    }

    scenario("a request made by a consent user names the caller and its on-behalf-of user", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val row = storedTransactionRequestFor(agent)
      storedField(row.mUserId.get) shouldBe agent.userId
      storedField(row.mOnBehalfOfUserId.get) shouldBe human.userId
    }

    // Regression: the attribution used to supply BOTH columns, so a resolver Failure -- which is
    // what a broken consent chain gets -- wrote null over mUserId too, and the payment row no
    // longer said who made it. mUserId is read from the call context, never from the attribution.
    scenario("a broken consent chain still names the caller, and leaves no on-behalf-of user", AgentDelegationTag) {
      val human = createUser()
      val consent1 = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent1 = createUser(createdByConsentId = Some(consent1.consentId))
      val consent2 = MappedConsent.create.mUserId(agent1.userId).saveMe()   // names a consent user: data bug
      val agent2 = createUser(createdByConsentId = Some(consent2.consentId))
      Users.users.vend.onBehalfOfUserIdOf(agent2.userId) shouldBe a[Failure]   // the precondition this pins
      val row = storedTransactionRequestFor(agent2)
      storedField(row.mUserId.get) shouldBe agent2.userId
      storedField(row.mOnBehalfOfUserId.get) shouldBe ""
    }

    scenario("a consent user whose consent has no human yet acts for itself (fails closed)", AgentDelegationTag) {
      val consent = MappedConsent.create.mUserId("").saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val row = storedTransactionRequestFor(agent)
      storedField(row.mUserId.get) shouldBe agent.userId
      storedField(row.mOnBehalfOfUserId.get) shouldBe agent.userId
    }
  }

  feature("Banks record the human who created them (UserReference.BankCreatedByUserId)") {

    /** Create a bank through the connector as `callerUserId`, and return the stored row. */
    def createBankAs(callContext: Option[CallContext]): MappedBank = {
      val bankId = s"agent-delegation-bank-${generateUUID().take(8)}"
      LocalMappedConnector.createOrUpdateBank(
        bankId = bankId, fullBankName = "Agent Delegation Test Bank", shortBankName = "ADTB",
        logoURL = "", websiteURL = "", swiftBIC = "", national_identifier = "",
        bankRoutingScheme = "", bankRoutingAddress = "", callContext = callContext
      ).openOrThrowException("expected the bank to be created")
      MappedBank.find(By(MappedBank.permalink, bankId))
        .openOrThrowException("expected the bank row to have been written")
    }

    scenario("a bank created by an original user is created by that user", AgentDelegationTag) {
      val human = createUser()
      storedField(createBankAs(Some(CallContext(user = Full(human)))).CreatedByUserId.get) shouldBe human.userId
    }

    // The defect this closes, seen in the wild 2026-09-03: a bank created through Opey under a
    // temporary consent had createdbyuserid = the consent user, so it would drop out of every
    // "banks created by me" read once that consent was revoked.
    scenario("a bank created by a consent user is created by its on-behalf-of user", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val row = createBankAs(Some(CallContext(user = Full(agent))))
      storedField(row.CreatedByUserId.get) shouldBe human.userId
      storedField(row.CreatedByUserId.get) should not be agent.userId
    }

    scenario("a consent user whose consent has no human yet creates for itself (fails closed)", AgentDelegationTag) {
      val consent = MappedConsent.create.mUserId("").saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      storedField(createBankAs(Some(CallContext(user = Full(agent)))).CreatedByUserId.get) shouldBe agent.userId
    }

    // Berlin Group / UK consents carry their consenter on the request rather than in the stored
    // chain, so the request layer has to win -- the same order CallContext.onBehalfOfUserId uses.
    scenario("the request layer's consenter takes precedence over the stored chain", AgentDelegationTag) {
      val consenter = createUser()
      val caller = createUser()
      val row = createBankAs(Some(CallContext(user = Full(caller), consenter = Full(consenter))))
      storedField(row.CreatedByUserId.get) shouldBe consenter.userId
    }

    scenario("no authenticated user leaves the creator empty", AgentDelegationTag) {
      storedField(createBankAs(None).CreatedByUserId.get) shouldBe ""
    }
  }

  feature("Counterparties record both ids (UserReference.CounterpartyCreatedByUserIdCreatedByOnBehalfOfUserId)") {

    /** Create a counterparty through the provider as `callerUserId`, and return the stored row. */
    def createCounterpartyAs(callerUserId: String): MappedCounterparty = {
      val name = s"agent-delegation-cp-${generateUUID().take(8)}"
      MapperCounterparties.createCounterparty(
        createdByUserId = callerUserId, thisBankId = "agent-delegation-bank",
        thisAccountId = generateUUID(), thisViewId = "owner", name = name,
        otherAccountRoutingScheme = "IBAN", otherAccountRoutingAddress = "DE89370400440532013000",
        otherBankRoutingScheme = "BIC", otherBankRoutingAddress = "COBADEFF",
        otherBranchRoutingScheme = "", otherBranchRoutingAddress = "", isBeneficiary = true,
        otherAccountSecondaryRoutingScheme = "", otherAccountSecondaryRoutingAddress = "",
        description = "agent delegation test", currency = "EUR", bespoke = Nil
      ).openOrThrowException("expected the counterparty to be created")
      MappedCounterparty.find(By(MappedCounterparty.mName, name))
        .openOrThrowException("expected the counterparty row to have been written")
    }

    // Record-both, not redirect: mCreatedByUserId is published as created_by_user_id on the
    // v2.2.0/v4.0.0 responses, so it must keep saying who actually made the call.
    scenario("a counterparty created by an original user names that user in both columns", AgentDelegationTag) {
      val human = createUser()
      val row = createCounterpartyAs(human.userId)
      storedField(row.mCreatedByUserId.get) shouldBe human.userId
      storedField(row.mCreatedByOnBehalfOfUserId.get) shouldBe human.userId
    }

    scenario("a counterparty created by a consent user names the agent and its on-behalf-of user", AgentDelegationTag) {
      val human = createUser()
      val consent = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val row = createCounterpartyAs(agent.userId)
      storedField(row.mCreatedByUserId.get) shouldBe agent.userId
      storedField(row.mCreatedByOnBehalfOfUserId.get) shouldBe human.userId
    }

    scenario("a consent user whose consent has no human yet acts for itself (fails closed)", AgentDelegationTag) {
      val consent = MappedConsent.create.mUserId("").saveMe()
      val agent = createUser(createdByConsentId = Some(consent.consentId))
      val row = createCounterpartyAs(agent.userId)
      storedField(row.mCreatedByUserId.get) shouldBe agent.userId
      storedField(row.mCreatedByOnBehalfOfUserId.get) shouldBe agent.userId
    }

    // A broken chain must not blank the audit column: who sent money where is the question this
    // table exists to answer, so the actor is kept even when the human cannot be resolved.
    scenario("a broken consent chain still names the actor in both columns", AgentDelegationTag) {
      val human = createUser()
      val consent1 = MappedConsent.create.mUserId(human.userId).saveMe()
      val agent1 = createUser(createdByConsentId = Some(consent1.consentId))
      val consent2 = MappedConsent.create.mUserId(agent1.userId).saveMe()
      val agent2 = createUser(createdByConsentId = Some(consent2.consentId))
      Users.users.vend.onBehalfOfUserIdOf(agent2.userId) shouldBe a[Failure]
      val row = createCounterpartyAs(agent2.userId)
      storedField(row.mCreatedByUserId.get) shouldBe agent2.userId
      storedField(row.mCreatedByOnBehalfOfUserId.get) shouldBe agent2.userId
    }
  }
}
