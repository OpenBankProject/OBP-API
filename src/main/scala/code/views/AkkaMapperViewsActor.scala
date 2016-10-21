package code.views

import akka.actor.Actor
import akka.event.Logging
import code.model._
import code.model.dataAccess.{ViewImpl, ViewPrivileges}
import net.liftweb.common.Box
import net.liftweb.mapper.Schemifier


/**
  * Created by petar on 10/10/16.
  */


class AkkaMapperViewsActor extends Actor with RemoteViewCases {

  val logger = Logging(context.system, this)

  Schemifier.schemify(true, Schemifier.infoF _, ViewImpl)
  Schemifier.schemify(true, Schemifier.infoF _, ViewPrivileges)

  val v = MapperViews

  def receive = {

    case permissions(account : BankAccount) => sender ! v.permissions(account)
    case permission(account : BankAccount, user: User) => sender ! v.permission(account, user)
    case addPermission(viewUID : ViewUID, user : User) => sender ! v.addPermission(viewUID, user)
    case addPermissions(views : List[ViewUID], user : User) => sender ! v.addPermissions(views, user)
    case revokePermission(viewUID : ViewUID, user : User) => sender ! v.revokePermission(viewUID, user)
    case revokeAllPermission(bankId : BankId, accountId : AccountId, user : User) => sender ! v.revokeAllPermission(bankId, accountId, user)

    case createView(bankAccount : BankAccount, view: CreateViewJSON) => sender ! v.createView(bankAccount, view)
    case removeView(viewId : ViewId, bankAccount: BankAccount) => sender ! v.removeView(viewId, bankAccount)
    case updateView(bankAccount : BankAccount, viewId : ViewId, viewUpdateJson : UpdateViewJSON) => sender ! v.updateView(bankAccount, viewId, viewUpdateJson)
    case views(bankAccount : BankAccount) => sender ! v.views(bankAccount)
    case permittedViews(user: User, bankAccount: BankAccount) => sender ! v.permittedViews(user, bankAccount)
    case publicViews(bankAccount : BankAccount) => sender ! v.publicViews(bankAccount)

    case getAllPublicAccounts => sender ! v.getAllPublicAccounts
    case getPublicBankAccounts(bank : Bank) => sender ! v.getPublicBankAccounts(bank)

    case getAllAccountsUserCanSee(user : Box[User]) => sender ! v.getAllAccountsUserCanSee(user)
    case getAllAccountsUserCanSee(bank: Bank, user : Box[User]) => sender ! v.getAllAccountsUserCanSee(bank, user)

    case getNonPublicBankAccounts(user: User, bankId: BankId) => sender ! v.getNonPublicBankAccounts(user, bankId)
    case getNonPublicBankAccounts(user: User) => sender ! v.getNonPublicBankAccounts(user)

    case view(viewId: ViewId, bankAccount: BankAccount) => sender ! v.view(viewId, bankAccount)
    case view(viewUID : ViewUID) => sender ! v.view(viewUID)

  }

}
