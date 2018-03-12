/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
TESOBE Ltd
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
import code.api.util.APIUtil
import code.views.Views
import code.entitlement.Entitlement
import code.model.dataAccess.{MappedAccountView, ResourceUser, ViewImpl, ViewPrivileges}
import code.users.Users
import code.util.Helper.MdcLoggable
import net.liftweb.mapper.By

case class UserId(val value : Long) {
  override def toString = value.toString
}


// TODO Document clearly the difference between this and AuthUser

trait User extends MdcLoggable {

  def resourceUserId : UserId
  def userId: String

  def idGivenByProvider: String
  def provider : String
  def emailAddress : String
  def name : String
  
  /**
    * Also see @`hasViewAccess(view: View): Boolean`
    * Here only need the bankIdAccountId, it will search for the `owner` view internally.
    * And than call the `hasViewAccess(view: View): Boolean` method
    * @return if the user have the account owner view access return true, otherwise false.
    */
  final def hasOwnerViewAccess(bankIdAccountId: BankIdAccountId): Boolean ={
    //find the bankAccount owner view object
    val viewImplBox = Views.views.vend.view(ViewId("owner"), bankIdAccountId)
    viewImplBox match {
      case Full(v) => hasViewAccess(v)
      case _ => 
        logger.warn(s"It is strange. This bankAccount(${bankIdAccountId.bankId}, ${bankIdAccountId.accountId}) do not have `owner` view.")
        return false
    }
  }
  /**
    * Also see @`hasOwnerViewAccess(bankIdAccountId: BankIdAccountId): Boolean`
    * Here we need the `view` object, so we need check the exsitence before call this method.
    * In the method, we will check if the view and user have the record in ViewPrileges table.
    * If it is, return true, otherwise false.
    * @param view the view object, need check the exsitence before calling the method
    * @return if has the input view access, return true, otherwise false.
    */
  final def hasViewAccess(view: View): Boolean ={
    val accountViewBox = MappedAccountView.find(ViewIdBankIdAccountId(view.viewId, view.bankId, view.accountId))
    accountViewBox match {
      case Full(accountView) => !(ViewPrivileges.count(By(ViewPrivileges.user, this.resourceUserId.value), By(ViewPrivileges.view, accountView.id.get)) == 0)
      case _ => false
    }
  }
  
  def assignedEntitlements : List[Entitlement] = {
    Entitlement.entitlement.vend.getEntitlementsByUserId(userId) match {
      case Full(l) => l.sortWith(_.roleName < _.roleName)
      case _ => List()
    }
  }

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def toJson : JObject =
    ("id" -> idGivenByProvider) ~
    ("provider" -> provider) ~
    ("display_name" -> name)
}

object User {
  def findByResourceUserId(id : Long) : Box[User] =
    Users.users.vend.getUserByResourceUserId(id)

  def findResourceUserByResourceUserId(id : Long) : Box[ResourceUser] =
    Users.users.vend.getResourceUserByResourceUserId(id)

  def findByProviderId(provider : String, idGivenByProvider : String) =
    //if you change this, think about backwards compatibility! All existing
    //versions of the API return this failure message, so if you change it, make sure
    //that all stable versions retain the same behavior
    Users.users.vend.getUserByProviderId(provider, idGivenByProvider) ~> UserNotFound(provider, idGivenByProvider)

  def findByUserId(userId : String) = {
    val usr = Users.users.vend.getUserByUserId(userId)
    usr
  }

  def findByUserName(userName: String) = {
    Users.users.vend.getUserByUserName(userName)
  }

  def findByEmail(email: String) = {
    Users.users.vend.getUserByEmail(email) match {
      case Full(list) => list
      case _ => List()
    }
  }

  def findAll() = {
    Users.users.vend.getAllUsers() match {
      case Full(list) => list
      case _          => List()
    }
  }

  def createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) = {
     Users.users.vend.createResourceUser(provider, providerId, name, email, userId)
  }

  def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) = {
    Users.users.vend.createUnsavedResourceUser(provider, providerId, name, email, userId)
  }

  def saveResourceUser(ru: ResourceUser) = {
    Users.users.vend.saveResourceUser(ru)
  }

  //def bulkDeleteAllResourceUsers(): Box[Boolean] = {
  //  Users.users.vend.bulkDeleteAllResourceUsers()
  //}
}
