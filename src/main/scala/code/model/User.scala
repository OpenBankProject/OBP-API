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
import code.model.dataAccess.{ResourceUser, ViewImpl, ViewPrivileges}
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
  
  final def allViewsUserCanAccess: List[View] ={
    val privateViewsUserCanAccess = ViewPrivileges.findAll(By(ViewPrivileges.user, this.resourceUserId.value)).map(_.view.obj.toList).flatten
    val publicViewsUserCanAccess = if (APIUtil.ALLOW_PUBLIC_VIEWS)
      ViewImpl
        .findAll(By(ViewImpl.isPublic_, true)) // find all the public view in ViewImpl table, it has no relevent with user, all the user can get the public view.
    else
      Nil
    (privateViewsUserCanAccess++publicViewsUserCanAccess).distinct
    
  }
  final def hasOwnerView(bankAccount: BankAccount): Boolean ={
    //find the bankAccount owner view object
    val viewImplBox = ViewImpl.find(ViewId("owner"),BankIdAccountId(bankAccount.bankId, bankAccount.accountId))
    val viewImpl = viewImplBox match {
      case Full(v) => v
      case _ => 
        logger.warn(s"It is strange. This bankAccount(${bankAccount.bankId}, ${bankAccount.accountId}) do not have `owner` view.")
        return false
    }
    
    //check the ViewPrivileges by user and viewImpl
    !(ViewPrivileges.count(By(ViewPrivileges.user, this.resourceUserId.value), By(ViewPrivileges.view, viewImpl.id)) == 0)
  }
  /**
    * The use have viewPrivilege, only check the `ViewPrivileges` table. 
    */
  final def hasViewPrivilege(view: View): Boolean ={
    val viewImpl = view.asInstanceOf[ViewImpl]
    !(ViewPrivileges.count(By(ViewPrivileges.user, this.resourceUserId.value), By(ViewPrivileges.view, viewImpl.id)) == 0)
  }
  
  final def allViewsUserCanAccessForAccount(bankAccount: BankAccount) : List[View] =
    allViewsUserCanAccess.filter(
      view => 
        view.bankId == bankAccount.bankId && 
        view.accountId == bankAccount.accountId
    )
  
  final def canInitiateTransactions(bankAccount: BankAccount) : Box[Unit] ={
    if(allViewsUserCanAccessForAccount(bankAccount).exists(_.canInitiateTransaction)){
      Full()
    }
    else {
      Failure("user doesn't have access to any view that allows initiating transactions")
    }
  }
  
  /**
  * @return the bank accounts where the user has at least access to a Private view (is_public==false)
  */
  def privateAccounts : List[BankAccount] = {
    Views.views.vend.getPrivateBankAccounts(this).flatMap { a =>
      BankAccount(a.bankId, a.accountId)
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
