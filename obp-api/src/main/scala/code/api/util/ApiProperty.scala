package code.api.util

import code.api.util.APIUtil.getPropsValue
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full

object ApiProperty extends MdcLoggable {
  val requireScopesForAllRoles = {
    val actualName = getPropsValue("require_scopes_for_all_roles")
    val deprecatedName = getPropsValue("require_scopes")
    (actualName, deprecatedName) match {
      case (Full(actual), Full(deprecated)) => // Both properties are defined. Use actual one and log warning. {true/false}
        logger.warn("The props file has defined actual property name require_scopes_for_all_roles as well as deprecated require_scopes. The deprecated one is ignored!")
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
