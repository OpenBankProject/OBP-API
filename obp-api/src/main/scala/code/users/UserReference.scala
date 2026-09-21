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

package code.users

/**
 * This trait answers a single question: when an API call is made by an agent acting for a person,
 * whose user id should be written to the database?
 *
 * A person can grant a Consent to a piece of software. That software then calls the API under an
 * identity of its own, with its own user id, created for the Consent and discarded when the Consent
 * is revoked. We call such an identity a consent user. If the software creates a bank account and
 * OBP records the consent user as the account holder, the account now belongs to an identity that
 * is about to disappear, and the person who asked for it can no longer see it.
 *
 * Every column that stores a user id therefore needs a decision, and this trait is the set of
 * decisions available. There are three of them:
 *
 *  - UseAuthenticatedUserId means store the id of whoever actually made the call, agent included,
 *    and resolve nothing. It suits audit trails, and the rows carrying a Consent's own permissions,
 *    which are meant to die with it.
 *  - UseOnBehalfOfUserId means store the id of the person the agent is acting for, which
 *    Users.resolveOnBehalfOfUserId looks up. It suits anything the person owns.
 *  - Reject means a consent user must not do this at all, and the call fails with a 400.
 *
 * The wider design, and the vocabulary it uses, is written up in ON_BEHALF_OF_USER_ID_PLAN.md.
 */
sealed trait AttributionPolicy
object AttributionPolicy {
  case object UseAuthenticatedUserId                           extends AttributionPolicy
  case object UseOnBehalfOfUserId                              extends AttributionPolicy
  case object Reject                                           extends AttributionPolicy
}

/**
 * This class holds the two user ids a database write can choose between, and says which of them to
 * use. Users.attributionOf builds one and returns it.
 *
 * The code that asks for it is whatever is about to store a row: in OBP that means a provider,
 * which is the class owning reads and writes for one table, such as MapperCounterparties or
 * MappedEntitlements. It passes in the id of the caller and the UserReference for the column it is
 * filling, and gets one of these back.
 *
 * The two ids are userId, the caller who authenticated, which is the agent whenever an agent made
 * the call; and onBehalfOfUserId, the person that call was made for. They are equal when nobody is
 * delegating, and equal again when the reference's policy is UseAuthenticatedUserId, because that
 * policy never looks the person up. consentId names the Consent that established the delegation,
 * where there was one.
 *
 * A provider filling a single column need not choose between them itself: userIdToStore applies the
 * policy and returns the one id to write. Only the few tables that keep both columns, a transaction
 * request and a counterparty, read userId and onBehalfOfUserId directly.
 */
case class Attribution(
  userId:           String,
  onBehalfOfUserId: String,
  consentId:        Option[String],
  ref:              UserReference
) {
  /** This is true when an agent made the call for somebody else, and false when the caller was
   *  acting only for themselves. */
  def isDelegated: Boolean = userId != onBehalfOfUserId

  /** This is the one id to write into the column or columns that `ref` names, chosen by applying
   *  that reference's policy. */
  def userIdToStore: String = ref.policy match {
    case AttributionPolicy.UseOnBehalfOfUserId => onBehalfOfUserId
    case _                                     => userId
  }
}

/**
 * This class is one entry in OBP's table of decisions. Each value below names a database column
 * that holds a user id and says which AttributionPolicy governs it, so the file as a whole is the
 * table; Users.attributionOf reads it at runtime to work out which id to write.
 *
 * A test keeps the table complete. UserReferenceAttributionPolicyTest reflects over every Mapper in
 * ToSchemify.models, picks out each field whose name looks like it holds a user id (it matches
 * userid, createdby, grantedby and holder), and requires every one of them to be either named by a
 * value in this file, or listed in notUserIdColumns with the reason it is not a user id after all.
 *
 * So adding a Mapper with a column such as UserId or CreatedByUserId will fail the build, and it
 * will keep failing until you do one of those two things. That is intended rather than an
 * obstacle. A column nobody has decided about silently stores the agent's id whenever a Consent is
 * involved, and the first sign of trouble is a person reporting that something they created through
 * an agent has vanished. Failing on the day the table is added puts the question in front of the
 * developer who knows what the column is for. It has caught two columns so far,
 * ApiProductSubscription.CreatedByUserId and DynamicGlossaryItem.CreatedByUserId, both added after
 * this file was first written.
 *
 * Adding an entry does not commit you to redirecting anything. Choosing UseAuthenticatedUserId, or
 * excluding the column, satisfies the test just as well as choosing UseOnBehalfOfUserId does, since
 * it only insists that somebody answered the question. Whether a provider then really applies the
 * policy it declared is a separate guard, OnBehalfOfOwnershipSweepTest.
 *
 * The constructor takes the policy; mapperClass, the fully-qualified Mapper class written as a
 * string so that this file imports nothing and cannot trigger Mapper initialisation; fields, the
 * names of the field objects on that Mapper; and note, the reason this policy was chosen, which
 * every value carries.
 *
 * A value is named after the column it governs, as Table_Column, dropping the Mapped or Mapper
 * prefix from the class and Lift's m prefix from the field. Where one reference governs two
 * columns it is named after the first, and fields lists both.
 */
sealed abstract class UserReference(
  val policy: AttributionPolicy,
  val mapperClass: String,
  val fields: List[String],
  val note: String = ""
) {
  /** This is the value's own name, as written below, e.g. "Bank_CreatedByUserId". The tests and
   *  the log lines identify a reference by it. */
  def name: String = getClass.getSimpleName.stripSuffix("$")
}

object UserReference {
  import AttributionPolicy._

  // The values are grouped by policy, because the policy is what governs behaviour. Within a
  // group the order is not significant.

  // ---- UseAuthenticatedUserId: the agent's own id is the truthful thing to store here, either
  // ---- because the row is an audit record of who acted, or because it carries a Consent's own
  // ---- permissions and is meant to be revoked along with that Consent.
  case object AccountAccess_UserFk                             extends UserReference(UseAuthenticatedUserId, "code.views.system.AccountAccess", List("user_fk"), "views copied from the consent JWT each request; has lifecycle GC")
  case object Entitlement_UserId_ConsentScope                  extends UserReference(UseAuthenticatedUserId, "code.entitlement.MappedEntitlement", List("mUserId"), "only when createdByProcess == consent_user: the consent engine copying the consent's own scope")
  case object Entitlement_GrantedByUserId                      extends UserReference(UseAuthenticatedUserId, "code.entitlement.MappedEntitlement", List("mGrantedByUserId"), "audit: who granted")
  case object UserLocks_UserId                                 extends UserReference(UseAuthenticatedUserId, "code.userlocks.UserLocks", List("UserId"), "lock the authenticated user")
  case object ExpectedChallengeAnswer_ExpectedUserId           extends UserReference(UseAuthenticatedUserId, "code.transactionChallenge.MappedExpectedChallengeAnswer", List("ExpectedUserId"), "consent and signing-basket authorisation: the caller IS the person authorising, so the challenge is theirs")
  case object ChatMessage_SenderUserId                         extends UserReference(UseAuthenticatedUserId, "code.chat.ChatMessage", List("SenderUserId"), "sender = the authenticated user is truthful")
  case object Metric_UserId                                    extends UserReference(UseAuthenticatedUserId, "code.metrics.MappedMetric", List("userId"), "record both: on-behalf-of via consent_reference_id at read time")
  case object MetricArchive_UserId                             extends UserReference(UseAuthenticatedUserId, "code.metrics.MetricArchive", List("userId"), "as Metric_UserId")
  case object ConnectorTrace_UserId                            extends UserReference(UseAuthenticatedUserId, "code.metrics.ConnectorTrace", List("userId"), "as Metric_UserId")
  case object DynamicDataAccess_GrantedBy                      extends UserReference(UseAuthenticatedUserId, "code.DynamicData.DynamicDataAccess", List("GrantedBy"), "audit: who granted")
  case object AuthUser_User                                    extends UserReference(UseAuthenticatedUserId, "code.model.dataAccess.AuthUser", List("user"), "login row -> its own ResourceUser; not attribution")
  case object OpenIDConnectToken_AuthUserPrimaryKey            extends UserReference(UseAuthenticatedUserId, "code.token.OpenIDConnectToken", List("AuthUserPrimaryKey"), "token belongs to the login; not attribution")
  case object UserRefreshes_UserId                             extends UserReference(UseAuthenticatedUserId, "code.UserRefreshes.MappedUserRefreshes", List("mUserId"), "operational: refresh of the authenticated user's own account list")

  // ---- UseOnBehalfOfUserId: the row belongs to the person, so it must outlive the Consent that
  // ---- created it. A handful of these tables keep both ids, and those name two fields.
  case object TransactionRequest_UserId                        extends UserReference(UseOnBehalfOfUserId   , "code.transactionrequests.MappedTransactionRequest", List("mUserId", "mOnBehalfOfUserId"), "record both: mUserId = userId, mOnBehalfOfUserId = onBehalfOfUserId")
  case object ExpectedChallengeAnswer_ExpectedUserId_TransactionRequest extends UserReference(UseOnBehalfOfUserId, "code.transactionChallenge.MappedExpectedChallengeAnswer", List("ExpectedUserId"), "payment SCA: the challenge belongs to the human whose money moves, never to the agent that started the payment")
  case object Entitlement_UserId                               extends UserReference(UseOnBehalfOfUserId   , "code.entitlement.MappedEntitlement", List("mUserId"), "the role holder; the consent-engine case is Entitlement_UserId_ConsentScope")
  case object AccountHolders_User                              extends UserReference(UseOnBehalfOfUserId   , "code.accountholders.MapperAccountHolders", List("user"), "the human holds the account; one held by a per-consent identity strands when the consent dies")
  case object UserCustomerLink_UserId                          extends UserReference(UseOnBehalfOfUserId   , "code.usercustomerlinks.MappedUserCustomerLink", List("mUserId"), "a Customer is linked to a human; a link on an agent identity dies with its Consent")
  case object AccountApplication_UserId                        extends UserReference(UseOnBehalfOfUserId   , "code.accountapplication.MappedAccountApplication", List("mUserId"), "explicit target: user_id comes from the request and is guarded at the endpoint, so the provider redirect is unreachable -- see ON_BEHALF_OF_USER_ID_PLAN.md row 14")
  case object AccountAccessRequest_RequestorUserId             extends UserReference(UseOnBehalfOfUserId   , "code.accountaccessrequest.AccountAccessRequest", List("RequestorUserId"), "who asked for access; the request outlives the session it was made in")
  case object AccountAccessRequest_TargetUserId                extends UserReference(UseOnBehalfOfUserId   , "code.accountaccessrequest.AccountAccessRequest", List("TargetUserId"), "explicit target: a consent user named here is rejected at the endpoint")
  case object AccountAccessRequest_CheckerUserId               extends UserReference(UseOnBehalfOfUserId   , "code.accountaccessrequest.AccountAccessRequest", List("CheckerUserId"), "who approved; maker/checker evidence has to name a human")
  case object DynamicChangeRequest_RequestorUserId             extends UserReference(UseOnBehalfOfUserId   , "code.dynamicchangerequest.DynamicChangeRequest", List("RequestorUserId"), "maker of a dynamic-code change")
  case object DynamicChangeRequest_CheckerUserId               extends UserReference(UseOnBehalfOfUserId   , "code.dynamicchangerequest.DynamicChangeRequest", List("CheckerUserId"), "checker; must differ from the requestor")
  case object EntitlementRequest_UserId                        extends UserReference(UseOnBehalfOfUserId   , "code.entitlementrequest.MappedEntitlementRequest", List("mUserId"), "who asked for the role; the grant that follows lands on a human")
  case object UserScope_UserId                                 extends UserReference(UseOnBehalfOfUserId   , "code.scope.MappedUserScope", List("mUserId"), "the scope holder")
  case object ApiCollection_UserId                             extends UserReference(UseOnBehalfOfUserId   , "code.apicollection.ApiCollection", List("UserId"), "the user's own saved collection, created through POST /my/api-collections")
  case object UserAttribute_UserId                             extends UserReference(UseOnBehalfOfUserId   , "code.users.UserAttribute", List("UserId"), "the user's own attribute, created through the /my/ endpoints")
  case object UserAgreement_UserId                             extends UserReference(UseOnBehalfOfUserId   , "code.users.UserAgreement", List("UserId"), "the user's own acceptance of terms")
  case object UserInitAction_UserId                            extends UserReference(UseOnBehalfOfUserId   , "code.users.UserInitAction", List("UserId"), "the user's own onboarding action")
  case object UserAuthContext_UserId                           extends UserReference(UseOnBehalfOfUserId   , "code.context.MappedUserAuthContext", List("mUserId"), "consent copies the on-behalf-of user's contexts into ConsentAuthContext separately")
  case object UserAuthContextUpdate_UserId                     extends UserReference(UseOnBehalfOfUserId   , "code.context.MappedUserAuthContextUpdate", List("mUserId"), "as UserAuthContext_UserId")
  case object DynamicEntity_UserId                             extends UserReference(UseOnBehalfOfUserId   , "code.dynamicEntity.DynamicEntity", List("UserId"), "the definition's creator; a definition outlives the Consent that created it")
  case object DynamicData_UserId                               extends UserReference(UseOnBehalfOfUserId   , "code.DynamicData.DynamicData", List("UserId"), "personal rows, and the one reference where the redirect MUST be symmetric: MapppedDynamicDataProvider resolves on save/update/get/delete alike, because a row keyed by this column on both sides is otherwise written by an agent and then invisible to it")
  case object DynamicDataAccess_UserId                         extends UserReference(UseOnBehalfOfUserId   , "code.DynamicData.DynamicDataAccess", List("UserId"), "row-level ACL. Deliberately still on the consent user today, because the bootstrap grant and the allows check have to agree with each other -- rows strand, nothing leaks. A later Phase 2 row; see the plan")
  case object DynamicEndpoint_UserId                           extends UserReference(UseOnBehalfOfUserId   , "code.DynamicEndpoint.DynamicEndpoint", List("UserId"), "the dynamic endpoint's creator; outlives the Consent")
  case object DynamicResourceDoc_CreatedByUserId               extends UserReference(UseOnBehalfOfUserId   , "code.dynamicResourceDoc.DynamicResourceDoc", List("CreatedByUserId", "UpdatedByUserId"), "and UpdatedByUserId; a dynamic artefact outlives the Consent that created it")
  case object DynamicMessageDoc_CreatedByUserId                extends UserReference(UseOnBehalfOfUserId   , "code.dynamicMessageDoc.DynamicMessageDoc", List("CreatedByUserId", "UpdatedByUserId"), "and UpdatedByUserId; a dynamic artefact outlives the Consent that created it")
  case object ConnectorMethod_CreatedByUserId                  extends UserReference(UseOnBehalfOfUserId   , "code.connectormethod.ConnectorMethod", List("CreatedByUserId", "UpdatedByUserId"), "and UpdatedByUserId; a dynamic artefact outlives the Consent that created it")
  case object AbacRule_CreatedByUserId                         extends UserReference(UseOnBehalfOfUserId   , "code.abacrule.AbacRule", List("CreatedByUserId", "UpdatedByUserId"), "and UpdatedByUserId; an access rule outlives the Consent that created it")
  case object Counterparty_CreatedByUserId                     extends UserReference(UseOnBehalfOfUserId   , "code.metadata.counterparties.MappedCounterparty", List("mCreatedByUserId", "mCreatedByOnBehalfOfUserId"), "record both: a counterparty controls where money may be sent, so the actor stays on mCreatedByUserId (and is published as created_by_user_id) while the human goes to mCreatedByOnBehalfOfUserId")
  case object CounterpartyWhereTag_User                        extends UserReference(UseOnBehalfOfUserId   , "code.metadata.counterparties.MappedCounterpartyWhereTag", List("user"), "who tagged the counterparty's location")
  case object ApiProductSubscription_CreatedByUserId           extends UserReference(UseOnBehalfOfUserId   , "code.apiproductsubscription.ApiProductSubscription", List("CreatedByUserId"), "a subscription outlives the Consent that took it out")
  case object DynamicGlossaryItem_CreatedByUserId              extends UserReference(UseOnBehalfOfUserId   , "code.glossaryitem.DynamicGlossaryItem", List("CreatedByUserId"), "outlives the Consent that created it")
  case object Bank_CreatedByUserId                             extends UserReference(UseOnBehalfOfUserId   , "code.model.dataAccess.MappedBank", List("CreatedByUserId"), "creator grant already resolved at the endpoint")
  case object Organisation_CreatedByUserId                     extends UserReference(UseOnBehalfOfUserId   , "code.organisation.Organisation", List("CreatedByUserId"), "outlives the Consent that created it")
  case object PayeeLookup_CreatedByUserId                      extends UserReference(UseOnBehalfOfUserId   , "code.payeelookup.PayeeLookup", List("CreatedByUserId"), "outlives the Consent that created it")
  case object RoutingScheme_CreatedByUserId                    extends UserReference(UseOnBehalfOfUserId   , "code.routingscheme.RoutingScheme", List("CreatedByUserId"), "outlives the Consent that created it")
  case object UtilityPaymentCallback_CreatedByUserId           extends UserReference(UseOnBehalfOfUserId   , "code.utilitypayment.UtilityPaymentCallback", List("CreatedByUserId"), "outlives the Consent that created it")
  case object StandingOrder_UserId                             extends UserReference(UseOnBehalfOfUserId   , "code.standingorders.StandingOrder", List("UserId"), "the payer; a standing order keeps executing long after any Consent expires")
  case object DirectDebit_UserId                               extends UserReference(UseOnBehalfOfUserId   , "code.directdebit.DirectDebit", List("UserId"), "the payer; a mandate keeps executing long after any Consent expires")
  case object Mandate_CreatedByUserId                          extends UserReference(UseOnBehalfOfUserId   , "code.mandate.Mandate", List("CreatedByUserId", "UpdatedByUserId"), "and UpdatedByUserId; a mandate authorises payment and outlives the Consent that created it")
  case object SignatoryPanel_UserIds                           extends UserReference(UseOnBehalfOfUserId   , "code.mandate.SignatoryPanel", List("UserIds"), "list of user ids")
  case object AccountWebhook_CreatedByUserId                   extends UserReference(UseOnBehalfOfUserId   , "code.webhook.MappedAccountWebhook", List("mCreatedByUserId"), "published as created_by_user_id on v3.1.0 and v4.0.0; nothing reads it as an ownership key, so the human is not locked out -- but a webhook outlives the Consent that made it and keeps sending account events, so the human behind it should be recorded. Record-both, like Counterparty. See todo/webhook_attribution.md")
  case object SystemAccountNotificationWebhook_CreatedByUserId extends UserReference(UseOnBehalfOfUserId   , "code.webhook.SystemAccountNotificationWebhook", List("CreatedByUserId"), "as AccountWebhook_CreatedByUserId")
  case object BankAccountNotificationWebhook_CreatedByUserId   extends UserReference(UseOnBehalfOfUserId   , "code.webhook.BankAccountNotificationWebhook", List("CreatedByUserId"), "as AccountWebhook_CreatedByUserId")
  case object ChatRoom_CreatedByUserId                         extends UserReference(UseOnBehalfOfUserId   , "code.chat.ChatRoom", List("CreatedByUserId"), "Portal chat: a human's room")
  case object Participant_UserId                               extends UserReference(UseOnBehalfOfUserId   , "code.chat.Participant", List("UserId"), "the person in the room")
  case object Reaction_UserId                                  extends UserReference(UseOnBehalfOfUserId   , "code.chat.Reaction", List("UserId"), "the person who reacted")
  case object ChatEmailDigestState_UserId                      extends UserReference(UseOnBehalfOfUserId   , "code.chat.ChatEmailDigestState", List("UserId"), "the person the digest is for")
  case object ChatMessage_MentionedUserIds                     extends UserReference(UseOnBehalfOfUserId   , "code.chat.ChatMessage", List("MentionedUserIds"), "explicit targets, humans by construction")
  case object CrmEvent_UserId                                  extends UserReference(UseOnBehalfOfUserId   , "code.crm.MappedCrmEvent", List("mUserId"), "the user the event concerns")
  case object KycCheck_User                                    extends UserReference(UseOnBehalfOfUserId   , "code.kycchecks.MappedKycCheck", List("user"), "the customer's user")
  case object KycCheck_StaffUserId                             extends UserReference(UseOnBehalfOfUserId   , "code.kycchecks.MappedKycCheck", List("mStaffUserId"), "staff = human operator")
  case object KycDocument_User                                 extends UserReference(UseOnBehalfOfUserId   , "code.kycdocuments.MappedKycDocument", List("user"), "the customer's user")
  case object KycStatus_User                                   extends UserReference(UseOnBehalfOfUserId   , "code.kycstatuses.MappedKycStatus", List("user"), "the customer's user")
  case object SocialMedia_User                                 extends UserReference(UseOnBehalfOfUserId   , "code.socialmedia.MappedSocialMedia", List("user"), "the customer's user")
  case object CustomerMessage_User                             extends UserReference(UseOnBehalfOfUserId   , "code.customer.MappedCustomerMessage", List("user"), "the customer's user")
  case object Meeting_CustomerUserId                           extends UserReference(UseOnBehalfOfUserId   , "code.meetings.MappedMeeting", List("mCustomerUserId"), "the customer side of the meeting")
  case object Meeting_StaffUserId                              extends UserReference(UseOnBehalfOfUserId   , "code.meetings.MappedMeeting", List("mStaffUserId"), "staff = human operator")
  case object Tag_User                                         extends UserReference(UseOnBehalfOfUserId   , "code.metadata.tags.MappedTag", List("user"), "the author of the annotation; it outlives the Consent")
  case object WhereTag_User                                    extends UserReference(UseOnBehalfOfUserId   , "code.metadata.wheretags.MappedWhereTag", List("user"), "the author of the annotation; it outlives the Consent")
  case object TransactionImage_User                            extends UserReference(UseOnBehalfOfUserId   , "code.metadata.transactionimages.MappedTransactionImage", List("user"), "the author of the annotation; it outlives the Consent")

  // ---- Reject: an agent must not do this at all, because what it would create outlives the
  // ---- Consent and would let the delegation extend itself.
  case object Consent_UserId                                   extends UserReference(Reject                , "code.consent.MappedConsent", List("mUserId"), "a consent user creating a consent = nested delegation")
  case object Consumer_CreatedByUserId                         extends UserReference(Reject                , "code.model.Consumer", List("createdByUserId"), "credentials outlive the consent")
  case object Token_UserForeignKey                             extends UserReference(Reject                , "code.model.Token", List("userForeignKey"), "credentials outlive the consent")

  /** This lists every reference declared above. The tests walk this list rather than reflecting
   *  over the file, so a value declared above but left out here is invisible to them; the two have
   *  to be kept in step by hand. */
  lazy val all: List[UserReference] = List(
    AccountAccess_UserFk,
    Entitlement_UserId_ConsentScope,
    Entitlement_GrantedByUserId,
    UserLocks_UserId,
    ExpectedChallengeAnswer_ExpectedUserId,
    ExpectedChallengeAnswer_ExpectedUserId_TransactionRequest,
    ChatMessage_SenderUserId,
    Metric_UserId,
    MetricArchive_UserId,
    ConnectorTrace_UserId,
    DynamicDataAccess_GrantedBy,
    AuthUser_User,
    OpenIDConnectToken_AuthUserPrimaryKey,
    UserRefreshes_UserId,
    TransactionRequest_UserId,
    Entitlement_UserId,
    AccountHolders_User,
    UserCustomerLink_UserId,
    AccountApplication_UserId,
    AccountAccessRequest_RequestorUserId,
    AccountAccessRequest_TargetUserId,
    AccountAccessRequest_CheckerUserId,
    DynamicChangeRequest_RequestorUserId,
    DynamicChangeRequest_CheckerUserId,
    EntitlementRequest_UserId,
    UserScope_UserId,
    ApiCollection_UserId,
    UserAttribute_UserId,
    UserAgreement_UserId,
    UserInitAction_UserId,
    UserAuthContext_UserId,
    UserAuthContextUpdate_UserId,
    DynamicEntity_UserId,
    DynamicData_UserId,
    DynamicDataAccess_UserId,
    DynamicEndpoint_UserId,
    DynamicResourceDoc_CreatedByUserId,
    DynamicMessageDoc_CreatedByUserId,
    ConnectorMethod_CreatedByUserId,
    AbacRule_CreatedByUserId,
    Counterparty_CreatedByUserId,
    CounterpartyWhereTag_User,
    ApiProductSubscription_CreatedByUserId,
    DynamicGlossaryItem_CreatedByUserId,
    Bank_CreatedByUserId,
    Organisation_CreatedByUserId,
    PayeeLookup_CreatedByUserId,
    RoutingScheme_CreatedByUserId,
    UtilityPaymentCallback_CreatedByUserId,
    StandingOrder_UserId,
    DirectDebit_UserId,
    Mandate_CreatedByUserId,
    SignatoryPanel_UserIds,
    AccountWebhook_CreatedByUserId,
    SystemAccountNotificationWebhook_CreatedByUserId,
    BankAccountNotificationWebhook_CreatedByUserId,
    ChatRoom_CreatedByUserId,
    Participant_UserId,
    Reaction_UserId,
    ChatEmailDigestState_UserId,
    ChatMessage_MentionedUserIds,
    CrmEvent_UserId,
    KycCheck_User,
    KycCheck_StaffUserId,
    KycDocument_User,
    KycStatus_User,
    SocialMedia_User,
    CustomerMessage_User,
    Meeting_CustomerUserId,
    Meeting_StaffUserId,
    Tag_User,
    WhereTag_User,
    TransactionImage_User,
    Consent_UserId,
    Consumer_CreatedByUserId,
    Token_UserForeignKey
  )

  /** This lists the columns whose names look like user ids to UserReferenceAttributionPolicyTest
   *  but which do not in fact hold one, so no policy applies. Each entry is the Mapper class, the
   *  field, and why it is excluded.
   *
   *  Only columns the test's pattern really catches belong here. It fails on an entry that matches
   *  nothing, because an exclusion covering no column reads as cover it is not giving. Four were
   *  removed for that reason: AccountAccessRequest.CheckerComment, DynamicChangeRequest.CheckerComment,
   *  MappedKycCheck.mStaffName and MappedMeeting.mStaffToken, all free text and none of them matched. */
  val notUserIdColumns: List[(String, String, String)] = List(
    ("code.model.dataAccess.MappedBankAccount", "holder", "free-text holder name"),
    ("code.transaction.MappedTransaction", "counterpartyAccountHolder", "free-text name"),
    ("code.entitlement.MappedEntitlement", "mCreatedByProcess", "process tag"),
    ("code.model.dataAccess.ResourceUser", "userId_", "the user's own id"),
    ("code.model.dataAccess.ResourceUser", "CreatedByConsentId", "consent id"),
    ("code.model.dataAccess.ResourceUser", "CreatedByUserInvitationId", "invitation id")
  )

  /** This returns every reference that carries the given policy. The sweep tests use it to ask,
   *  for instance, which columns are supposed to belong to the person rather than the agent. */
  def byPolicy(p: AttributionPolicy): List[UserReference] = all.filter(_.policy == p)
}
