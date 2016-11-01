package code.api.util

sealed trait ApiRole{
  val requiresBankId: Boolean
}

object ApiRole {

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
    val requiresBankId = true
  }
  case object CanSearchMetrics extends ApiRole{
    val requiresBankId = true
  }
  case object CanCreateCustomer extends ApiRole{
    val requiresBankId = true
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
  case object CanGetEntitlementsForAnyUserAtAnyBank extends ApiRole{
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

  def valueOf(value: String): ApiRole = value match {
    case "CanSearchAllTransactions" => CanSearchAllTransactions
    case "CanSearchAllAccounts" => CanSearchAllAccounts
    case "CanQueryOtherUser" => CanQueryOtherUser
    case "CanSearchWarehouse" => CanSearchWarehouse
    case "CanSearchMetrics" => CanSearchMetrics
    case "CanCreateCustomer" => CanCreateCustomer
    case "CanCreateAccount" => CanCreateAccount
    case "CanGetAnyUser" => CanGetAnyUser
    case "CanCreateAnyTransactionRequest" => CanCreateAnyTransactionRequest
    case "CanAddSocialMediaHandle" => CanAddSocialMediaHandle
    case "CanGetSocialMediaHandles" => CanGetSocialMediaHandles
    case "CanCreateSandbox" => CanCreateSandbox
    case "CanGetEntitlementsForAnyUserAtOneBank" => CanGetEntitlementsForAnyUserAtOneBank
    case "CanGetEntitlementsForAnyUserAtAnyBank" => CanGetEntitlementsForAnyUserAtAnyBank
    case "CanGetConsumers" => CanGetConsumers
    case "CanDisableConsumers" => CanDisableConsumers
    case "CanEnableConsumers" => CanEnableConsumers
    case _ => throw new IllegalArgumentException()
  }

  val availableRoles = "CanSearchAllTransactions" ::
                      "CanSearchAllAccounts" ::
                      "CanQueryOtherUser" ::
                      "CanSearchWarehouse" ::
                      "CanSearchMetrics" ::
                      "CanCreateCustomer" ::
                      "CanCreateAccount" ::
                      "CanGetAnyUser" ::
                      "CanCreateAnyTransactionRequest" ::
                      "CanAddSocialMediaHandle" ::
                      "CanGetSocialMediaHandles" ::
                      "CanCreateSandbox" ::
                      "CanGetEntitlementsForAnyUserAtOneBank" ::
                      "CanGetEntitlementsForAnyUserAtAnyBank" ::
                      "CanGetConsumers" ::
                      "CanDisableConsumers" ::
                      "CanEnableConsumers" ::
                       Nil

}