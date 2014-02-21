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

import scala.math.BigDecimal
import java.util.Date
import scala.collection.immutable.Set
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JObject
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST.JArray
import net.liftweb.common._
import code.model.dataAccess.{LocalStorage, Account, HostedBank}
import code.model.dataAccess.OBPEnvelope.OBPQueryParam


class Bank(
  val id: String,
  val shortName : String,
  val fullName : String,
  val permalink : String,
  val logoURL : String,
  val website : String
)
{
  def accounts(user : Box[User]) : Box[List[BankAccount]] = {
    user match {
      case Full(u) => {
        nonPublicAccounts(u)
      }
      case _ => {
        publicAccounts
      }
    }
  }

  def publicAccounts : Box[List[BankAccount]] = LocalStorage.getPublicBankAccounts(this)
  def nonPublicAccounts(user : User) : Box[List[BankAccount]] = {
    LocalStorage.getNonPublicBankAccounts(user, id)
  }

  def detailedJson : JObject = {
    ("name" -> shortName) ~
    ("website" -> "") ~
    ("email" -> "")
  }

  def toJson : JObject = {
    ("alias" -> permalink) ~
      ("name" -> shortName) ~
      ("logo" -> "") ~
      ("links" -> linkJson)
  }

  def linkJson : JObject = {
    ("rel" -> "bank") ~
    ("href" -> {"/" + permalink + "/bank"}) ~
    ("method" -> "GET") ~
    ("title" -> {"Get information about the bank identified by " + permalink})
  }
}

object Bank {
  def apply(bankPermalink: String) : Box[Bank] = {
    LocalStorage.getBank(bankPermalink)
  }

  def all : List[Bank] = LocalStorage.allBanks

  def toJson(banks: Seq[Bank]) : JArray =
    banks.map(bank => bank.toJson)

}

class AccountOwner(
  val id : String,
  val name : String
)

class BankAccount(
  val id : String,
  val owners : Set[AccountOwner],
  val accountType : String,
  val balance : BigDecimal,
  val currency : String,
  val name : String,
  val label : String,
  val nationalIdentifier : String,
  val swift_bic : Option[String],
  val iban : Option[String],
  val number : String,
  val bankName : String,
  val bankPermalink : String,
  val permalink : String
) extends Loggable{

  private def viewNotAllowed(view : View ) = Failure("user does not have access to the " + view.name + " view")

  def permittedViews(user: Box[User]) : List[View] = {
    user match {
      case Full(u) => u.permittedViews(this)
      case _ =>{
        logger.info("no user was found in the permittedViews")
        publicViews
      }
    }
  }

  /**
  * @param the view that we want test the access to
  * @param the user that we want to see if he has access to the view or not
  * @return true if the user is allowed to access this view, false otherwise
  */
  def authorizedAccess(view: View, user: Option[User]) : Boolean = {
    if(view.isPublic)
      true
    else
      user match {
        case Some(u) => u.permittedView(view, this)
        case _ => false
      }
  }

  /**
  * @param a user requesting to see the other users' permissions
  * @return a Box of all the users' permissions of this bank account if the user passed as a parameter has access to the owner view (allowed to see this kind of data)
  */
  def permissions(user : User) : Box[List[Permission]] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      LocalStorage.permissions(this)
    else
      Failure("user : " + user.emailAddress + "don't have access to owner view on account " + id, Empty, Empty)
  }

  /**
  * @param user the user requesting to see the other users permissions on this account
  * @param otherUserProvider the authentication provider of the user whose permissions will be retrieved
  * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) whose permissions will be retrieved
  * @return a Box of the user permissions of this bank account if the user passed as a parameter has access to the owner view (allowed to see this kind of data)
  */
  def permission(user : User, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Permission] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        u <- User.findByProviderId(otherUserProvider, otherUserIdGivenByProvider)
        p <- LocalStorage.permission(this, u)
        } yield p
    else
      Failure("user : " + user.emailAddress + "don't have access to owner view on account " + id, Empty, Empty)
  }

  /**
  * @param user the user that wants to grant another user access to a view on this account
  * @param the id of the view that we want to grant access
  * @param otherUserProvider the authentication provider of the user to whom access to the view will be granted
  * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to the view will be granted
  * @return a Full(true) if everything is okay, a Failure otherwise
  */
  def addPermission(user : User, viewId : String, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Boolean] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        view <- View.fromUrl(viewId, this) //check if the viewId corresponds to a view
        otherUser <- User.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        isSaved <- LocalStorage.addPermission(id, view, otherUser) ?~ "could not save the privilege"
      } yield isSaved
    else
      Failure("user : " + user.emailAddress + "don't have access to owner view on account " + id, Empty, Empty)
  }

  /**
  * @param user the user that wants to grant another user access to a several views on this account
  * @param the list of views ids that we want to grant access to
  * @param otherUserProvider the authentication provider of the user to whom access to the views will be granted
  * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to the views will be granted
  * @return a the list of the granted views if everything is okay, a Failure otherwise
  */
  def addPermissions(user : User, viewIds : List[String], otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[List[View]] = {
    //we try to get all the views that correspond to that list of view ids
    lazy val viewBoxes = viewIds.map(id => View.fromUrl(id, this))
    //we see if the the is Failures
    lazy val failureList = viewBoxes.collect(v => {
      v match {
        case Empty => Empty
        case x : Failure => x
      }
    })

    lazy val viewsFormIds : Box[List[View]] =
      //if no failures then we return the Full views
      if(failureList.isEmpty)
        Full(viewBoxes.flatten)
      else
        //we return just the first failure
        failureList.head

    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        otherUser <- User.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        views <- viewsFormIds
        grantedViews <- LocalStorage.addPermissions(id, views, otherUser) ?~ "could not save the privilege"
      } yield views
    else
      Failure("user : " + user.emailAddress + "don't have access to owner view on account " + id, Empty, Empty)
  }

  /**
  * @param user the user that wants to revoke another user's access to a view on this account
  * @param the id of the view that we want to revoke access
  * @param otherUserProvider the authentication provider of the user to whom access to the view will be revoked
  * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to the view will be revoked
  * @return a Full(true) if everything is okay, a Failure otherwise
  */
  def revokePermission(user : User, viewId : String, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Boolean] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        view <- View.fromUrl(viewId, this) //check if the viewId corresponds to a view
        otherUser <- User.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        isRevoked <- LocalStorage.revokePermission(id, view, otherUser) ?~ "could not revoke the privilege"
      } yield isRevoked
    else
      Failure("user : " + user.emailAddress + " don't have access to owner view on account " + id, Empty, Empty)
  }

  /**
  *
  * @param user the user that wants to revoke another user's access to all views on this account
  * @param otherUserProvider the authentication provider of the user to whom access to all views will be revoked
  * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to all views will be revoked
  * @return a Full(true) if everything is okay, a Failure otherwise
  */

  def revokeAllPermissions(user : User, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Boolean] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        otherUser <- User.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        isRevoked <- LocalStorage.revokeAllPermission(id, otherUser)
      } yield isRevoked
    else
      Failure("user : " + user.emailAddress + " don't have access to owner view on account " + id, Empty, Empty)
  }

  def views(user : User) : Box[List[View]] = {
    //check if the user have access to the owner view in this the account
    if(user.ownerAccess(this))
      for{
        isRevoked <- LocalStorage.views(id) ?~ "could not get the views"
      } yield isRevoked
    else
      Failure("user : " + user.emailAddress + " don't have access to owner view on account " + id, Empty, Empty)
  }

  def createView(v: ViewCreationJSON): Box[View] =
    LocalStorage.createView(this, v)

  def removeView(viewId: String) : Box[Unit] =
    LocalStorage.removeView(viewId, this)

  def publicViews : List[View] =
    LocalStorage.publicViews(id).getOrElse(Nil)

  def moderatedTransaction(id: String, view: View, user: Box[User]) : Box[ModeratedTransaction] = {
    if(authorizedAccess(view, user))
      LocalStorage.getModeratedTransaction(id, bankPermalink, permalink)(view.moderate)
    else
      viewNotAllowed(view)
  }

  def getModeratedTransactions(user : Box[User], view : View, queryParams: OBPQueryParam*): Box[List[ModeratedTransaction]] = {
    if(authorizedAccess(view, user))
      LocalStorage.getModeratedTransactions(permalink, bankPermalink, queryParams: _*)(view.moderate)
    else
      viewNotAllowed(view)
  }

  def moderatedBankAccount(view: View, user: Box[User]) : Box[ModeratedBankAccount] = {
    if(authorizedAccess(view, user))
      //implicit conversion from option to box
      view.moderate(this)
    else
      viewNotAllowed(view)
  }

  /**
  * @param the view that we will use to get the ModeratedOtherBankAccount list
  * @param the user that want access to the ModeratedOtherBankAccount list
  * @return a Box of a list ModeratedOtherBankAccounts, it the bank
  *  accounts that have at least one transaction in common with this bank account
  */
  def moderatedOtherBankAccounts(view : View, user : Box[User]) : Box[List[ModeratedOtherBankAccount]] = {
    if(authorizedAccess(view, user))
      LocalStorage.getModeratedOtherBankAccounts(id)(view.moderate)
    else
      viewNotAllowed(view)
  }
  /**
  * @param the ID of the other bank account that the user want have access
  * @param the view that we will use to get the ModeratedOtherBankAccount
  * @param the user that want access to the otherBankAccounts list
  * @return a Box of a ModeratedOtherBankAccounts, it a bank
  *  account that have at least one transaction in common with this bank account
  */
  def moderatedOtherBankAccount(otherAccountID : String, view : View, user : Box[User]) : Box[ModeratedOtherBankAccount] =
    if(authorizedAccess(view, user))
      LocalStorage.getModeratedOtherBankAccount(id, otherAccountID)(view.moderate)
    else
      viewNotAllowed(view)

  def overviewJson(user: Box[User]): JObject = {
    val views = permittedViews(user)
    ("number" -> number) ~
    ("account_alias" -> label) ~
    ("owner_description" -> "") ~
    ("views_available" -> views.map(view => view.toJson)) ~
    View.linksJson(views, permalink, bankPermalink)
  }
}

object BankAccount {
  def apply(bankpermalink: String, bankAccountPermalink: String) : Box[BankAccount] = {
    LocalStorage.getBankAccount(bankpermalink, bankAccountPermalink)
  }

  def publicAccounts : List[BankAccount] = {
    LocalStorage.getAllPublicAccounts()
  }
}

class OtherBankAccount(
  val id : String,
  val label : String,
  val nationalIdentifier : String,
  //the bank international identifier
  val swift_bic : Option[String],
  //the international account identifier
  val iban : Option[String],
  val number : String,
  val bankName : String,
  val metadata : OtherBankAccountMetadata,
  val kind : String
)

class Transaction(
  //A universally unique id
  val uuid : String,
  //The bank's id for the transaction
  val id : String,
  val thisAccount : BankAccount,
  val otherAccount : OtherBankAccount,
  val metadata : TransactionMetadata,
  //E.g. cash withdrawal, electronic payment, etc.
  val transactionType : String,
  val amount : BigDecimal,
  //ISO 4217, e.g. EUR, GBP, USD, etc.
  val currency : String,
  // Bank provided label
  val description : Option[String],
  // The date the transaction was initiated
  val startDate : Date,
  // The date when the money finished changing hands
  val finishDate : Date,
  //the new balance for the bank account
  val balance :  BigDecimal
)