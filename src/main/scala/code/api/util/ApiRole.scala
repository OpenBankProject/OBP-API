package code.api.util

sealed trait ApiRole{
  val requiresBankId: Boolean
}

object ApiRole {

  // TODO Is there a better way to define these. i.e. do we need to define the role in three places below?

  case object CanSearchAllTransactions extends ApiRole{
    val requiresBankId = false
  }
  case object CanSearchAllAccounts extends ApiRole{
    val requiresBankId = false
  }
  case object CanQueryOtherUser extends ApiRole{
    val requiresBankId = false
  }
  case object CanSearchWarehouse extends ApiRole{
    val requiresBankId = false
  }
  case object CanSearchMetrics extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateCustomer extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateCustomerAtAnyBank extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateUserCustomerLink extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateUserCustomerLinkAtAnyBank extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateAccount extends ApiRole{
    val requiresBankId = true
  }
  case object CanGetAnyUser extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateAnyTransactionRequest extends ApiRole{
    val requiresBankId = true
  }
  case object CanAddSocialMediaHandle extends ApiRole{
    val requiresBankId = true
  }
  case object CanGetSocialMediaHandles extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateSandbox extends ApiRole{
    val requiresBankId = false
  }
  case object CanGetEntitlementsForAnyUserAtOneBank extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateEntitlementAtOneBank extends ApiRole{
    val requiresBankId = true
  }
  case object CanDeleteEntitlementAtOneBank extends ApiRole{
    val requiresBankId = true
  }
  case object CanGetEntitlementsForAnyUserAtAnyBank extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateEntitlementAtAnyBank extends ApiRole{
    val requiresBankId = false
  }
  case object CanDeleteEntitlementAtAnyBank extends ApiRole{
    val requiresBankId = false
  }
  case object CanGetConsumers extends ApiRole{
    val requiresBankId = false
  }
  case object CanDisableConsumers extends ApiRole{
    val requiresBankId = false
  }
  case object CanEnableConsumers extends ApiRole{
    val requiresBankId = false
  }
  case object CanUpdateConsumerRedirectUrl extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateConsumer extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateTransactionType extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateCardsForBank extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateBranch extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateBranchAtAnyBank extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateAtm extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateAtmAtAnyBank extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateProduct extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateProductAtAnyBank extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateFxRate extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateFxRateAtAnyBank extends ApiRole{
    val requiresBankId = false
  }
  case object CanCreateBank extends ApiRole{
    val requiresBankId = false
  }
  case object CanReadMetrics extends ApiRole{
    val requiresBankId = false
  }
  case object CanGetConfig extends ApiRole{
    val requiresBankId = false
  }
  case object CanGetConnectorMetrics extends ApiRole{
    val requiresBankId = false
  }

  private val roles = CanSearchAllTransactions ::
    CanSearchAllAccounts ::
    CanQueryOtherUser ::
    CanSearchWarehouse ::
    CanSearchMetrics ::
    CanCreateCustomer ::
    CanCreateAccount ::
    CanGetAnyUser ::
    CanCreateAnyTransactionRequest ::
    CanAddSocialMediaHandle ::
    CanGetSocialMediaHandles ::
    CanCreateSandbox ::
    CanGetEntitlementsForAnyUserAtOneBank ::
    CanCreateEntitlementAtOneBank ::
    CanDeleteEntitlementAtOneBank ::
    CanGetEntitlementsForAnyUserAtAnyBank ::
    CanCreateEntitlementAtAnyBank ::
    CanDeleteEntitlementAtAnyBank ::
    CanGetConsumers ::
    CanDisableConsumers ::
    CanEnableConsumers ::
    CanUpdateConsumerRedirectUrl ::
    CanCreateConsumer ::
    CanCreateTransactionType::
    CanCreateCardsForBank ::
    CanCreateUserCustomerLink ::
    CanCreateBranch ::
    CanCreateBranchAtAnyBank ::
    CanCreateAtm ::
    CanCreateAtmAtAnyBank ::
    CanCreateProduct ::
    CanCreateProductAtAnyBank ::
    CanCreateFxRate ::
    CanCreateFxRateAtAnyBank ::
    CanCreateBank ::
    CanReadMetrics ::
    CanGetConfig ::
    CanGetConnectorMetrics ::
    Nil

  def valueOf(value: String): ApiRole = {
    roles.filter(_.toString == value) match {
      case x :: Nil => x // We find exactly one Role
      case _ :: _ => throw new Exception("Duplicated role") // We find more than one Role
      case _ => throw new IllegalArgumentException() // There is no Role
    }
  }

  def availableRoles: List[String] = roles.map(_.toString)

}