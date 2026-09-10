package code.users

/**
 * Attribution policy: what a user-reference column stores when the caller is a consent user.
 * Design and vocabulary: OBP-API/ON_BEHALF_OF_USER_ID_PLAN.md ("The policy file").
 *
 *  - KeepUserId          the authenticated user's own id; no resolver
 *  - UseOnBehalfOfUserId the on-behalf-of user's id, via Users.onBehalfOfUserIdOf
 *  - Reject              a consent user must not do this at all: Failure -> 400
 */
sealed trait AttributionPolicy
object AttributionPolicy {
  case object KeepUserId          extends AttributionPolicy
  case object UseOnBehalfOfUserId extends AttributionPolicy
  case object Reject              extends AttributionPolicy
}

/**
 * What a provider gets back from Users.attributionOf: everything it should store, plus the
 * facts the resolver logged. userId is the authenticated caller; onBehalfOfUserId is who owns
 * what the call creates (== userId for an original user acting alone; for a KeepUserId
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
 * One value per user-reference column (or per record-both table). This file IS the policy
 * table: Users.attributionOf reads it at runtime, and UserReferenceAttributionPolicyTest
 * (frozen-style) asserts every Mapper column whose name looks like a user reference is named
 * by exactly one value here (or listed in notUserIdColumns). A new table fails until sorted.
 *
 * mapperClass is the fully-qualified class that owns the write - on this branch a Doobie store or
 * row rather than a Lift Mapper entity - and fields are the database column names it writes. Both
 * are read only to build the log line in Users.attributionOf, and AgentDelegationTest asserts the
 * class actually resolves, so an entry naming something this branch does not have fails the suite.
 * Two upstream entries are absent here for that reason: PemUsageLastUser (the PemUsage scaffold was
 * deleted - no table was ever created for it) and UserScopeUser (MappedUserScope had no callers and
 * was deleted with its table). Restore them alongside their tables if either feature comes back.
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

  // ---- KeepUserId: authorisation materialisation and audit of the actor
  case object AccountAccessUser                       extends UserReference(KeepUserId         , "code.views.system.AccountAccess", List("user_fk"), "views copied from the consent JWT each request; has lifecycle GC")
  case object ConsentEntitlementUser                  extends UserReference(KeepUserId         , "code.entitlement.MappedEntitlement", List("mUserId"), "only when createdByProcess == consent_user: the consent engine copying the consent's own scope")
  case object EntitlementGrantedBy                    extends UserReference(KeepUserId         , "code.entitlement.MappedEntitlement", List("mGrantedByUserId"), "audit: who granted")
  case object UserLocksUser                           extends UserReference(KeepUserId         , "code.userlocks.UserLocksProvider", List("userid"), "lock the authenticated user")
  case object ExpectedChallengeAnswerUser             extends UserReference(KeepUserId         , "code.transactionChallenge.MappedExpectedChallengeAnswer", List("ExpectedUserId"), "the challenge is answered by the initiating user")
  case object ChatMessageSender                       extends UserReference(KeepUserId         , "code.chat.ChatMessage", List("SenderUserId"), "sender = the authenticated user is truthful")
  case object MetricUser                              extends UserReference(KeepUserId         , "code.metrics.MappedMetric", List("userId"), "record both: on-behalf-of via consent_reference_id at read time")
  case object MetricArchiveUser                       extends UserReference(KeepUserId         , "code.metrics.MetricArchive", List("userId"), "as MetricUser")
  case object ConnectorTraceUser                      extends UserReference(KeepUserId         , "code.metrics.DoobieConnectorTrace", List("userid"), "as MetricUser")
  case object DynamicDataAccessGrantedBy              extends UserReference(KeepUserId         , "code.DynamicData.DynamicDataAccess", List("GrantedBy"), "audit: who granted")
  case object AuthUserResourceUser                    extends UserReference(KeepUserId         , "code.model.dataAccess.AuthUser", List("user"), "login row -> its own ResourceUser; not attribution")
  case object OpenIDConnectTokenUser                  extends UserReference(KeepUserId         , "code.token.OpenIDConnectToken", List("AuthUserPrimaryKey"), "token belongs to the login; not attribution")
  case object UserRefreshesUser                       extends UserReference(KeepUserId         , "code.UserRefreshes.DoobieUserRefreshesProvider", List("muserid"), "operational: refresh of the authenticated user's own account list")

  // ---- UseOnBehalfOfUserId: ownership / attribution (record-both tables list both columns)
  case object TransactionRequest                      extends UserReference(UseOnBehalfOfUserId, "code.transactionrequests.MappedTransactionRequest", List("mUserId", "mOnBehalfOfUserId"), "record both: mUserId = userId, mOnBehalfOfUserId = onBehalfOfUserId")
  case object EntitlementUser                         extends UserReference(UseOnBehalfOfUserId, "code.entitlement.MappedEntitlement", List("mUserId"), "the role holder; the consent-engine case is ConsentEntitlementUser")
  case object AccountHolderUser                       extends UserReference(UseOnBehalfOfUserId, "code.accountholders.MapperAccountHolders", List("user"))
  case object UserCustomerLinkUser                    extends UserReference(UseOnBehalfOfUserId, "code.usercustomerlinks.DoobieUserCustomerLinkProvider", List("muserid"))
  case object AccountApplicationUser                  extends UserReference(UseOnBehalfOfUserId, "code.accountapplication.MappedAccountApplication", List("mUserId"))
  case object AccountAccessRequestRequestor           extends UserReference(UseOnBehalfOfUserId, "code.accountaccessrequest.AccountAccessRequest", List("RequestorUserId"))
  case object AccountAccessRequestTarget              extends UserReference(UseOnBehalfOfUserId, "code.accountaccessrequest.AccountAccessRequest", List("TargetUserId"), "explicit target: a consent user named here is rejected at the endpoint")
  case object AccountAccessRequestChecker             extends UserReference(UseOnBehalfOfUserId, "code.accountaccessrequest.AccountAccessRequest", List("CheckerUserId"))
  case object DynamicChangeRequestRequestor           extends UserReference(UseOnBehalfOfUserId, "code.dynamicchangerequest.DynamicChangeRequest", List("RequestorUserId"), "maker of a dynamic-code change")
  case object DynamicChangeRequestChecker             extends UserReference(UseOnBehalfOfUserId, "code.dynamicchangerequest.DynamicChangeRequest", List("CheckerUserId"), "checker; must differ from the requestor")
  case object EntitlementRequestUser                  extends UserReference(UseOnBehalfOfUserId, "code.entitlementrequest.MappedEntitlementRequest", List("mUserId"))
  case object ApiCollectionUser                       extends UserReference(UseOnBehalfOfUserId, "code.apicollection.DoobieApiCollectionsProvider", List("userid"))
  case object UserAttributeUser                       extends UserReference(UseOnBehalfOfUserId, "code.users.UserAttribute", List("UserId"))
  case object UserAgreementUser                       extends UserReference(UseOnBehalfOfUserId, "code.users.UserAgreement", List("UserId"))
  case object UserInitActionUser                      extends UserReference(UseOnBehalfOfUserId, "code.users.UserInitActionProvider", List("userid"))
  case object UserAuthContextUser                     extends UserReference(UseOnBehalfOfUserId, "code.context.DoobieUserAuthContextProvider", List("muserid"), "consent copies the on-behalf-of user's contexts into ConsentAuthContext separately")
  case object UserAuthContextUpdateUser               extends UserReference(UseOnBehalfOfUserId, "code.context.MappedUserAuthContextUpdateProvider", List("muserid"))
  case object DynamicEntityUser                       extends UserReference(UseOnBehalfOfUserId, "code.dynamicEntity.DynamicEntity", List("UserId"))
  case object DynamicDataUser                         extends UserReference(UseOnBehalfOfUserId, "code.DynamicData.DynamicData", List("UserId"))
  case object DynamicDataAccessUser                   extends UserReference(UseOnBehalfOfUserId, "code.DynamicData.DynamicDataAccess", List("UserId"))
  case object DynamicEndpointUser                     extends UserReference(UseOnBehalfOfUserId, "code.DynamicEndpoint.DynamicEndpoint", List("UserId"))
  case object DynamicResourceDocCreator               extends UserReference(UseOnBehalfOfUserId, "code.dynamicResourceDoc.DynamicResourceDoc", List("CreatedByUserId", "UpdatedByUserId"))
  case object DynamicMessageDocCreator                extends UserReference(UseOnBehalfOfUserId, "code.dynamicMessageDoc.DynamicMessageDoc", List("CreatedByUserId", "UpdatedByUserId"))
  case object ConnectorMethodCreator                  extends UserReference(UseOnBehalfOfUserId, "code.connectormethod.DoobieConnectorMethodProvider", List("createdbyuserid", "updatedbyuserid"))
  case object AbacRuleCreator                         extends UserReference(UseOnBehalfOfUserId, "code.abacrule.AbacRule", List("CreatedByUserId", "UpdatedByUserId"))
  case object CounterpartyCreator                     extends UserReference(UseOnBehalfOfUserId, "code.metadata.counterparties.MappedCounterparty", List("mCreatedByUserId"))
  case object CounterpartyWhereTagUser                extends UserReference(UseOnBehalfOfUserId, "code.metadata.counterparties.MappedCounterpartyWhereTag", List("user"))
  case object BankCreator                             extends UserReference(UseOnBehalfOfUserId, "code.model.dataAccess.MappedBank", List("CreatedByUserId"), "creator grant already resolved at the endpoint")
  case object OrganisationCreator                     extends UserReference(UseOnBehalfOfUserId, "code.organisation.DoobieOrganisationProvider", List("createdbyuserid"))
  case object PayeeLookupCreator                      extends UserReference(UseOnBehalfOfUserId, "code.payeelookup.DoobiePayeeLookupProvider", List("createdbyuserid"))
  case object RoutingSchemeCreator                    extends UserReference(UseOnBehalfOfUserId, "code.routingscheme.RoutingScheme", List("CreatedByUserId"))
  case object UtilityPaymentCallbackCreator           extends UserReference(UseOnBehalfOfUserId, "code.utilitypayment.DoobieUtilityPaymentCallbackProvider", List("createdbyuserid"))
  case object StandingOrderUser                       extends UserReference(UseOnBehalfOfUserId, "code.standingorders.StandingOrder", List("UserId"))
  case object DirectDebitUser                         extends UserReference(UseOnBehalfOfUserId, "code.directdebit.DirectDebit", List("UserId"))
  case object MandateCreator                          extends UserReference(UseOnBehalfOfUserId, "code.mandate.Mandate", List("CreatedByUserId", "UpdatedByUserId"))
  case object SignatoryPanelUsers                     extends UserReference(UseOnBehalfOfUserId, "code.mandate.SignatoryPanel", List("UserIds"), "list of user ids")
  case object AccountWebhookCreator                   extends UserReference(UseOnBehalfOfUserId, "code.webhook.MappedAccountWebhook", List("mCreatedByUserId"))
  case object SystemAccountNotificationWebhookCreator extends UserReference(UseOnBehalfOfUserId, "code.webhook.SystemAccountNotificationWebhook", List("CreatedByUserId"))
  case object BankAccountNotificationWebhookCreator   extends UserReference(UseOnBehalfOfUserId, "code.webhook.BankAccountNotificationWebhook", List("CreatedByUserId"))
  case object ChatRoomCreator                         extends UserReference(UseOnBehalfOfUserId, "code.chat.ChatRoom", List("CreatedByUserId"), "Portal chat: a human's room")
  case object ChatParticipantUser                     extends UserReference(UseOnBehalfOfUserId, "code.chat.Participant", List("UserId"))
  case object ChatReactionUser                        extends UserReference(UseOnBehalfOfUserId, "code.chat.Reaction", List("UserId"))
  case object ChatEmailDigestStateUser                extends UserReference(UseOnBehalfOfUserId, "code.chat.ChatEmailDigestState", List("UserId"))
  case object ChatMessageMentionedUsers               extends UserReference(UseOnBehalfOfUserId, "code.chat.ChatMessage", List("MentionedUserIds"), "explicit targets, humans by construction")
  case object CrmEventUser                            extends UserReference(UseOnBehalfOfUserId, "code.crm.DoobieCrmEventProvider", List("muserid"))
  case object KycCheckUser                            extends UserReference(UseOnBehalfOfUserId, "code.kycchecks.MappedKycCheck", List("user"), "the customer's user")
  case object KycCheckStaff                           extends UserReference(UseOnBehalfOfUserId, "code.kycchecks.MappedKycCheck", List("mStaffUserId"), "staff = human operator")
  case object KycDocumentUser                         extends UserReference(UseOnBehalfOfUserId, "code.kycdocuments.MappedKycDocument", List("user"))
  case object KycStatusUser                           extends UserReference(UseOnBehalfOfUserId, "code.kycstatuses.MappedKycStatus", List("user"))
  case object SocialMediaUser                         extends UserReference(UseOnBehalfOfUserId, "code.socialmedia.MappedSocialMedia", List("user"))
  case object CustomerMessageUser                     extends UserReference(UseOnBehalfOfUserId, "code.customer.MappedCustomerMessage", List("user"))
  case object MeetingCustomerUser                     extends UserReference(UseOnBehalfOfUserId, "code.meetings.MappedMeeting", List("mCustomerUserId"))
  case object MeetingStaffUser                        extends UserReference(UseOnBehalfOfUserId, "code.meetings.MappedMeeting", List("mStaffUserId"), "staff = human operator")
  case object TagUser                                 extends UserReference(UseOnBehalfOfUserId, "code.metadata.tags.DoobieTags", List("user_c"))
  case object WhereTagUser                            extends UserReference(UseOnBehalfOfUserId, "code.metadata.wheretags.DoobieWhereTags", List("user_c"))
  case object TransactionImageUser                    extends UserReference(UseOnBehalfOfUserId, "code.metadata.transactionimages.DoobieTransactionImages", List("user_c"))

  // ---- Reject: a consent user must not do this at all
  case object ConsentCreator                          extends UserReference(Reject             , "code.consent.MappedConsent", List("mUserId"), "a consent user creating a consent = nested delegation")
  case object OAuthConsumerCreator                    extends UserReference(Reject             , "code.model.Consumer", List("createdByUserId"), "credentials outlive the consent")
  case object OAuthTokenUser                          extends UserReference(Reject             , "code.model.Token", List("userForeignKey"), "credentials outlive the consent")

  /** Every reference; the frozen test walks this. */
  lazy val all: List[UserReference] = List(
    AccountAccessUser,
    ConsentEntitlementUser,
    EntitlementGrantedBy,
    UserLocksUser,
    ExpectedChallengeAnswerUser,
    ChatMessageSender,
    MetricUser,
    MetricArchiveUser,
    ConnectorTraceUser,
    DynamicDataAccessGrantedBy,
    AuthUserResourceUser,
    OpenIDConnectTokenUser,
    UserRefreshesUser,
    TransactionRequest,
    EntitlementUser,
    AccountHolderUser,
    UserCustomerLinkUser,
    AccountApplicationUser,
    AccountAccessRequestRequestor,
    AccountAccessRequestTarget,
    AccountAccessRequestChecker,
    DynamicChangeRequestRequestor,
    DynamicChangeRequestChecker,
    EntitlementRequestUser,
    ApiCollectionUser,
    UserAttributeUser,
    UserAgreementUser,
    UserInitActionUser,
    UserAuthContextUser,
    UserAuthContextUpdateUser,
    DynamicEntityUser,
    DynamicDataUser,
    DynamicDataAccessUser,
    DynamicEndpointUser,
    DynamicResourceDocCreator,
    DynamicMessageDocCreator,
    ConnectorMethodCreator,
    AbacRuleCreator,
    CounterpartyCreator,
    CounterpartyWhereTagUser,
    BankCreator,
    OrganisationCreator,
    PayeeLookupCreator,
    RoutingSchemeCreator,
    UtilityPaymentCallbackCreator,
    StandingOrderUser,
    DirectDebitUser,
    MandateCreator,
    SignatoryPanelUsers,
    AccountWebhookCreator,
    SystemAccountNotificationWebhookCreator,
    BankAccountNotificationWebhookCreator,
    ChatRoomCreator,
    ChatParticipantUser,
    ChatReactionUser,
    ChatEmailDigestStateUser,
    ChatMessageMentionedUsers,
    CrmEventUser,
    KycCheckUser,
    KycCheckStaff,
    KycDocumentUser,
    KycStatusUser,
    SocialMediaUser,
    CustomerMessageUser,
    MeetingCustomerUser,
    MeetingStaffUser,
    TagUser,
    WhereTagUser,
    TransactionImageUser,
    ConsentCreator,
    OAuthConsumerCreator,
    OAuthTokenUser
  )

  /** Mapper fields the frozen test's name pattern matches but which are not user ids. */
  val notUserIdColumns: List[(String, String, String)] = List(
    ("code.model.dataAccess.MappedBankAccount", "holder", "free-text holder name"),
    ("code.transaction.MappedTransaction", "counterpartyAccountHolder", "free-text name"),
    ("code.accountaccessrequest.AccountAccessRequest", "CheckerComment", "text"),
    ("code.dynamicchangerequest.DynamicChangeRequest", "CheckerComment", "text"),
    ("code.kycchecks.MappedKycCheck", "mStaffName", "text"),
    ("code.meetings.MappedMeeting", "mStaffToken", "token"),
    ("code.entitlement.MappedEntitlement", "mCreatedByProcess", "process tag"),
    ("code.model.dataAccess.ResourceUser", "userId_", "the user's own id"),
    ("code.model.dataAccess.ResourceUser", "CreatedByConsentId", "consent id"),
    ("code.model.dataAccess.ResourceUser", "CreatedByUserInvitationId", "invitation id")
  )

  def byPolicy(p: AttributionPolicy): List[UserReference] = all.filter(_.policy == p)
}
