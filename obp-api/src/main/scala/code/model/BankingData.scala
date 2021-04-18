/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.model

import java.util.Date

import code.accountholders.AccountHolders
import code.api.{APIFailureNewStyle, Constant}
import code.api.util.APIUtil.{OBPReturnType, canGrantAccessToViewCommon, canRevokeAccessToViewCommon, fullBoxOrException, unboxFull, unboxFullOrFail}
import code.api.util.ErrorMessages._
import code.api.util._
import code.bankconnectors.{Connector, LocalMappedConnector}
import code.customer.CustomerX
import code.model.dataAccess.MappedBankAccount
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.views.Views
import code.views.system.AccountAccess
import com.openbankproject.commons.model.{AccountId, AccountRouting, Attribute, Bank, BankAccount, BankAccountCommons, BankId, BankIdAccountId, Counterparty, CounterpartyId, CounterpartyTrait, CreateViewJson, Customer, Permission, TransactionId, UpdateViewJSON, User, UserPrimaryKey, View, ViewId, ViewIdBankIdAccountId}
import net.liftweb.common._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{JArray, JObject}

import scala.collection.immutable.{List, Set}
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future

case class BankExtended(bank: Bank) {

  def publicAccounts(publicAccountAccessesForBank: List[AccountAccess]) : List[BankAccount] = {
    publicAccountAccessesForBank
      .map(a=>BankIdAccountId(BankId(a.bank_id.get), AccountId(a.account_id.get))).distinct
      .flatMap(a => BankAccountX(a.bankId, a.accountId))
  }

  // TODO refactor this function to get accounts from list in a single call via connector
  def privateAccounts(privateAccountAccessesAtOneBank : List[AccountAccess]) : List[BankAccount] = {
    privateAccountAccessesAtOneBank
      .map(a=>BankIdAccountId(BankId(a.bank_id.get), AccountId(a.account_id.get))).distinct
      .flatMap(a => BankAccountX(a.bankId, a.accountId))
  }

  def privateAccountsFuture(privateAccountAccessesAtOneBank : List[AccountAccess], callContext: Option[CallContext]): Future[(List[BankAccount], Option[CallContext])] = {
    val accounts: List[BankIdAccountId] = privateAccountAccessesAtOneBank
      .map(a=>BankIdAccountId(BankId(a.bank_id.get), AccountId(a.account_id.get))).distinct
    Connector.connector.vend.getBankAccounts(accounts, callContext) map { i =>
      (unboxFullOrFail(i._1, callContext,s"$BankAccountNotFound", 400 ), i._2)
    }
  }

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def detailedJson : JObject = {
    ("name" -> bank.shortName) ~
      ("website" -> "") ~
      ("email" -> "")
  }

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def toJson : JObject = {
    ("alias" -> bank.bankId.value) ~
      ("name" -> bank.shortName) ~
      ("logo" -> "") ~
      ("links" -> linkJson)
  }

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def linkJson : JObject = {
    ("rel" -> "bank") ~
      ("href" -> {"/" + bank.bankId + "/bank"}) ~
      ("method" -> "GET") ~
      ("title" -> {"Get information about the bank identified by " + bank.bankId})
  }
}

object BankX {

  def apply(bankId: BankId, callContext: Option[CallContext]) : Box[(Bank, Option[CallContext])] = {
    Connector.connector.vend.getBankLegacy(bankId, callContext)
  }

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def toJson(banks: Seq[Bank]) : JArray =
    banks.map(bank => bank.toJson)
}

class AccountOwner(
                    val id : String,
                    val name : String
                  )




/** Internal model of a Bank Account
  * @define accountType The account type aka financial product name. The customer friendly text that identifies the financial product this account is based on, as given by the bank
  * @define accountId An identifier (no spaces, url friendly, should be a UUID) that hides the actual account number (obp identifier)
  * @define number The actual bank account number as given by the bank to the customer
  * @define bankId The short bank identifier that holds this account (url friendly, usually short name of bank with hyphens)
  * @define label A string that helps identify the account to a customer or the public. Can be updated by the account owner. Default would typically include the owner display name (should be legal entity owner) + accountType + few characters of number
  * @define iban The IBAN (could be empty)
  * @define currency The currency (3 letter code)
  * @define balance The current balance on the account
  */

// TODO Add: @define productCode A code (no spaces, url friendly) that identifies the financial product this account is based on.

case class BankAccountExtended(val bankAccount: BankAccount) extends MdcLoggable {

  private val bankId = bankAccount.bankId

  private val accountId = bankAccount.accountId

  //TODO: remove?
  @deprecated("This is not useful anymore, better remove it. ","08-01-2020")
  final def bankName : String =
    Connector.connector.vend.getBankLegacy(bankId, None).map(_._1).map(_.fullName).getOrElse("")
  //TODO: remove?
  @deprecated("This is not useful anymore, better remove it.","08-01-2020")
  final def nationalIdentifier : String =
    Connector.connector.vend.getBankLegacy(bankId, None).map(_._1).map(_.nationalIdentifier).getOrElse("")

  //From V300, used scheme, address
  @deprecated("This should be read from bank object, not call it from here. ","08-01-2020")
  final def bankRoutingScheme : String =
    Connector.connector.vend.getBankLegacy(bankId, None).map(_._1).map(_.bankRoutingScheme).getOrElse("")
  @deprecated("This should be read from bank object, not call it from here. ","08-01-2020")
  final def bankRoutingAddress : String =
    Connector.connector.vend.getBankLegacy(bankId, None).map(_._1).map(_.bankRoutingAddress).getOrElse("")

  /*
    * Delete this account (if connector allows it, e.g. local mirror of account data)
    * */
  final def remove(user : User): Box[Boolean] = {
    if(user.hasOwnerViewAccess(BankIdAccountId(bankId,accountId))){
      Full(Connector.connector.vend.removeAccount(bankId, accountId).openOrThrowException(attemptedToOpenAnEmptyBox))
    } else {
      Failure(UserNoOwnerView+"user's email : " + user.emailAddress + ". account : " + accountId, Empty, Empty)
    }
  }

  final def updateLabel(user : User, label : String): Box[Boolean] = {
    if(user.hasOwnerViewAccess(BankIdAccountId(bankId, accountId))){
      Connector.connector.vend.updateAccountLabel(bankId, accountId, label)
    } else {
      Failure(UserNoOwnerView+"user's email : " + user.emailAddress + ". account : " + accountId, Empty, Empty)
    }
  }

  /**
    * Note: There are two types of account-owners in OBP: the OBP users and the customers(in a real bank, these should from Main Frame)
    *
    * This will return all the OBP users who have the link in code.accountholder.MapperAccountHolders.
    * This field is tricky, it belongs to Trait `BankAccount` directly, not a field in `MappedBankAccount`
    * So this method always need to call the Model `MapperAccountHolders` and get the data there.
    * Note:
    *  We need manually create records for`MapperAccountHolders`, then we can get the data back.
    *  each time when we create a account, we need call `getOrCreateAccountHolder`
    *  eg1: code.sandbox.OBPDataImport#setAccountOwner used in createSandBox
    *  eg2: code.model.dataAccess.AuthUser#updateUserAccountViews used in Adapter create accounts.
    *  eg3: code.bankconnectors.Connector#setAccountHolder used in api level create account.
    */
  final def userOwners: Set[User] = {
    val accountHolders = AccountHolders.accountHolders.vend.getAccountHolders(bankId, accountId)
    if(accountHolders.isEmpty) {
      //account holders are not all set up in the db yet, so we might not get any back.
      //In this case, we just use the previous behaviour, which did not return very much information at all
      Set(new User {
        val userPrimaryKey = UserPrimaryKey(-1)
        val userId = ""
        val idGivenByProvider = ""
        val provider = ""
        val emailAddress = ""
        val name : String = bankAccount.accountHolder
        val createdByConsentId = None
      })
    } else {
      accountHolders
    }
  }

  /**
    * Note: There are two types of account-owners in OBP: the OBP users and the customers(in a real bank, these should from Main Frame)
    * This method is in processing, not finished yet.
    * For now, it just returns the Customers link to the OBP user, both for `Sandbox Mode` and `MainFrame Mode`.
    *
    * Maybe later, we need to create a new model, store the link between account<--> Customers. But this is not OBP Standard, these customers should come
    * from MainFrame, this is only for MainFrame Mode. We need to clarify what kind of Customers we can get from MainFrame.
    *
    * In other words, this currently returns the customers that are linked to the user (via user-customer-links)
    *
    *
    */
  final def customerOwners: Set[Customer] = {
    val customerList = for{
      accountHolder <- (AccountHolders.accountHolders.vend.getAccountHolders(bankId, accountId).toList)
      customers <- CustomerX.customerProvider.vend.getCustomersByUserId(accountHolder.userId)
    } yield {
      customers
    }
    customerList.toSet
  }

  private def viewNotAllowed(view : View ) = Failure(s"${UserNoPermissionAccessView} Current VIEW_ID (${view.viewId.value})")



  /**
    * @param user a user requesting to see the other users' permissions
    * @return a Box of all the users' permissions of this bank account if the user passed as a parameter has access to the owner view (allowed to see this kind of data)
    */
  final def permissions(user : User) : Box[List[Permission]] = {
    //check if the user have access to the owner view in this the account
    if(user.hasOwnerViewAccess(BankIdAccountId(bankId, accountId)))
      Full(Views.views.vend.permissions(BankIdAccountId(bankId, accountId)))
    else
      Failure("user " + user.emailAddress + " does not have access to owner view on account " + accountId, Empty, Empty)
  }

  /**
    * @param user the user requesting to see the other users permissions on this account
    * @param otherUserProvider the authentication provider of the user whose permissions will be retrieved
    * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) whose permissions will be retrieved
    * @return a Box of the user permissions of this bank account if the user passed as a parameter has access to the owner view (allowed to see this kind of data)
    */
  final def permission(user : User, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Permission] = {
    //check if the user have access to the owner view in this the account
    if(user.hasOwnerViewAccess(BankIdAccountId(bankId, accountId)))
      for{
        u <- UserX.findByProviderId(otherUserProvider, otherUserIdGivenByProvider)
        p <- Views.views.vend.permission(BankIdAccountId(bankId, accountId), u)
      } yield p
    else
      Failure(UserNoOwnerView+"user's email : " + user.emailAddress + ". account : " + accountId, Empty, Empty)
  }

  /**
    * @param user the user that wants to grant another user access to a view on this account
    * @param viewUID uid of the view to which we want to grant access
    * @param otherUserProvider the authentication provider of the user to whom access to the view will be granted
    * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to the view will be granted
    * @return a Full(true) if everything is okay, a Failure otherwise
    */
  final def grantAccessToView(user : User, viewUID : ViewIdBankIdAccountId, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[View] = {
    def grantAccessToCustomOrSystemView(user: User): Box[View] = {
      val ViewIdBankIdAccountId(viewId, bankId, accountId) = viewUID
      Views.views.vend.systemView(viewId) match {
        case Full(systemView) => Views.views.vend.grantAccessToSystemView(bankId, accountId, systemView, user)
        case _ => Views.views.vend.grantAccessToCustomView(viewUID, user)
      }
    }
    if(canGrantAccessToViewCommon(bankId, accountId, user))
      for{
        otherUser <- UserX.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        savedView <- grantAccessToCustomOrSystemView(otherUser) ?~ "could not save the privilege"
      } yield savedView
    else
      Failure(UserNoOwnerView+"user's email : " + user.emailAddress + ". account : " + accountId, Empty, Empty)
  }

  /**
    * @param user the user that wants to grant another user access to a several views on this account
    * @param viewUIDs uids of the views to which we want to grant access
    * @param otherUserProvider the authentication provider of the user to whom access to the views will be granted
    * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to the views will be granted
    * @return a the list of the granted views if everything is okay, a Failure otherwise
    */
  final def grantAccessToMultipleViews(user : User, viewUIDs : List[ViewIdBankIdAccountId], otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[List[View]] = {
    if(canGrantAccessToViewCommon(bankId, accountId, user))
      for{
        otherUser <- UserX.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        grantedViews <- Views.views.vend.grantAccessToMultipleViews(viewUIDs, otherUser) ?~ "could not save the privilege"
      } yield grantedViews
    else
      Failure(UserNoOwnerView+"user's email : " + user.emailAddress + ". account : " + accountId, Empty, Empty)
  }

  /**
    * @param user the user that wants to revoke another user's access to a view on this account
    * @param viewUID uid of the view to which we want to revoke access
    * @param otherUserProvider the authentication provider of the user to whom access to the view will be revoked
    * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to the view will be revoked
    * @return a Full(true) if everything is okay, a Failure otherwise
    */
  final def revokeAccessToView(user : User, viewUID : ViewIdBankIdAccountId, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Boolean] = {
    def revokeAccessToCustomOrSystemView(user: User): Box[Boolean] = {
      val ViewIdBankIdAccountId(viewId, bankId, accountId) = viewUID
      Views.views.vend.systemView(viewId) match {
        case Full(systemView) => Views.views.vend.revokeAccessToSystemView(bankId, accountId, systemView, user)
        case _ => Views.views.vend.revokeAccess(viewUID, user)
      }
    }
    //check if the user have access to the owner view in this the account
    if(canRevokeAccessToViewCommon(bankId, accountId, user))
      for{
        otherUser <- UserX.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) //check if the userId corresponds to a user
        isRevoked <- revokeAccessToCustomOrSystemView(otherUser: User) ?~ "could not revoke the privilege"
      } yield isRevoked
    else
      Failure(UserNoOwnerView+"user's email : " + user.emailAddress + ". account : " + accountId, Empty, Empty)
  }

  /**
    *
    * @param user the user that wants to revoke another user's access to all views on this account
    * @param otherUserProvider the authentication provider of the user to whom access to all views will be revoked
    * @param otherUserIdGivenByProvider the id of the user (the one given by their auth provider) to whom access to all views will be revoked
    * @return a Full(true) if everything is okay, a Failure otherwise
    */

  final def revokeAllAccountAccesses(user : User, otherUserProvider : String, otherUserIdGivenByProvider: String) : Box[Boolean] = {
    if(canRevokeAccessToViewCommon(bankId, accountId, user))
      for{
        otherUser <- UserX.findByProviderId(otherUserProvider, otherUserIdGivenByProvider) ?~ UserNotFoundByUsername
        isRevoked <- Views.views.vend.revokeAllAccountAccesses(bankId, accountId, otherUser)
      } yield isRevoked
    else
      Failure(UserNoOwnerView+"user's email : " + user.emailAddress + ". account : " + accountId, Empty, Empty)
  }


  final def createView(userDoingTheCreate : User,v: CreateViewJson): Box[View] = {
    if(!userDoingTheCreate.hasOwnerViewAccess(BankIdAccountId(bankId,accountId))) {
      Failure({"user: " + userDoingTheCreate.idGivenByProvider + " at provider " + userDoingTheCreate.provider + " does not have owner access"})
    } else {
      val view = Views.views.vend.createView(BankIdAccountId(bankId,accountId), v)

      //if(view.isDefined) {
      //  logger.debug("user: " + userDoingTheCreate.idGivenByProvider + " at provider " + userDoingTheCreate.provider + " created view: " + view.get +
      //      " for account " + accountId + "at bank " + bankId)
      //}

      view
    }
  }

  final def updateView(userDoingTheUpdate : User, viewId : ViewId, v: UpdateViewJSON) : Box[View] = {
    if(!userDoingTheUpdate.hasOwnerViewAccess(BankIdAccountId(bankId,accountId))) {
      Failure({"user: " + userDoingTheUpdate.idGivenByProvider + " at provider " + userDoingTheUpdate.provider + " does not have owner access"})
    } else {
      val view = Views.views.vend.updateCustomView(BankIdAccountId(bankId,accountId), viewId, v)
      //if(view.isDefined) {
      //  logger.debug("user: " + userDoingTheUpdate.idGivenByProvider + " at provider " + userDoingTheUpdate.provider + " updated view: " + view.get +
      //      " for account " + accountId + "at bank " + bankId)
      //}

      view
    }
  }

  final def removeView(userDoingTheRemove : User, viewId: ViewId) : Box[Boolean] = {
    if(!userDoingTheRemove.hasOwnerViewAccess(BankIdAccountId(bankId,accountId))) {
      return Failure({"user: " + userDoingTheRemove.idGivenByProvider + " at provider " + userDoingTheRemove.provider + " does not have owner access"})
    } else {
      val deleted = Views.views.vend.removeCustomView(viewId, BankIdAccountId(bankId,accountId))

      //if (deleted.isDefined) {
      //    logger.debug("user: " + userDoingTheRemove.idGivenByProvider + " at provider " + userDoingTheRemove.provider + " deleted view: " + viewId +
      //    " for account " + accountId + "at bank " + bankId)
      //}

      deleted
    }
  }

  final def moderatedTransaction(transactionId: TransactionId, view: View, bankIdAccountId: BankIdAccountId, user: Box[User], callContext: Option[CallContext] = None) : Box[(ModeratedTransaction, Option[CallContext])] = {
    if(APIUtil.hasAccountAccess(view, bankIdAccountId, user))
      for{
        (transaction, callContext)<-Connector.connector.vend.getTransactionLegacy(bankId, accountId, transactionId, callContext)
        moderatedTransaction<- view.moderateTransaction(transaction)
      } yield (moderatedTransaction, callContext)
    else
      viewNotAllowed(view)
  }
  final def moderatedTransactionFuture(transactionId: TransactionId, view: View, user: Box[User], callContext: Option[CallContext] = None) : Future[Box[(ModeratedTransaction, Option[CallContext])]] = {
    if(APIUtil.hasAccountAccess(view, BankIdAccountId(bankId, accountId), user))
      for{
        (transaction, callContext)<-Connector.connector.vend.getTransaction(bankId, accountId, transactionId, callContext) map {
          x => (unboxFullOrFail(x._1, callContext, TransactionNotFound, 400), x._2)
        }
      } yield {
        view.moderateTransaction(transaction) match {
          case Full(m) => Full((m, callContext))
          case _ => Failure("Server error - moderateTransactionsWithSameAccount")
        }
      }
    else
      Future(viewNotAllowed(view))
  }

  /*
   end views
  */

  // TODO We should extract params (and their defaults) prior to this call, so this whole function can be cached.
  final def getModeratedTransactions(bank: Bank, user : Box[User], view : View, bankIdAccountId: BankIdAccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Box[(List[ModeratedTransaction],Option[CallContext])] = {
    if(APIUtil.hasAccountAccess(view, bankIdAccountId, user)) {
      for {
        (transactions, callContext)  <- Connector.connector.vend.getTransactionsLegacy(bankId, accountId, callContext, queryParams)
        moderated <- view.moderateTransactionsWithSameAccount(bank, transactions) ?~! "Server error"
      } yield (moderated, callContext)
    }
    else viewNotAllowed(view)
  }
  final def getModeratedTransactionsFuture(bank: Bank, user : Box[User], view : View, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Future[Box[(List[ModeratedTransaction],Option[CallContext])]] = {
    if(APIUtil.hasAccountAccess(view, BankIdAccountId(bankId, accountId), user)) {
      for {
        (transactions, callContext)  <- Connector.connector.vend.getTransactions(bankId, accountId, callContext, queryParams) map {
          x => (unboxFullOrFail(x._1, callContext, InvalidConnectorResponse, 400), x._2)
        }
      } yield {
        view.moderateTransactionsWithSameAccount(bank, transactions) match {
          case Full(m) => Full((m, callContext))
          case _ => Failure("Server error - moderateTransactionsWithSameAccount")
        }
      }
    }
    else Future(viewNotAllowed(view))
  }

  // TODO We should extract params (and their defaults) prior to this call, so this whole function can be cached.
  final def getModeratedTransactionsCore(bank: Bank, user : Box[User], view : View, bankIdAccountId: BankIdAccountId, queryParams: List[OBPQueryParam], callContext: Option[CallContext] ): OBPReturnType[Box[List[ModeratedTransactionCore]]] = {
    if(APIUtil.hasAccountAccess(view, bankIdAccountId,user)) {
      for {
        (transactions, callContext) <- NewStyle.function.getTransactionsCore(bankId, accountId, queryParams, callContext)
        moderated <- Future {view.moderateTransactionsWithSameAccountCore(bank, transactions)}
      } yield (moderated, callContext)
    }
    else Future{(viewNotAllowed(view), callContext)}
  }

  final def moderatedBankAccount(view: View, bankIdAccountId: BankIdAccountId, user: Box[User], callContext: Option[CallContext]) : Box[ModeratedBankAccount] = {
    if(APIUtil.hasAccountAccess(view, bankIdAccountId, user))
    //implicit conversion from option to box
      view.moderateAccountLegacy(bankAccount)
    else
      viewNotAllowed(view)
  }

  final def moderatedBankAccountCore(view: View, bankIdAccountId: BankIdAccountId, user: Box[User], callContext: Option[CallContext]) : Box[ModeratedBankAccountCore] = {
    if(APIUtil.hasAccountAccess(view, bankIdAccountId, user))
    //implicit conversion from option to box
      view.moderateAccountCore(bankAccount)
    else
      viewNotAllowed(view)
  }

  /**
    * @param view that we will use to get the ModeratedOtherBankAccount list
    * @param bankIdAccountId bankId and accountId
    * @param user that want access to the ModeratedOtherBankAccount list
    * @return a Box of a list ModeratedOtherBankAccounts, it the bank
    *  accounts that have at least one transaction in common with this bank account
    */
  final def moderatedOtherBankAccounts(view : View, bankIdAccountId: BankIdAccountId, user : Box[User]) : Box[List[ModeratedOtherBankAccount]] =
    if(APIUtil.hasAccountAccess(view, bankIdAccountId, user)){
      val implicitModeratedOtherBankAccounts = Connector.connector.vend.getCounterpartiesFromTransaction(bankId, accountId).openOrThrowException(attemptedToOpenAnEmptyBox).map(oAcc => view.moderateOtherAccount(oAcc)).flatten
      val explictCounterpartiesBox = Connector.connector.vend.getCounterpartiesLegacy(view.bankId, view.accountId, view.viewId)
      explictCounterpartiesBox match {
        case Full((counterparties, callContext))=> {
          val explictModeratedOtherBankAccounts: List[ModeratedOtherBankAccount] = counterparties.flatMap(BankAccountX.toInternalCounterparty).flatMap(counterparty=>view.moderateOtherAccount(counterparty))
          Full(explictModeratedOtherBankAccounts ++ implicitModeratedOtherBankAccounts)
        }
        case _ => Full(implicitModeratedOtherBankAccounts)
      }
    }
    else
      viewNotAllowed(view)
  /**
    * @param the ID of the other bank account that the user want have access
    * @param the view that we will use to get the ModeratedOtherBankAccount
    * @param the user that want access to the otherBankAccounts list
    * @return a Box of a ModeratedOtherBankAccounts, it a bank
    *  account that have at least one transaction in common with this bank account
    */
  final def moderatedOtherBankAccount(counterpartyID : String, view : View, bankIdAccountId: BankIdAccountId, user : Box[User], callContext: Option[CallContext]) : Box[ModeratedOtherBankAccount] =
    if(APIUtil.hasAccountAccess(view, bankIdAccountId, user))
      Connector.connector.vend.getCounterpartyByCounterpartyIdLegacy(CounterpartyId(counterpartyID), None).map(_._1).flatMap(BankAccountX.toInternalCounterparty).flatMap(view.moderateOtherAccount) match {
        //First check the explict counterparty
        case Full(moderatedOtherBankAccount) => Full(moderatedOtherBankAccount)
        //Than we checked the implict counterparty.
        case _ => Connector.connector.vend.getCounterpartyFromTransaction(bankId, accountId, counterpartyID).flatMap(oAcc => view.moderateOtherAccount(oAcc))
      }
    else
      viewNotAllowed(view)

}

object BankAccountX {

  def apply(bankId: BankId, accountId: AccountId) : Box[BankAccount] = {
    Connector.connector.vend.getBankAccountOld(bankId, accountId)
  }

  def apply(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) : Box[(BankAccount,Option[CallContext])] = {
    Connector.connector.vend.getBankAccountLegacy(bankId, accountId, callContext)
  }
  /**
    * Mapping a CounterpartyTrait to OBP BankAccount.
    * If we can find an mapping OBP BankAccount, we will return it.
    * If not, we will use the default bankAccounts. Which are created during boot.scala.
    *
    * @param counterparty
    * @param isOutgoingAccount counterparty can be incoming or outgoing. 
    *                          outgoing: obp account send money to counterparty.
    *                          incoming: counterparty send money to obp account.
    * @return BankAccount
    */
  def getBankAccountFromCounterparty(counterparty: CounterpartyTrait, isOutgoingAccount: Boolean) : Box[BankAccount] = {
    if (
      (counterparty.otherBankRoutingScheme =="OBP" || counterparty.otherBankRoutingScheme =="OBP_BANK_ID" )
      && (counterparty.otherAccountRoutingScheme =="OBP" || counterparty.otherAccountRoutingScheme =="OBP_ACCOUNT_ID")
    )
      for{
        toBankId <- Full(BankId(counterparty.otherBankRoutingAddress))
        toAccountId <- Full(AccountId(counterparty.otherAccountRoutingAddress))
        toAccount <- BankAccountX(toBankId, toAccountId) ?~! s"${ErrorMessages.BankNotFound} Current Value: BANK_ID(counterparty.otherBankRoutingAddress=$toBankId) and ACCOUNT_ID(counterparty.otherAccountRoutingAddress=$toAccountId), please use correct OBP BankAccount to create the Counterparty.!!!!! "
      } yield{
        toAccount
      }
    else if (
      (counterparty.otherBankRoutingScheme =="OBP" || counterparty.otherBankRoutingScheme =="OBP_BANK_ID" ) 
        && (counterparty.otherAccountSecondaryRoutingScheme == "OBP" || counterparty.otherAccountSecondaryRoutingScheme == "OBP_ACCOUNT_ID")
    )
      for{
        toBankId <- Full(BankId(counterparty.otherBankRoutingAddress))
        toAccountId <- Full(AccountId(counterparty.otherAccountSecondaryRoutingAddress))
        toAccount <- BankAccountX(toBankId, toAccountId) ?~! s"${ErrorMessages.BankNotFound} Current Value: BANK_ID(counterparty.otherBankRoutingAddress=$toBankId) and ACCOUNT_ID(counterparty.otherAccountRoutingAddress=$toAccountId), please use correct OBP BankAccount to create the Counterparty.!!!!! "
      } yield{
        toAccount
      }
    else {
      //in obp we are creating a fake account with the counterparty information in this case:
      //These are just the obp mapped mode, if connector to the bank, bank will decide it.

      val accountRouting1 =
        if (counterparty.otherAccountRoutingScheme.isEmpty) Nil
        else List(AccountRouting(counterparty.otherAccountRoutingScheme, counterparty.otherAccountRoutingAddress))
      val accountRouting2 =
        if (counterparty.otherAccountSecondaryRoutingScheme.isEmpty) Nil
        else List(AccountRouting(counterparty.otherAccountSecondaryRoutingScheme, counterparty.otherAccountSecondaryRoutingAddress))

      // Due to the new field in the database, old counterparty have void currency, so by default, we set it to EUR
      val counterpartyCurrency = if (counterparty.currency.nonEmpty) counterparty.currency else "EUR"

      Full(BankAccountCommons(
        AccountId(counterparty.otherAccountSecondaryRoutingAddress), "", 0,
        currency = counterpartyCurrency,
        name = counterparty.name,
        "", "", BankId(counterparty.otherBankRoutingAddress), new Date(), "",
        accountRoutings = accountRouting1 ++ accountRouting2,
        List.empty, accountHolder = counterparty.name,
        Some(List(Attribute(
          name = "BANK_ROUTING_SCHEME",
          `type` = "STRING",
          value = counterparty.otherBankRoutingScheme
        ),
          Attribute(
            name = "BANK_ROUTING_ADDRESS",
            `type` = "STRING",
            value = counterparty.otherBankRoutingAddress
          ),
        ))
      ))
    }
  }

  //This method change CounterpartyTrait to internal counterparty, becuasa of the view stuff.
  //All the fields need be controlled by the view, and the `com.openbankproject.commons.model.View.moderate` accept the `Counterparty` as parameter.
  def toInternalCounterparty(counterparty: CounterpartyTrait) : Box[Counterparty] = {
    Full(
      //TODO, check all the `new Counterparty` code, they can be reduced into one gernal method for all.
      new Counterparty(
        kind = "",//Can not map
        nationalIdentifier = "", //Can not map
        otherAccountProvider = "", //Can not map

        thisBankId = BankId(counterparty.thisBankId),
        thisAccountId = AccountId(counterparty.thisAccountId),
        counterpartyId = counterparty.counterpartyId,
        counterpartyName = counterparty.name,

        otherBankRoutingAddress = Some(counterparty.otherBankRoutingAddress),
        otherBankRoutingScheme = counterparty.otherBankRoutingScheme,
        otherAccountRoutingScheme = counterparty.otherAccountRoutingScheme,
        otherAccountRoutingAddress = Some(counterparty.otherBankRoutingAddress),
        isBeneficiary = true
      )
    )
  }


  def publicAccounts(publicAccountAccesses: List[AccountAccess]) : List[BankAccount] = {
    publicAccountAccesses
      .map(a => BankIdAccountId(BankId(a.bank_id.get), AccountId(a.account_id.get))).distinct
      .flatMap(a => BankAccountX(a.bankId, a.accountId))
  }

  def privateAccounts(privateViewsUserCanAccess: List[AccountAccess]) : List[BankAccount] = {
    privateViewsUserCanAccess
      .map(a => BankIdAccountId(BankId(a.bank_id.get), AccountId(a.account_id.get))).distinct.
      flatMap(a => BankAccountX(a.bankId, a.accountId))
  }
}

/*
The other bank account or counterparty in a transaction
as see from the perspective of the original party.
 */


trait TransactionUUID {
  def theTransactionId : TransactionId
  def theBankId : BankId
  def theAccountId : AccountId
}

