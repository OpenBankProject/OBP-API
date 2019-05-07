package code.api.util

import code.api.util.APIUtil.getPropsValue
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full

object ApiProperty extends MdcLoggable {
  
  val requireScopesForAllRoles = getValueByNameOrAlias("require_scopes_for_all_roles", "require_scopes")

  private def getValueByNameOrAlias(name: String, alias: String): Boolean = {
    (getPropsValue(name), getPropsValue(alias)) match {
      case (Full(actual), Full(deprecated)) => // Both properties are defined. Use actual one and log warning. {true/false}
        logger.warn(s"The props file has defined actual property name $name as well as deprecated $alias. The deprecated one is ignored!")
        actual.toBoolean
      case (Full(actual), _) => // Only actual name of the property is defined. {true/false}
        actual.toBoolean
      case (_, Full(deprecated)) => // Only deprecated name of the property is defined. {true/false}
        deprecated.toBoolean
      case _ => // Not defined. {false}
        false
    }
  }
}
