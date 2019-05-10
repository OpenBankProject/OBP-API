package code.api.util

import code.api.util.APIUtil.getPropsValue
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full

/**
  * Main purpose of this file is to centralize use of a property with two names.
  * Typical user scenario would be:
  * 1. We have the name "require_scopes" of an existing property
  * 2. We introduce a new name "require_scopes_for_all_roles"
  * 3. We have to support all instances with old name in order to allow smooth transition from the old to the new name.
  */
object ApiProperty extends MdcLoggable {
  
  val requireScopesForAllRoles = getValueByNameOrAliasAsBoolean("require_scopes_for_all_roles", "require_scopes", "false")
  

  /**
    * Workflow of get property with boolean value
    * 
                      1st try                              2nd try
     get         +---------------+                    +---------------+
     property    |               |  no match          |               |  no match
    +----------->+     Name      +------------------->+     Alias     +----------+
                 |               |                    |               |          |
                 +-------+-------+                    +-------+-------+          |
                         |                                    |                  |
         true/false      | match                              | match            |
    <--------------------+                                    |                  |
                                                              |                  |
         true/false                                           |                  |
    <---------------------------------------------------------+                  |
                                                                                 |
         false                                                                   |
    <----------------------------------------------------------------------------+
    *
    * @param name The name of a property
    * @param alias The alias of a property
    * @return true/false
    */
  private def getValueByNameOrAliasAsBoolean(name: String, alias: String, defaultValue: String): Boolean = {
    getValueByNameOrAlias(name, alias, defaultValue).toBoolean
  }
  /**
    * Workflow of get property with string value
    *
                      1st try                              2nd try
     get         +---------------+                    +---------------+
     property    |               |  no match          |               |  no match
    +----------->+     Name      +------------------->+     Alias     +----------+
                 |               |                    |               |          |
                 +-------+-------+                    +-------+-------+          |
                         |                                    |                  |
         value           | match                              | match            |
    <--------------------+                                    |                  |
                                                              |                  |
         value                                                |                  |
    <---------------------------------------------------------+                  |
                                                                                 |
         default value                                                           |
    <----------------------------------------------------------------------------+
    *
    * @param name The name of a property
    * @param alias The alias of a property
    * @return value/default value
    */
  private def getValueByNameOrAlias(name: String, alias: String, defaultValue: String): String = {
    (getPropsValue(name), getPropsValue(alias)) match {
      case (Full(actual), Full(deprecated)) => // Both properties are defined. Use actual one and log warning. {true/false}
        logger.warn(s"The props file has defined actual property name $name as well as deprecated $alias. The deprecated one is ignored!")
        actual
      case (Full(actual), _) => // Only actual name of the property is defined. {true/false}
        actual
      case (_, Full(deprecated)) => // Only deprecated name of the property is defined. {true/false}
        deprecated
      case _ => // Not defined. {false}
        defaultValue
    }
  }
}
