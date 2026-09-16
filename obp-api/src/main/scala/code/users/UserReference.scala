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
 * Attribution policy: what a user-reference column stores when the caller is a consent user.
 * Design and vocabulary: OBP-API/ON_BEHALF_OF_USER_ID_PLAN.md ("The policy file").
 *
 *  - UseAuthenticatedUserId  the authenticated user's own id; no resolver
 *  - UseOnBehalfOfUserId     the on-behalf-of user's id, via Users.onBehalfOfUserIdOf
 *  - Reject                  a consent user must not do this at all: Failure -> 400
 */
sealed trait AttributionPolicy
object AttributionPolicy {
  case object UseAuthenticatedUserId extends AttributionPolicy
  case object UseOnBehalfOfUserId    extends AttributionPolicy
  case object Reject                 extends AttributionPolicy
}

/**
 * What a provider gets back from Users.attributionOf: everything it should store, plus the
 * facts the resolver logged. userId is the authenticated caller; onBehalfOfUserId is who owns
 * what the call creates (== userId for an original user acting alone; for a UseAuthenticatedUserId
 * reference the resolver is not consulted and it is simply userId).
 */
case class Attribution(
  userId:           String,
  onBehalfOfUserId: String,
  consentId:        Option[String],
  ref:              UserReference
) {
  def isDelegated: Boolean = userId != onBehalfOfUserId
  /** The single value for the column(s) `ref` names, per its policy. */
  def userIdToStore: String = ref.policy match {
    case AttributionPolicy.UseOnBehalfOfUserId => onBehalfOfUserId
    case _                                     => userId
  }
}

/**
 * One value per user-reference column (or per record-both table). This file IS the policy table:
 * Users.attributionOf reads it at runtime to decide which id to write.
 *
 * UserReferenceAttributionPolicyTest keeps it complete. It reflects over every Mapper in
 * ToSchemify.models, picks out each field whose name looks like a user reference (it matches
 * userid / createdby / grantedby / holder), and requires every one of them to be either:
 *
 *   1. named by a value in this file, or
 *   2. listed in notUserIdColumns, with the reason it is not a user id after all.
 *
 * So, concretely: if you add a Mapper with a column such as `UserId` or `CreatedByUserId`, that
 * test starts failing, and it keeps failing until you do one of those two things. That is the
 * intended behaviour, not an obstacle. A column nobody has decided about silently stores the
 * agent's id whenever a Consent is involved, and the first symptom is a user reporting that
 * something they created through an agent has disappeared. Failing the build on the day the
 * table is added puts the decision in front of the person who knows what the column means.
 * It has already caught two: ApiProductSubscription.CreatedByUserId and
 * DynamicGlossaryItem.CreatedByUserId, both added after this file was first written.
 *
 * Adding an entry here does NOT commit you to redirecting anything. Choosing
 * UseAuthenticatedUserId, or excluding the column, satisfies the test just as well as choosing
 * UseOnBehalfOfUserId — it only insists that somebody answered the question. Whether a provider
 * then actually applies the policy is a separate guard, OnBehalfOfOwnershipSweepTest.
 *
 * mapperClass is the fully-qualified Mapper class; fields are its field object names; note is
 * why this policy was chosen, and every value carries one.
 */
sealed abstract class UserReference(
  val policy: AttributionPolicy,
  val mapperClass: String,
  val fields: List[String],
  val note: String = ""
) {
  def name: String = getClass.getSimpleName.stripSuffix("$")
}

object UserReference {
  import AttributionPolicy._

  // ---- UseAuthenticatedUserId: authorisation materialisation and audit of the actor
  case object AccountAccessUserFk                                  extends UserReference(UseAuthenticatedUserId, "code.views.system.AccountAccess", List("user_fk"), "views copied from the consent JWT each request; has lifecycle GC")
  case object EntitlementUserIdConsentScope                        extends UserReference(UseAuthenticatedUserId, "code.entitlement.MappedEntitlement", List("mUserId"), "only when createdByProcess == consent_user: the consent engine copying the consent's own scope")
  case object EntitlementGrantedByUserId                           extends UserReference(UseAuthenticatedUserId, "code.entitlement.MappedEntitlement", List("mGrantedByUserId"), "audit: who granted")
  case object UserLocksUserId                                      extends UserReference(UseAuthenticatedUserId, "code.userlocks.UserLocks", List("UserId"), "lock the authenticated user")
  case object ExpectedChallengeAnswerExpectedUserId                extends UserReference(UseAuthenticatedUserId, "code.transactionChallenge.MappedExpectedChallengeAnswer", List("ExpectedUserId"), "the challenge is answered by the initiating user")
  case object ChatMessageSenderUserId                              extends UserReference(UseAuthenticatedUserId, "code.chat.ChatMessage", List("SenderUserId"), "sender = the authenticated user is truthful")
  case object MetricUserId                                         extends UserReference(UseAuthenticatedUserId, "code.metrics.MappedMetric", List("userId"), "record both: on-behalf-of via consent_reference_id at read time")
  case object MetricArchiveUserId                                  extends UserReference(UseAuthenticatedUserId, "code.metrics.MetricArchive", List("userId"), "as MetricUser")
  case object ConnectorTraceUserId                                 extends UserReference(UseAuthenticatedUserId, "code.metrics.ConnectorTrace", List("userId"), "as MetricUser")
  case object DynamicDataAccessGrantedBy                           extends UserReference(UseAuthenticatedUserId, "code.DynamicData.DynamicDataAccess", List("GrantedBy"), "audit: who granted")
  case object AuthUserUser                                         extends UserReference(UseAuthenticatedUserId, "code.model.dataAccess.AuthUser", List("user"), "login row -> its own ResourceUser; not attribution")
  case object OpenIDConnectTokenAuthUserPrimaryKey                 extends UserReference(UseAuthenticatedUserId, "code.token.OpenIDConnectToken", List("AuthUserPrimaryKey"), "token belongs to the login; not attribution")
  case object UserRefreshesUserId                                  extends UserReference(UseAuthenticatedUserId, "code.UserRefreshes.MappedUserRefreshes", List("mUserId"), "operational: refresh of the authenticated user's own account list")

  // ---- UseOnBehalfOfUserId: ownership / attribution (record-both tables list both columns)
  case object TransactionRequestUserIdOnBehalfOfUserId             extends UserReference(UseOnBehalfOfUserId   , "code.transactionrequests.MappedTransactionRequest", List("mUserId", "mOnBehalfOfUserId"), "record both: mUserId = userId, mOnBehalfOfUserId = onBehalfOfUserId")
  case object EntitlementUserId                                    extends UserReference(UseOnBehalfOfUserId   , "code.entitlement.MappedEntitlement", List("mUserId"), "the role holder; the consent-engine case is ConsentEntitlementUser")
  case object AccountHoldersUser                                   extends UserReference(UseOnBehalfOfUserId   , "code.accountholders.MapperAccountHolders", List("user"), "the human holds the account; one held by a per-consent identity strands when the consent dies")
  case object UserCustomerLinkUserId                               extends UserReference(UseOnBehalfOfUserId   , "code.usercustomerlinks.MappedUserCustomerLink", List("mUserId"), "a Customer is linked to a human; a link on an agent identity dies with its Consent")
  case object AccountApplicationUserId                             extends UserReference(UseOnBehalfOfUserId   , "code.accountapplication.MappedAccountApplication", List("mUserId"), "explicit target: user_id comes from the request and is guarded at the endpoint, so the provider redirect is unreachable -- see ON_BEHALF_OF_USER_ID_PLAN.md row 14")
  case object AccountAccessRequestRequestorUserId                  extends UserReference(UseOnBehalfOfUserId   , "code.accountaccessrequest.AccountAccessRequest", List("RequestorUserId"), "who asked for access; the request outlives the session it was made in")
  case object AccountAccessRequestTargetUserId                     extends UserReference(UseOnBehalfOfUserId   , "code.accountaccessrequest.AccountAccessRequest", List("TargetUserId"), "explicit target: a consent user named here is rejected at the endpoint")
  case object AccountAccessRequestCheckerUserId                    extends UserReference(UseOnBehalfOfUserId   , "code.accountaccessrequest.AccountAccessRequest", List("CheckerUserId"), "who approved; maker/checker evidence has to name a human")
  case object DynamicChangeRequestRequestorUserId                  extends UserReference(UseOnBehalfOfUserId   , "code.dynamicchangerequest.DynamicChangeRequest", List("RequestorUserId"), "maker of a dynamic-code change")
  case object DynamicChangeRequestCheckerUserId                    extends UserReference(UseOnBehalfOfUserId   , "code.dynamicchangerequest.DynamicChangeRequest", List("CheckerUserId"), "checker; must differ from the requestor")
  case object EntitlementRequestUserId                             extends UserReference(UseOnBehalfOfUserId   , "code.entitlementrequest.MappedEntitlementRequest", List("mUserId"), "who asked for the role; the grant that follows lands on a human")
  case object UserScopeUserId                                      extends UserReference(UseOnBehalfOfUserId   , "code.scope.MappedUserScope", List("mUserId"), "the scope holder")
  case object ApiCollectionUserId                                  extends UserReference(UseOnBehalfOfUserId   , "code.apicollection.ApiCollection", List("UserId"), "the user's own saved collection, created through POST /my/api-collections")
  case object UserAttributeUserId                                  extends UserReference(UseOnBehalfOfUserId   , "code.users.UserAttribute", List("UserId"), "the user's own attribute, created through the /my/ endpoints")
  case object UserAgreementUserId                                  extends UserReference(UseOnBehalfOfUserId   , "code.users.UserAgreement", List("UserId"), "the user's own acceptance of terms")
  case object UserInitActionUserId                                 extends UserReference(UseOnBehalfOfUserId   , "code.users.UserInitAction", List("UserId"), "the user's own onboarding action")
  case object UserAuthContextUserId                                extends UserReference(UseOnBehalfOfUserId   , "code.context.MappedUserAuthContext", List("mUserId"), "consent copies the on-behalf-of user's contexts into ConsentAuthContext separately")
  case object UserAuthContextUpdateUserId                          extends UserReference(UseOnBehalfOfUserId   , "code.context.MappedUserAuthContextUpdate", List("mUserId"), "as UserAuthContextUser")
  case object DynamicEntityUserId                                  extends UserReference(UseOnBehalfOfUserId   , "code.dynamicEntity.DynamicEntity", List("UserId"), "the definition's creator; a definition outlives the Consent that created it")
  case object DynamicDataUserId                                    extends UserReference(UseOnBehalfOfUserId   , "code.DynamicData.DynamicData", List("UserId"), "personal rows, and the one reference where the redirect MUST be symmetric: MapppedDynamicDataProvider resolves on save/update/get/delete alike, because a row keyed by this column on both sides is otherwise written by an agent and then invisible to it")
  case object DynamicDataAccessUserId                              extends UserReference(UseOnBehalfOfUserId   , "code.DynamicData.DynamicDataAccess", List("UserId"), "row-level ACL. Deliberately still on the consent user today, because the bootstrap grant and the allows check have to agree with each other -- rows strand, nothing leaks. A later Phase 2 row; see the plan")
  case object DynamicEndpointUserId                                extends UserReference(UseOnBehalfOfUserId   , "code.DynamicEndpoint.DynamicEndpoint", List("UserId"), "the dynamic endpoint's creator; outlives the Consent")
  case object DynamicResourceDocCreatedByUserIdUpdatedByUserId     extends UserReference(UseOnBehalfOfUserId   , "code.dynamicResourceDoc.DynamicResourceDoc", List("CreatedByUserId", "UpdatedByUserId"), "dynamic artefact that outlives the Consent that created it")
  case object DynamicMessageDocCreatedByUserIdUpdatedByUserId      extends UserReference(UseOnBehalfOfUserId   , "code.dynamicMessageDoc.DynamicMessageDoc", List("CreatedByUserId", "UpdatedByUserId"), "dynamic artefact that outlives the Consent that created it")
  case object ConnectorMethodCreatedByUserIdUpdatedByUserId        extends UserReference(UseOnBehalfOfUserId   , "code.connectormethod.ConnectorMethod", List("CreatedByUserId", "UpdatedByUserId"), "dynamic artefact that outlives the Consent that created it")
  case object AbacRuleCreatedByUserIdUpdatedByUserId               extends UserReference(UseOnBehalfOfUserId   , "code.abacrule.AbacRule", List("CreatedByUserId", "UpdatedByUserId"), "an access rule outlives the Consent that created it")
  case object CounterpartyCreatedByUserIdCreatedByOnBehalfOfUserId extends UserReference(UseOnBehalfOfUserId   , "code.metadata.counterparties.MappedCounterparty", List("mCreatedByUserId", "mCreatedByOnBehalfOfUserId"), "record both: a counterparty controls where money may be sent, so the actor stays on mCreatedByUserId (and is published as created_by_user_id) while the human goes to mCreatedByOnBehalfOfUserId")
  case object CounterpartyWhereTagUser                             extends UserReference(UseOnBehalfOfUserId   , "code.metadata.counterparties.MappedCounterpartyWhereTag", List("user"), "who tagged the counterparty's location")
  case object ApiProductSubscriptionCreatedByUserId                extends UserReference(UseOnBehalfOfUserId   , "code.apiproductsubscription.ApiProductSubscription", List("CreatedByUserId"), "a subscription outlives the Consent that took it out")
  case object DynamicGlossaryItemCreatedByUserId                   extends UserReference(UseOnBehalfOfUserId   , "code.glossaryitem.DynamicGlossaryItem", List("CreatedByUserId"), "outlives the Consent that created it")
  case object BankCreatedByUserId                                  extends UserReference(UseOnBehalfOfUserId   , "code.model.dataAccess.MappedBank", List("CreatedByUserId"), "creator grant already resolved at the endpoint")
  case object OrganisationCreatedByUserId                          extends UserReference(UseOnBehalfOfUserId   , "code.organisation.Organisation", List("CreatedByUserId"), "outlives the Consent that created it")
  case object PayeeLookupCreatedByUserId                           extends UserReference(UseOnBehalfOfUserId   , "code.payeelookup.PayeeLookup", List("CreatedByUserId"), "outlives the Consent that created it")
  case object RoutingSchemeCreatedByUserId                         extends UserReference(UseOnBehalfOfUserId   , "code.routingscheme.RoutingScheme", List("CreatedByUserId"), "outlives the Consent that created it")
  case object UtilityPaymentCallbackCreatedByUserId                extends UserReference(UseOnBehalfOfUserId   , "code.utilitypayment.UtilityPaymentCallback", List("CreatedByUserId"), "outlives the Consent that created it")
  case object StandingOrderUserId                                  extends UserReference(UseOnBehalfOfUserId   , "code.standingorders.StandingOrder", List("UserId"), "the payer; a standing order keeps executing long after any Consent expires")
  case object DirectDebitUserId                                    extends UserReference(UseOnBehalfOfUserId   , "code.directdebit.DirectDebit", List("UserId"), "the payer; a mandate keeps executing long after any Consent expires")
  case object MandateCreatedByUserIdUpdatedByUserId                extends UserReference(UseOnBehalfOfUserId   , "code.mandate.Mandate", List("CreatedByUserId", "UpdatedByUserId"), "a mandate authorises payment and outlives the Consent that created it")
  case object SignatoryPanelUserIds                                extends UserReference(UseOnBehalfOfUserId   , "code.mandate.SignatoryPanel", List("UserIds"), "list of user ids")
  case object AccountWebhookCreatedByUserId                        extends UserReference(UseOnBehalfOfUserId   , "code.webhook.MappedAccountWebhook", List("mCreatedByUserId"), "ownership KEY, not just provenance: getAccountWebhooksByUserIdFuture queries By(mCreatedByUserId, userId), so an agent-created webhook is invisible to the human")
  case object SystemAccountNotificationWebhookCreatedByUserId      extends UserReference(UseOnBehalfOfUserId   , "code.webhook.SystemAccountNotificationWebhook", List("CreatedByUserId"), "as AccountWebhookCreator")
  case object BankAccountNotificationWebhookCreatedByUserId        extends UserReference(UseOnBehalfOfUserId   , "code.webhook.BankAccountNotificationWebhook", List("CreatedByUserId"), "as AccountWebhookCreator")
  case object ChatRoomCreatedByUserId                              extends UserReference(UseOnBehalfOfUserId   , "code.chat.ChatRoom", List("CreatedByUserId"), "Portal chat: a human's room")
  case object ParticipantUserId                                    extends UserReference(UseOnBehalfOfUserId   , "code.chat.Participant", List("UserId"), "the person in the room")
  case object ReactionUserId                                       extends UserReference(UseOnBehalfOfUserId   , "code.chat.Reaction", List("UserId"), "the person who reacted")
  case object ChatEmailDigestStateUserId                           extends UserReference(UseOnBehalfOfUserId   , "code.chat.ChatEmailDigestState", List("UserId"), "the person the digest is for")
  case object ChatMessageMentionedUserIds                          extends UserReference(UseOnBehalfOfUserId   , "code.chat.ChatMessage", List("MentionedUserIds"), "explicit targets, humans by construction")
  case object CrmEventUserId                                       extends UserReference(UseOnBehalfOfUserId   , "code.crm.MappedCrmEvent", List("mUserId"), "the user the event concerns")
  case object KycCheckUser                                         extends UserReference(UseOnBehalfOfUserId   , "code.kycchecks.MappedKycCheck", List("user"), "the customer's user")
  case object KycCheckStaffUserId                                  extends UserReference(UseOnBehalfOfUserId   , "code.kycchecks.MappedKycCheck", List("mStaffUserId"), "staff = human operator")
  case object KycDocumentUser                                      extends UserReference(UseOnBehalfOfUserId   , "code.kycdocuments.MappedKycDocument", List("user"), "the customer's user")
  case object KycStatusUser                                        extends UserReference(UseOnBehalfOfUserId   , "code.kycstatuses.MappedKycStatus", List("user"), "the customer's user")
  case object SocialMediaUser                                      extends UserReference(UseOnBehalfOfUserId   , "code.socialmedia.MappedSocialMedia", List("user"), "the customer's user")
  case object CustomerMessageUser                                  extends UserReference(UseOnBehalfOfUserId   , "code.customer.MappedCustomerMessage", List("user"), "the customer's user")
  case object MeetingCustomerUserId                                extends UserReference(UseOnBehalfOfUserId   , "code.meetings.MappedMeeting", List("mCustomerUserId"), "the customer side of the meeting")
  case object MeetingStaffUserId                                   extends UserReference(UseOnBehalfOfUserId   , "code.meetings.MappedMeeting", List("mStaffUserId"), "staff = human operator")
  case object TagUser                                              extends UserReference(UseOnBehalfOfUserId   , "code.metadata.tags.MappedTag", List("user"), "the author of the annotation; it outlives the Consent")
  case object WhereTagUser                                         extends UserReference(UseOnBehalfOfUserId   , "code.metadata.wheretags.MappedWhereTag", List("user"), "the author of the annotation; it outlives the Consent")
  case object TransactionImageUser                                 extends UserReference(UseOnBehalfOfUserId   , "code.metadata.transactionimages.MappedTransactionImage", List("user"), "the author of the annotation; it outlives the Consent")

  // ---- Reject: a consent user must not do this at all
  case object ConsentUserId                                        extends UserReference(Reject                , "code.consent.MappedConsent", List("mUserId"), "a consent user creating a consent = nested delegation")
  case object ConsumerCreatedByUserId                              extends UserReference(Reject                , "code.model.Consumer", List("createdByUserId"), "credentials outlive the consent")
  case object TokenUserForeignKey                                  extends UserReference(Reject                , "code.model.Token", List("userForeignKey"), "credentials outlive the consent")

  /** Every reference; the frozen test walks this. */
  lazy val all: List[UserReference] = List(
    AccountAccessUserFk,
    EntitlementUserIdConsentScope,
    EntitlementGrantedByUserId,
    UserLocksUserId,
    ExpectedChallengeAnswerExpectedUserId,
    ChatMessageSenderUserId,
    MetricUserId,
    MetricArchiveUserId,
    ConnectorTraceUserId,
    DynamicDataAccessGrantedBy,
    AuthUserUser,
    OpenIDConnectTokenAuthUserPrimaryKey,
    UserRefreshesUserId,
    TransactionRequestUserIdOnBehalfOfUserId,
    EntitlementUserId,
    AccountHoldersUser,
    UserCustomerLinkUserId,
    AccountApplicationUserId,
    AccountAccessRequestRequestorUserId,
    AccountAccessRequestTargetUserId,
    AccountAccessRequestCheckerUserId,
    DynamicChangeRequestRequestorUserId,
    DynamicChangeRequestCheckerUserId,
    EntitlementRequestUserId,
    UserScopeUserId,
    ApiCollectionUserId,
    UserAttributeUserId,
    UserAgreementUserId,
    UserInitActionUserId,
    UserAuthContextUserId,
    UserAuthContextUpdateUserId,
    DynamicEntityUserId,
    DynamicDataUserId,
    DynamicDataAccessUserId,
    DynamicEndpointUserId,
    DynamicResourceDocCreatedByUserIdUpdatedByUserId,
    DynamicMessageDocCreatedByUserIdUpdatedByUserId,
    ConnectorMethodCreatedByUserIdUpdatedByUserId,
    AbacRuleCreatedByUserIdUpdatedByUserId,
    CounterpartyCreatedByUserIdCreatedByOnBehalfOfUserId,
    CounterpartyWhereTagUser,
    ApiProductSubscriptionCreatedByUserId,
    DynamicGlossaryItemCreatedByUserId,
    BankCreatedByUserId,
    OrganisationCreatedByUserId,
    PayeeLookupCreatedByUserId,
    RoutingSchemeCreatedByUserId,
    UtilityPaymentCallbackCreatedByUserId,
    StandingOrderUserId,
    DirectDebitUserId,
    MandateCreatedByUserIdUpdatedByUserId,
    SignatoryPanelUserIds,
    AccountWebhookCreatedByUserId,
    SystemAccountNotificationWebhookCreatedByUserId,
    BankAccountNotificationWebhookCreatedByUserId,
    ChatRoomCreatedByUserId,
    ParticipantUserId,
    ReactionUserId,
    ChatEmailDigestStateUserId,
    ChatMessageMentionedUserIds,
    CrmEventUserId,
    KycCheckUser,
    KycCheckStaffUserId,
    KycDocumentUser,
    KycStatusUser,
    SocialMediaUser,
    CustomerMessageUser,
    MeetingCustomerUserId,
    MeetingStaffUserId,
    TagUser,
    WhereTagUser,
    TransactionImageUser,
    ConsentUserId,
    ConsumerCreatedByUserId,
    TokenUserForeignKey
  )

  /** Mapper fields the frozen test's name pattern matches but which are not user ids.
   *
   *  Only columns the pattern actually catches belong here; UserReferenceAttributionPolicyTest fails
   *  on an entry that matches nothing, because an inert exclusion looks like cover it is not giving.
   *  (Removed for that reason: AccountAccessRequest.CheckerComment, DynamicChangeRequest.CheckerComment,
   *  MappedKycCheck.mStaffName, MappedMeeting.mStaffToken -- all free text, and none of them matched.) */
  val notUserIdColumns: List[(String, String, String)] = List(
    ("code.model.dataAccess.MappedBankAccount", "holder", "free-text holder name"),
    ("code.transaction.MappedTransaction", "counterpartyAccountHolder", "free-text name"),
    ("code.entitlement.MappedEntitlement", "mCreatedByProcess", "process tag"),
    ("code.model.dataAccess.ResourceUser", "userId_", "the user's own id"),
    ("code.model.dataAccess.ResourceUser", "CreatedByConsentId", "consent id"),
    ("code.model.dataAccess.ResourceUser", "CreatedByUserInvitationId", "invitation id")
  )

  def byPolicy(p: AttributionPolicy): List[UserReference] = all.filter(_.policy == p)
}
