package code.api.util

sealed trait ApiRole

object ApiRole {

  case object CanSearchAllTransactions extends ApiRole
  case object CanSearchAllAccounts extends ApiRole
  case object CanQueryOtherUser extends ApiRole
  case object CanSearchWarehouse extends ApiRole
  case object CanSearchMetrics extends ApiRole

  def valueOf(value: String): ApiRole = value match {
    case "CanSearchAllTransactions" => CanSearchAllTransactions
    case "CanSearchAllAccounts"    => CanSearchAllAccounts
    case "CanQueryOtherUser"    => CanQueryOtherUser
    case "CanSearchWarehouse"    => CanSearchWarehouse
    case "CanSearchMetrics"    => CanSearchMetrics
    case _ => throw new IllegalArgumentException()
  }

}