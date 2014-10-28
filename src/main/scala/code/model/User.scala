/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */

package code.model

import code.util.Helper
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST.JObject
import net.liftweb.common.{Box, Failure, Full}
import code.api.UserNotFound
import code.views.Views
import code.bankconnectors.Connector
import code.users.Users

trait User {

  def apiId : String

  def idGivenByProvider: String
  def provider : String
  def emailAddress : String
  //TODO: rename to displayName?
  def name : String

  def permittedViews(bankAccount: BankAccount) : List[View] =
    Views.views.vend.permittedViews(this, bankAccount)

  def canInitiateTransactions(bankAccount: BankAccount) : Box[Unit] ={
    if(permittedViews(bankAccount).exists(_.canInitiateTransaction)){
      Full()
    } 
    else {
      Failure("user don't have access to any view allowing to initiate transactions")
    }
  }
     

  def views: List[View]
  def permittedView(v: View, b: BankAccount): Boolean =
    views.contains(v)

  def ownerAccess(bankAccount: BankAccount): Boolean =
    permittedViews(bankAccount).exists(v => v.viewId==ViewId("owner"))

  /**
  * @return the bank accounts where the user has at least access to a non public view (is_public==false)
  */
  def nonPublicAccounts : Box[List[BankAccount]] = Views.views.vend.getNonPublicBankAccounts(this)

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def toJson : JObject =
    ("id" -> idGivenByProvider) ~
    ("provider" -> provider) ~
    ("display_name" -> name)
}

object User {
  def findByApiId(id : String) : Box[User] =
    Users.users.vend.getUserByApiId(id)

  def findByProviderId(provider : String, idGivenByProvider : String) =
    //if you change this, think about backwards compatibility! All existing
    //versions of the API return this failure message, so if you change it, make sure
    //that all stable versions retain the same behavior
    Users.users.vend.getUserByProviderId(provider, idGivenByProvider) ~> UserNotFound(provider, idGivenByProvider)
}