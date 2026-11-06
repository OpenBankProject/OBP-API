/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
object ApiPropsWithAlias {
  import HelperFunctions._
  def requireScopesForAllRoles = getValueByNameOrAliasAsBoolean(
    name="require_scopes_for_all_roles", 
    alias="require_scopes", 
    defaultValue="false")
  def migrationScriptsEnabled = getValueByNameOrAliasAsBoolean(
    name="migration_scripts.enabled",
    alias="migration_scripts.execute",
    defaultValue="false")
  def allowAccountFirehose = getValueByNameOrAliasAsBoolean(
    name="allow_account_firehose",
    alias="allow_firehose_views",
    defaultValue="false")
  def allowCustomerFirehose = getValueByNameOrAliasAsBoolean(
    name="allow_customer_firehose",
    alias="allow_firehose_views",
    defaultValue="false")
  // TODO Replace all "gateway.token_secret" and "jwt.token_secret" with "jwt_token_secret" props names at sandboxes/scripts
  def jwtTokenSecret = getValueByNameOrAlias(
    name="jwt_token_secret",
    alias="jwt.token_secret",
    defaultValue="Cannot get your at least 256 bit secret")
  def defaultLocale = getValueByNameOrAlias(
    name="default_locale",
    alias="language_tag",
    defaultValue="en_GB")
}

object HelperFunctions extends MdcLoggable {
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
  private[util] def getValueByNameOrAliasAsBoolean(name: String, alias: String, defaultValue: String): Boolean = {
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
  private[util] def getValueByNameOrAlias(name: String, alias: String, defaultValue: String): String = {
    (getPropsValue(name), getPropsValue(alias)) match {
      case (Full(actual), Full(deprecated)) => // Both properties are defined. Use actual one and log warning. {true/false}
        logger.warn(s"The props file has defined actual property $name as well as deprecated $alias. The deprecated one is ignored!")
        actual
      case (Full(actual), _) => // Only actual name of the property is defined. {true/false}
        actual
      case (_, Full(deprecated)) => // Only deprecated name of the property is defined. {true/false}
        logger.warn(s"The props file uses deprecated property $alias. Please use $name instead of it")
        deprecated
      case _ => // Not defined. {false}
        defaultValue
    }
  }
}