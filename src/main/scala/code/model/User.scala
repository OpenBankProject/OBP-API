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

import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST.JObject
import code.model.dataAccess.LocalStorage
import net.liftweb.common.Box

trait User {
  def id_ : String
  def provider : String
  def emailAddress : String
  def theFirstName : String
  def theLastName : String

  def permittedViews(bankAccount: BankAccount) : List[View] =
    LocalStorage.permittedViews(this, bankAccount)

  def views: List[View]
  def permittedView(v: View, b: BankAccount): Boolean =
    views.contains(v)

  def ownerAccess(bankAccount: BankAccount) : Boolean =
    permittedViews(bankAccount).exists(v => v.permalink=="owner")

  /**
  * @return the bank accounts where the user has at least access to a non public view (is_public==false)
  */
  def nonPublicAccounts : Box[List[BankAccount]] = LocalStorage.getNonPublicBankAccounts(this)

  def toJson : JObject =
    ("id" -> id_) ~
    ("provider" -> provider) ~
    ("display_name" -> {theFirstName + " " + theLastName})
}

object User {
  def findById(id : String) : Box[User] =
    LocalStorage.getUser(id)
}