package code.api.util

sealed trait ApiRole{
  val requiresBankId: Boolean
  override def toString() = getClass().getSimpleName
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

  case class CanCreateUserCustomerLinkAtAnyBank(requiresBankId: Boolean = false) extends ApiRole
  lazy val canCreateUserCustomerLinkAtAnyBank = CanCreateUserCustomerLinkAtAnyBank()

  case class CanCreateAccount(requiresBankId: Boolean = true) extends ApiRole
  lazy val canCreateAccount = CanCreateAccount()

  case class CanUpdateAccount(requiresBankId: Boolean = true) extends ApiRole
  lazy val canUpdateAccount = CanUpdateAccount()
  
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
      canCreateCustomerAddress ::
      canGetCustomerAddress ::
      canDeleteCustomerAddress ::
      canCreateAccount ::
      canUpdateAccount ::
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
      canDeleteCardsForBank ::
      canUpdateCardsForBank ::
      canGetCardsForBank ::
      canCreateBranch ::
      canCreateBranchAtAnyBank :: 
      canUpdateBranch ::
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
      canGetAccountApplications::
      canUpdateAccountApplications::
      canReadFx::
      canUpdateProductAttribute ::
      canGetProductAttribute ::
      canDeleteProductAttribute ::
      canCreateProductAttribute ::
      canMaintainProductCollection ::
      canDeleteBranchAtAnyBank ::
      canDeleteBranch ::
      canCreateSystemView ::
      canUpdateSystemView ::
      canGetSystemView ::
      canDeleteSystemView ::
      canCreateUserAuthContextUpdate ::
      canUpdateCustomerEmail ::
      canUpdateCustomerMobilePhoneNumber ::
      canUpdateCustomerIdentity ::
      canUpdateCustomerBranch ::
      canUpdateCustomerCreditLimit ::
      canUpdateCustomerCreditRatingAndSource ::
      canUpdateCustomerData ::
      canGetMethodRoutings ::
      canCreateMethodRouting ::
      canUpdateMethodRouting ::
      canDeleteMethodRouting :: 
      canUpdateCustomerNumber ::
      canCreateHistoricalTransaction ::
      canGetWebUiProps ::
      canCreateWebUiProps ::
      canDeleteWebUiProps ::
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