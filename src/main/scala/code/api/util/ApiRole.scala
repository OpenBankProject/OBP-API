package code.api.util

sealed trait ApiRole{
  val requiresBankId: Boolean
  override def toString() = getClass().getSimpleName
}

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

  case class CanCreateCustomerAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateCustomerAtAnyBank = CanCreateCustomerAtAnyBank()

  case class CanCreateUserCustomerLink(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateUserCustomerLink = CanCreateUserCustomerLink()

  case class CanCreateUserCustomerLinkAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateUserCustomerLinkAtAnyBank = CanCreateUserCustomerLinkAtAnyBank()

  case class CanCreateAccount(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateAccount = CanCreateAccount()

  case class CanGetAnyUser (requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetAnyUser = CanGetAnyUser()

  case class CanCreateAnyTransactionRequest(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateAnyTransactionRequest = CanCreateAnyTransactionRequest()

  case class CanAddSocialMediaHandle(requiresBankId: Boolean = true) extends ApiRole
  lazy val canAddSocialMediaHandle = CanAddSocialMediaHandle()

  case class CanGetSocialMediaHandles(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetSocialMediaHandles = CanGetSocialMediaHandles()

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

  case class CanCreateBranch(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateBranch = CanCreateBranch()

  case class CanCreateBranchAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateBranchAtAnyBank = CanCreateBranchAtAnyBank()

  case class CanCreateAtm(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateAtm = CanCreateAtm()

  case class CanCreateAtmAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateAtmAtAnyBank = CanCreateAtmAtAnyBank()

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

  case class CanReadMetrics (requiresBankId: Boolean = false) extends ApiRole
  lazy val canReadMetrics = CanReadMetrics()

  case class CanGetConfig(requiresBankId: Boolean = false) extends ApiRole
  lazy val canGetConfig = CanGetConfig()

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

  case class CanUseFirehoseAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canUseFirehoseAtAnyBank = CanUseFirehoseAtAnyBank()

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
  
  case class CanGetTaxResidence(requiresBankId: Boolean = true) extends ApiRole
  lazy val canGetTaxResidence = CanGetTaxResidence()

  case class CanCreateTaxResidence(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateTaxResidence = CanCreateTaxResidence()

  case class CanDeleteTaxResidence(requiresBankId: Boolean = true) extends ApiRole
  lazy val canDeleteTaxResidence = CanDeleteTaxResidence()

  case class CanRefreshUser(requiresBankId: Boolean = false) extends ApiRole
  lazy val canRefreshUser = CanRefreshUser()
  
  private val roles =
      canSearchAllTransactions ::
      canSearchAllAccounts ::
      canQueryOtherUser ::
      canSearchWarehouse ::
      canSearchWarehouseStatistics ::
      canSearchMetrics ::
      canGetCustomer ::
      canCreateCustomer ::
      canCreateCustomerAtAnyBank ::
      canCreateUserCustomerLink ::
      canCreateUserCustomerLinkAtAnyBank ::
      canCreateAccount ::
      canGetAnyUser ::
      canCreateAnyTransactionRequest ::
      canAddSocialMediaHandle ::
      canGetSocialMediaHandles ::
      canCreateSandbox ::
      canGetEntitlementsForAnyUserAtOneBank ::
      canCreateEntitlementAtOneBank ::
      canDeleteEntitlementAtOneBank ::
      canGetEntitlementsForAnyUserAtAnyBank ::
      canCreateEntitlementAtAnyBank ::
      canDeleteEntitlementAtAnyBank ::
      canGetConsumers ::
      canDisableConsumers ::
      canEnableConsumers ::
      canUpdateConsumerRedirectUrl ::
      canCreateConsumer ::
      canCreateTransactionType::
      canCreateCardsForBank ::
      canCreateBranch ::
      canCreateBranchAtAnyBank ::
      canCreateAtm ::
      canCreateAtmAtAnyBank ::
      canCreateProduct ::
      canCreateProductAtAnyBank ::
      canCreateFxRate ::
      canCreateFxRateAtAnyBank ::
      canCreateBank ::
      canReadMetrics ::
      canGetConfig ::
      canGetConnectorMetrics ::
      canGetOtherAccountsAtBank ::
      canDeleteEntitlementRequestsAtOneBank ::
      canDeleteEntitlementRequestsAtAnyBank ::
      canGetEntitlementRequestsAtOneBank ::
      canGetEntitlementRequestsAtAnyBank ::
      canUseFirehoseAtAnyBank ::
      canReadAggregateMetrics ::
      canCreateScopeAtOneBank ::
      canCreateScopeAtAnyBank ::
      canDeleteScopeAtAnyBank ::
      canDeleteScopeAtOneBank ::
      canUnlockUser ::
      canSetCallLimits ::
      canReadCallLimits ::
      canReadUserLockedStatus ::
      canCheckFundsAvailable ::
      canCreateWebhook ::
      canGetWebhooks ::
      canUpdateWebhook ::
      canUpdateUserAuthContext ::
      canGetUserAuthContext ::
      canDeleteUserAuthContext ::
      canCreateUserAuthContext ::
      canGetTaxResidence ::
      canCreateTaxResidence ::
      canDeleteTaxResidence ::
      canRefreshUser ::
      Nil

  lazy val rolesMappedToClasses = roles.map(_.getClass)

  def valueOf(value: String): ApiRole = {
    roles.filter(_.toString == value) match {
      case x :: Nil => x // We find exactly one Role
      case x :: _ => throw new Exception("Duplicated role: " + x) // We find more than one Role
      case _ => throw new IllegalArgumentException("Incorrect ApiRole value: " + value) // There is no Role
    }
  }

  def availableRoles: List[String] = roles.map(_.toString)

}