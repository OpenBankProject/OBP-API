package code.api.util

import java.util.concurrent.ConcurrentHashMap

import code.api.v4_0_0.{DynamicEndpointHelper, DynamicEntityHelper}
import com.openbankproject.commons.util.ReflectUtils

sealed trait ApiRole{
  val requiresBankId: Boolean
  override def toString() = getClass().getSimpleName

  def & (apiRole: ApiRole): RoleCombination = RoleCombination(this, apiRole)
}

/**
 * default relation of ApiRoles is or, So: List(role1, role2, role3) is: one of role1, role2 or role3.
 * this type is for and relationship, So: List(role1, role2 & role3) is: role1 or (role2 and role3)
 * @param left
 * @param right
 */
case class RoleCombination(left: ApiRole, right: ApiRole) extends ApiRole{
  val roles: List[ApiRole] = (left, right) match {
    case(l: RoleCombination, r: RoleCombination) => l.roles ::: r.roles
    case(l: RoleCombination, r: ApiRole) => l.roles :+ r
    case(l: ApiRole, r: RoleCombination) => l :: r.roles
    case _ => left :: right :: Nil
  }
  override val requiresBankId: Boolean = roles.exists(_.requiresBankId)
  override def toString() = roles.mkString("(", " and ", ")")
}

object RoleCombination {
  def unapply(role: ApiRole): Option[List[ApiRole]] = role match{
    case andRole: RoleCombination => Option(andRole.roles)
    case _ => None
  }
}

/** API Roles
  *
  * As a convention, Roles should start with one of:
  *
  * Can
  *   Create (in preference to Add)
  *   Get (in preference to Read)
  *   Update
  *   Delete
  *   Maintain
  *   Search
  *   Enable
  *   Disable
  *
  * If requiresBankId is true, its a bank specific Role else applies to all banks.
  *
  */

// Remember to add to the list of roles below


object ApiRole {

  case class CanSearchAllTransactions(requiresBankId: Boolean = false) extends ApiRole
  lazy val canSearchAllTransactions = CanSearchAllTransactions()

  case class CanSearchAllAccounts(requiresBankId: Boolean = false) extends ApiRole
  lazy val canSearchAllAccounts = CanSearchAllAccounts()

  case class CanQueryOtherUser(requiresBankId: Boolean = false) extends ApiRole
  lazy val canQueryOtherUser = CanQueryOtherUser()

  case class CanSearchWarehouse(requiresBankId: Boolean = false) extends ApiRole
  lazy val canSearchWarehouse = CanSearchWarehouse()

  case class CanSearchWarehouseStatistics(requiresBankId: Boolean = false) extends ApiRole
  lazy val canSearchWarehouseStatistics = CanSearchWarehouseStatistics()

  case class CanSearchMetrics(requiresBankId: Boolean = false) extends ApiRole
  lazy val canSearchMetrics = CanSearchMetrics()

  case class CanGetCustomer(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetCustomer = CanGetCustomer()

  case class CanCreateCustomer(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateCustomer = CanCreateCustomer()

  case class CanUpdateCustomerEmail(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateCustomerEmail = CanUpdateCustomerEmail()

  case class CanUpdateCustomerNumber(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateCustomerNumber = CanUpdateCustomerNumber()
  
  case class CanUpdateCustomerMobilePhoneNumber(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateCustomerMobilePhoneNumber = CanUpdateCustomerMobilePhoneNumber()  
  
  case class CanUpdateCustomerIdentity(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateCustomerIdentity = CanUpdateCustomerIdentity()

  case class CanUpdateCustomerBranch(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateCustomerBranch = CanUpdateCustomerBranch()

  case class CanUpdateCustomerData(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateCustomerData = CanUpdateCustomerData()

  case class CanUpdateCustomerCreditLimit(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateCustomerCreditLimit = CanUpdateCustomerCreditLimit()

  case class CanUpdateCustomerCreditRatingAndSource(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateCustomerCreditRatingAndSource = CanUpdateCustomerCreditRatingAndSource()

  case class CanCreateCustomerAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateCustomerAtAnyBank = CanCreateCustomerAtAnyBank()

  case class CanCreateUserCustomerLink(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateUserCustomerLink = CanCreateUserCustomerLink()
  
  case class CanDeleteUserCustomerLink(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteUserCustomerLink = CanDeleteUserCustomerLink()
  
  case class CanGetUserCustomerLink(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetUserCustomerLink = CanGetUserCustomerLink()

  case class CanCreateUserCustomerLinkAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateUserCustomerLinkAtAnyBank = CanCreateUserCustomerLinkAtAnyBank()
  
  case class CanGetUserCustomerLinkAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetUserCustomerLinkAtAnyBank = CanGetUserCustomerLinkAtAnyBank()
  
  case class CanDeleteUserCustomerLinkAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteUserCustomerLinkAtAnyBank = CanDeleteUserCustomerLinkAtAnyBank()

  case class CanCreateAccount(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateAccount = CanCreateAccount()

  case class CanUpdateAccount(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateAccount = CanUpdateAccount()

  case class CanCreateAccountAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateAccountAttributeAtOneBank = CanCreateAccountAttributeAtOneBank()
  
  case class CanUpdateAccountAttribute(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateAccountAttribute = CanUpdateAccountAttribute()
  
  case class CanGetAnyUser (requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetAnyUser = CanGetAnyUser()

  case class CanCreateAnyTransactionRequest(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateAnyTransactionRequest = CanCreateAnyTransactionRequest()

  case class CanAddSocialMediaHandle(requiresBankId: Boolean = true) extends ApiRole
  lazy val canAddSocialMediaHandle = CanAddSocialMediaHandle()

  case class CanGetSocialMediaHandles(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetSocialMediaHandles = CanGetSocialMediaHandles()

  case class CanCreateCustomerAddress(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateCustomerAddress = CanCreateCustomerAddress()

  case class CanDeleteCustomerAddress(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteCustomerAddress = CanDeleteCustomerAddress()

  case class CanGetCustomerAddress(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetCustomerAddress = CanGetCustomerAddress()

  case class CanCreateSandbox(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateSandbox = CanCreateSandbox()

  case class CanGetEntitlementsForAnyUserAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetEntitlementsForAnyUserAtOneBank = CanGetEntitlementsForAnyUserAtOneBank()

  case class CanCreateEntitlementAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateEntitlementAtOneBank = CanCreateEntitlementAtOneBank()

  case class CanDeleteEntitlementAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteEntitlementAtOneBank = CanDeleteEntitlementAtOneBank()

  case class CanGetEntitlementsForAnyUserAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetEntitlementsForAnyUserAtAnyBank = CanGetEntitlementsForAnyUserAtAnyBank()

  case class CanGetEntitlementsForOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetEntitlementsForOneBank = CanGetEntitlementsForOneBank()

  case class CanGetEntitlementsForAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetEntitlementsForAnyBank = CanGetEntitlementsForAnyBank()

  case class CanCreateEntitlementAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateEntitlementAtAnyBank = CanCreateEntitlementAtAnyBank()

  case class CanDeleteEntitlementAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteEntitlementAtAnyBank = CanDeleteEntitlementAtAnyBank()

  case class CanGetConsumers(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetConsumers = CanGetConsumers()

  case class CanDisableConsumers(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDisableConsumers = CanDisableConsumers()

  case class CanEnableConsumers(requiresBankId: Boolean = false) extends ApiRole
  lazy val canEnableConsumers = CanEnableConsumers()

  case class CanUpdateConsumerRedirectUrl(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUpdateConsumerRedirectUrl = CanUpdateConsumerRedirectUrl()

  case class CanCreateConsumer (requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateConsumer = CanCreateConsumer()

  case class CanCreateTransactionType(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateTransactionType = CanCreateTransactionType()

  case class CanCreateCardsForBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateCardsForBank = CanCreateCardsForBank()

  case class CanUpdateCardsForBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateCardsForBank = CanUpdateCardsForBank()

  case class CanDeleteCardsForBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteCardsForBank = CanDeleteCardsForBank()

  case class CanGetCardsForBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetCardsForBank = CanGetCardsForBank()
  
  case class CanCreateBranch(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateBranch = CanCreateBranch()

  case class CanUpdateBranch(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateBranch = CanUpdateBranch()
  
  case class CanCreateBranchAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateBranchAtAnyBank = CanCreateBranchAtAnyBank()

  case class CanDeleteBranch(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteBranch = CanDeleteBranch()

  case class CanDeleteBranchAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteBranchAtAnyBank = CanDeleteBranchAtAnyBank()

  case class CanCreateAtm(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateAtm = CanCreateAtm()

  case class CanCreateAtmAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateAtmAtAnyBank = CanCreateAtmAtAnyBank()

  case class CanCreateCounterparty(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateCounterparty = CanCreateCounterparty()
  
  case class CanCreateCounterpartyAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateCounterpartyAtAnyBank = CanCreateCounterpartyAtAnyBank()
  
  case class CanGetCounterparty(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetCounterparty = CanGetCounterparty()

  case class CanGetApiCollection(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetApiCollection = CanGetApiCollection()

  case class CanGetApiCollections(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetApiCollections = CanGetApiCollections()
  
  case class CanGetCounterpartyAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetCounterpartyAtAnyBank = CanGetCounterpartyAtAnyBank()
  
  case class CanCreateProduct(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateProduct = CanCreateProduct()

  case class CanCreateProductAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateProductAtAnyBank = CanCreateProductAtAnyBank()

  case class CanCreateFxRate(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateFxRate = CanCreateFxRate()

  case class CanCreateFxRateAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateFxRateAtAnyBank = CanCreateFxRateAtAnyBank()

  case class CanCreateBank (requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateBank = CanCreateBank()

  case class CanCreateSettlementAccountAtOneBank (requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateSettlementAccountAtOneBank = CanCreateSettlementAccountAtOneBank()

  case class CanGetSettlementAccountAtOneBank (requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetSettlementAccountAtOneBank = CanGetSettlementAccountAtOneBank()

  case class CanReadMetrics (requiresBankId: Boolean = false) extends ApiRole
  lazy val canReadMetrics = CanReadMetrics()

  case class CanGetConfig(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetConfig = CanGetConfig()
  
  case class CanGetDatabaseInfo(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetDatabaseInfo = CanGetDatabaseInfo()
  
  case class CanGetCallContext(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetCallContext = CanGetCallContext()

  case class CanGetConnectorMetrics(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetConnectorMetrics = CanGetConnectorMetrics()

  case class CanGetOtherAccountsAtBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetOtherAccountsAtBank = CanGetOtherAccountsAtBank()

  case class CanDeleteEntitlementRequestsAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteEntitlementRequestsAtOneBank = CanDeleteEntitlementRequestsAtOneBank()

  case class CanDeleteEntitlementRequestsAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteEntitlementRequestsAtAnyBank = CanDeleteEntitlementRequestsAtAnyBank()

  case class CanGetEntitlementRequestsAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetEntitlementRequestsAtOneBank = CanGetEntitlementRequestsAtOneBank()

  case class CanGetEntitlementRequestsAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetEntitlementRequestsAtAnyBank = CanGetEntitlementRequestsAtAnyBank()

  case class CanUseAccountFirehoseAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUseAccountFirehoseAtAnyBank = CanUseAccountFirehoseAtAnyBank()
  
  case class CanUseCustomerFirehoseAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUseCustomerFirehoseAtAnyBank = CanUseCustomerFirehoseAtAnyBank()

  case class CanReadAggregateMetrics (requiresBankId: Boolean = false) extends ApiRole
  lazy val canReadAggregateMetrics = CanReadAggregateMetrics()

  case class CanCreateScopeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateScopeAtOneBank = CanCreateScopeAtOneBank()

  case class CanCreateScopeAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateScopeAtAnyBank = CanCreateScopeAtAnyBank()

  case class CanDeleteScopeAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteScopeAtAnyBank = CanDeleteScopeAtAnyBank()

  case class CanDeleteScopeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteScopeAtOneBank = CanDeleteScopeAtOneBank()

  case class CanUnlockUser (requiresBankId: Boolean = false) extends ApiRole
  lazy val canUnlockUser = CanUnlockUser()
  
  case class CanLockUser (requiresBankId: Boolean = false) extends ApiRole
  lazy val canLockUser = CanLockUser()

  case class CanReadUserLockedStatus(requiresBankId: Boolean = false) extends ApiRole
  lazy val canReadUserLockedStatus = CanReadUserLockedStatus()

  case class CanSetCallLimits(requiresBankId: Boolean = false) extends ApiRole
  lazy val canSetCallLimits = CanSetCallLimits()

  case class CanReadCallLimits(requiresBankId: Boolean = false) extends ApiRole
  lazy val canReadCallLimits = CanReadCallLimits()

  case class CanCheckFundsAvailable (requiresBankId: Boolean = false) extends ApiRole
  lazy val canCheckFundsAvailable = CanCheckFundsAvailable()

  case class CanCreateWebhook(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateWebhook = CanCreateWebhook()

  case class CanUpdateWebhook(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateWebhook = CanUpdateWebhook()

  case class CanGetWebhooks(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetWebhooks = CanGetWebhooks()

  case class CanCreateUserAuthContext(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateUserAuthContext = CanCreateUserAuthContext()

  case class CanUpdateUserAuthContext(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUpdateUserAuthContext = CanUpdateUserAuthContext()

  case class CanGetUserAuthContext(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetUserAuthContext = CanGetUserAuthContext()

  case class CanDeleteUserAuthContext(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteUserAuthContext = CanDeleteUserAuthContext()

  case class CanCreateUserAuthContextUpdate(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateUserAuthContextUpdate = CanCreateUserAuthContextUpdate()

  case class CanGetTaxResidence(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetTaxResidence = CanGetTaxResidence()

  case class CanCreateTaxResidence(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateTaxResidence = CanCreateTaxResidence()

  case class CanDeleteTaxResidence(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteTaxResidence = CanDeleteTaxResidence()

  case class CanRefreshUser(requiresBankId: Boolean = false) extends ApiRole
  lazy val canRefreshUser = CanRefreshUser()

  case class CanGetAccountApplications(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetAccountApplications = CanGetAccountApplications()

  case class CanUpdateAccountApplications(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUpdateAccountApplications = CanUpdateAccountApplications()

  case class CanReadFx(requiresBankId: Boolean = true) extends ApiRole
  lazy val canReadFx = CanReadFx()

  case class CanUpdateProductAttribute(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateProductAttribute = CanUpdateProductAttribute()

  case class CanGetProductAttribute(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetProductAttribute = CanGetProductAttribute()

  case class CanDeleteProductAttribute(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteProductAttribute = CanDeleteProductAttribute()

  case class CanCreateProductAttribute(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateProductAttribute = CanCreateProductAttribute()

  case class CanMaintainProductCollection(requiresBankId: Boolean = true) extends ApiRole
  lazy val canMaintainProductCollection = CanMaintainProductCollection()

  case class CanCreateSystemView(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateSystemView = CanCreateSystemView()
  case class CanUpdateSystemView(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUpdateSystemView = CanUpdateSystemView()
  case class CanGetSystemView(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetSystemView = CanGetSystemView()
  case class CanDeleteSystemView(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteSystemView = CanDeleteSystemView()


  case class CanGetMethodRoutings(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetMethodRoutings = CanGetMethodRoutings()

  case class CanCreateMethodRouting(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateMethodRouting = CanCreateMethodRouting()

  case class CanUpdateMethodRouting(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUpdateMethodRouting = CanUpdateMethodRouting()

  case class CanDeleteMethodRouting(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteMethodRouting = CanDeleteMethodRouting()

  case class CanCreateHistoricalTransaction(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateHistoricalTransaction = CanCreateHistoricalTransaction()

  case class CanGetWebUiProps(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetWebUiProps = CanGetWebUiProps()

  case class CanCreateWebUiProps(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateWebUiProps = CanCreateWebUiProps()

  case class CanDeleteWebUiProps(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteWebUiProps = CanDeleteWebUiProps()

  case class CanGetDynamicEntities(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetDynamicEntities = CanGetDynamicEntities()

  case class CanCreateDynamicEntity(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateDynamicEntity = CanCreateDynamicEntity()

  case class CanUpdateDynamicEntity(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUpdateDynamicEntity = CanUpdateDynamicEntity()

  case class CanDeleteDynamicEntity(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteDynamicEntity = CanDeleteDynamicEntity()

  case class CanGetBankLevelDynamicEntities(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetBankLevelDynamicEntities = CanGetBankLevelDynamicEntities()

  case class CanGetDynamicEndpoint(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetDynamicEndpoint = CanGetDynamicEndpoint()
  
  case class CanGetDynamicEndpoints(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetDynamicEndpoints = CanGetDynamicEndpoints()

  case class CanCreateDynamicEndpoint(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateDynamicEndpoint = CanCreateDynamicEndpoint()

  case class CanUpdateDynamicEndpoint(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUpdateDynamicEndpoint = CanUpdateDynamicEndpoint()

  case class CanDeleteDynamicEndpoint(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteDynamicEndpoint = CanDeleteDynamicEndpoint()
  
  case class CanCreateResetPasswordUrl(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateResetPasswordUrl = CanCreateResetPasswordUrl()

  case class CanAddKycCheck(requiresBankId: Boolean = true) extends ApiRole
  lazy val canAddKycCheck = CanAddKycCheck()

  case class CanAddKycDocument(requiresBankId: Boolean = true) extends ApiRole
  lazy val canAddKycDocument = CanAddKycDocument()

  case class CanAddKycMedia(requiresBankId: Boolean = true) extends ApiRole
  lazy val canAddKycMedia = CanAddKycMedia()

  case class CanAddKycStatus(requiresBankId: Boolean = true) extends ApiRole
  lazy val canAddKycStatus = CanAddKycStatus()

  case class CanGetAnyKycChecks(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetAnyKycChecks = CanGetAnyKycChecks()

  case class CanGetAnyKycDocuments(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetAnyKycDocuments = CanGetAnyKycDocuments()

  case class CanGetAnyKycMedia(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetAnyKycMedia = CanGetAnyKycMedia()

  case class CanGetAnyKycStatuses(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetAnyKycStatuses = CanGetAnyKycStatuses()

  case class CanCreateDirectDebitAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateDirectDebitAtOneBank = CanCreateDirectDebitAtOneBank()
  
  case class CanCreateStandingOrderAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateStandingOrderAtOneBank = CanCreateStandingOrderAtOneBank()

  case class CanCreateCustomerAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateCustomerAttributeAtOneBank = CanCreateCustomerAttributeAtOneBank()

  case class CanUpdateCustomerAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateCustomerAttributeAtOneBank = CanUpdateCustomerAttributeAtOneBank()

  case class CanDeleteCustomerAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteCustomerAttributeAtOneBank = CanDeleteCustomerAttributeAtOneBank()

  case class CanGetCustomerAttributesAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetCustomerAttributesAtOneBank = CanGetCustomerAttributesAtOneBank()

  case class CanGetCustomerAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetCustomerAttributeAtOneBank = CanGetCustomerAttributeAtOneBank()

  case class CanCreateTransactionAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateTransactionAttributeAtOneBank = CanCreateTransactionAttributeAtOneBank()

  case class CanUpdateTransactionAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateTransactionAttributeAtOneBank = CanUpdateTransactionAttributeAtOneBank()

  case class CanDeleteTransactionAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteTransactionAttributeAtOneBank = CanDeleteTransactionAttributeAtOneBank()

  case class CanGetTransactionAttributesAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetTransactionAttributesAtOneBank = CanGetTransactionAttributesAtOneBank()

  case class CanGetTransactionAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetTransactionAttributeAtOneBank = CanGetTransactionAttributeAtOneBank()

  case class CanCreateTransactionRequestAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateTransactionRequestAttributeAtOneBank = CanCreateTransactionRequestAttributeAtOneBank()

  case class CanUpdateTransactionRequestAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateTransactionRequestAttributeAtOneBank = CanUpdateTransactionRequestAttributeAtOneBank()

  case class CanDeleteTransactionRequestAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteTransactionRequestAttributeAtOneBank = CanDeleteTransactionRequestAttributeAtOneBank()

  case class CanGetTransactionRequestAttributesAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetTransactionRequestAttributesAtOneBank = CanGetTransactionRequestAttributesAtOneBank()

  case class CanGetTransactionRequestAttributeAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetTransactionRequestAttributeAtOneBank = CanGetTransactionRequestAttributeAtOneBank()

  case class CanGetDoubleEntryTransactionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetDoubleEntryTransactionAtOneBank = CanGetDoubleEntryTransactionAtOneBank()

  case class CanReadResourceDoc(requiresBankId: Boolean = false) extends ApiRole
  lazy val canReadResourceDoc = CanReadResourceDoc()
  
  case class CanReadGlossary(requiresBankId: Boolean = false) extends ApiRole
  lazy val canReadGlossary = CanReadGlossary()

  case class CanCreateCustomerAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateCustomerAttributeDefinitionAtOneBank = CanCreateCustomerAttributeDefinitionAtOneBank()
  
  case class CanDeleteCustomerAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteCustomerAttributeDefinitionAtOneBank = CanDeleteCustomerAttributeDefinitionAtOneBank()
  
  case class CanGetCustomerAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetCustomerAttributeDefinitionAtOneBank = CanGetCustomerAttributeDefinitionAtOneBank()
  
  case class CanCreateAccountAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateAccountAttributeDefinitionAtOneBank = CanCreateAccountAttributeDefinitionAtOneBank() 
  
  case class CanDeleteAccountAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteAccountAttributeDefinitionAtOneBank = CanDeleteAccountAttributeDefinitionAtOneBank()
  
  case class CanGetAccountAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetAccountAttributeDefinitionAtOneBank = CanGetAccountAttributeDefinitionAtOneBank() 
  
  case class CanDeleteProductAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteProductAttributeDefinitionAtOneBank = CanDeleteProductAttributeDefinitionAtOneBank() 
  
  case class CanGetProductAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetProductAttributeDefinitionAtOneBank = CanGetProductAttributeDefinitionAtOneBank()
  
  case class CanCreateProductAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateProductAttributeDefinitionAtOneBank = CanCreateProductAttributeDefinitionAtOneBank()
  
  case class CanCreateTransactionAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateTransactionAttributeDefinitionAtOneBank = CanCreateTransactionAttributeDefinitionAtOneBank()
  
  case class CanDeleteTransactionAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteTransactionAttributeDefinitionAtOneBank = CanDeleteTransactionAttributeDefinitionAtOneBank()
  
  case class CanGetTransactionAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetTransactionAttributeDefinitionAtOneBank = CanGetTransactionAttributeDefinitionAtOneBank() 
  
  case class CanCreateTransactionRequestAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateTransactionRequestAttributeDefinitionAtOneBank = CanCreateTransactionRequestAttributeDefinitionAtOneBank()

  case class CanDeleteTransactionRequestAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteTransactionRequestAttributeDefinitionAtOneBank = CanDeleteTransactionRequestAttributeDefinitionAtOneBank()

  case class CanGetTransactionRequestAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetTransactionRequestAttributeDefinitionAtOneBank = CanGetTransactionRequestAttributeDefinitionAtOneBank()

  case class CanGetCardAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetCardAttributeDefinitionAtOneBank = CanGetCardAttributeDefinitionAtOneBank()

  case class CanDeleteCardAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteCardAttributeDefinitionAtOneBank = CanDeleteCardAttributeDefinitionAtOneBank()

  case class CanCreateCardAttributeDefinitionAtOneBank(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateCardAttributeDefinitionAtOneBank = CanCreateCardAttributeDefinitionAtOneBank()
  
  case class CanDeleteTransactionCascade(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteTransactionCascade = CanDeleteTransactionCascade()
  
  case class CanDeleteAccountCascade(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteAccountCascade = CanDeleteAccountCascade()
  
  case class CanDeleteProductCascade(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteProductCascade = CanDeleteProductCascade()

  case class CanGetConnectorEndpoint(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetConnectorEndpoint = CanGetConnectorEndpoint()

  case class CanCreateJsonSchemaValidation(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateJsonSchemaValidation = CanCreateJsonSchemaValidation()

  case class CanUpdateJsonSchemaValidation(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUpdateJsonSchemaValidation = CanUpdateJsonSchemaValidation()

  case class CanDeleteJsonSchemaValidation(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteJsonSchemaValidation = CanDeleteJsonSchemaValidation()

  case class CanGetJsonSchemaValidation(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetJsonSchemaValidation = CanGetJsonSchemaValidation()

  case class CanCreateAuthenticationTypeValidation(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateAuthenticationTypeValidation = CanCreateAuthenticationTypeValidation()

  case class CanUpdateAuthenticationTypeValidation(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUpdateAuthenticationTypeValidation = CanUpdateAuthenticationTypeValidation()

  case class CanDeleteAuthenticationValidation(requiresBankId: Boolean = false) extends ApiRole
  lazy val canDeleteAuthenticationValidation = CanDeleteAuthenticationValidation()

  case class CanGetAuthenticationTypeValidation(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetAuthenticationTypeValidation = CanGetAuthenticationTypeValidation()

  private val dynamicApiRoles = new ConcurrentHashMap[String, ApiRole]

  private case class DynamicApiRole(role: String, requiresBankId: Boolean = false) extends ApiRole{
    override def toString(): String = role
  }

  def getOrCreateDynamicApiRole(roleName: String, requiresBankId: Boolean = false): ApiRole = {
    dynamicApiRoles.computeIfAbsent(roleName, _ => DynamicApiRole(roleName, requiresBankId))
  }
  def removeDynamicApiRole(roleName: String): ApiRole = {
    dynamicApiRoles.remove(roleName)
  }

  private val roles = {
    val list = ReflectUtils.getFieldsNameToValue[ApiRole](this).values.toList
    val duplicatedRoleName = list.groupBy(_.toString()).filter(_._2.size > 1).map(_._1)
    assume(duplicatedRoleName.isEmpty, s"Duplicated role: ${duplicatedRoleName.mkString(", ")}")
    list
  }

  lazy val rolesMappedToClasses = roles.map(_.getClass)

  def valueOf(value: String): ApiRole = {
    roles.find(_.toString == value) match {
      case Some(x) => x // We find exactly one Role
      case _ if dynamicApiRoles.containsKey(value) => dynamicApiRoles.get(value)
      case _ if DynamicEntityHelper.dynamicEntityRoles.contains(value) ||
                DynamicEndpointHelper.allDynamicEndpointRoles.exists(_.toString() == value)
                =>
        getOrCreateDynamicApiRole(value)
      case _ => throw new IllegalArgumentException("Incorrect ApiRole value: " + value) // There is no Role
    }
  }

  def availableRoles: List[String] = {
    import scala.collection.JavaConverters._
    val dynamicRoles = dynamicApiRoles.keys().asScala.toList
    dynamicRoles ::: roles.map(_.toString)
  }

}

object Util {
  
  def checkWrongDefinedNames: List[List[Unit]] = {
    import scala.meta._
    val source: Source = new java.io.File("src/main/scala/code/api/util/ApiRole.scala").parse[Source].get

    val allowedPrefixes = 
      List(
        "CanCreate",
        "CanGet", 
        "CanUpdate", 
        "CanDelete", 
        "CanMaintain", 
        "CanSearch", 
        "CanEnable", 
        "CanDisable"
      )
    val allowedExistingNames = 
      List(
        "CanQueryOtherUser",
        "CanAddSocialMediaHandle", 
        "CanReadMetrics", 
        "CanUseFirehoseAtAnyBank", 
        "CanReadAggregateMetrics", 
        "CanUnlockUser", 
        "CanReadUserLockedStatus", 
        "CanReadCallLimits", 
        "CanCheckFundsAvailable", 
        "CanRefreshUser", 
        "CanReadFx", 
        "CanSetCallLimits"
      )
    
    val allowed = allowedPrefixes ::: allowedExistingNames

    source.collect {
      case obj: Defn.Object if obj.name.value == "ApiRole" =>
        obj.collect {
          case c: Defn.Class if allowed.exists(i => c.name.syntax.startsWith(i)) == true => 
            // OK
          case c: Defn.Class if allowed.exists(i => c.name.syntax.startsWith(i)) == false => 
            println("INCORRECT - " + c)
        }
    }
  }

  def main (args: Array[String]): Unit = {
    checkWrongDefinedNames
  }

}