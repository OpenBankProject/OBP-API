package code.views

import scala.collection.immutable.List
import code.model.BankAccount
import code.model.View
import code.model.Permission
import code.model.User
import code.model.dataAccess.APIUser
import code.model.dataAccess.ViewImpl
import code.model.dataAccess.HostedAccount
import code.model.dataAccess.ViewPrivileges
import code.bankconnectors.Connector
import net.liftweb.common.Loggable
import net.liftweb.mapper.{ByList, By}
import net.liftweb.common.{Box, Full, Empty, Failure}
import code.model.ViewCreationJSON
import code.model.ViewUpdateData
import code.api.APIFailure


//TODO: get rid of references to APIUser


object MapperViews extends Views with Loggable {
  
  def permissions(account : BankAccount) : Box[List[Permission]] = {
    for{
      acc <- HostedAccount.find(By(HostedAccount.accountID,account.id))
    } yield {

        val views: List[ViewImpl] = ViewImpl.findAll(By(ViewImpl.account, acc), By(ViewImpl.isPublic_, false))
        //all the user that have access to at least to a view
        val users = views.map(_.users.toList).flatten.distinct
        val usersPerView = views.map(v  =>(v, v.users.toList))
        users.map(u => {
          new Permission(
            u,
            usersPerView.filter(_._2.contains(u)).map(_._1)
          )
        })
      }
  }

  def permission(account : BankAccount, user: User) : Box[Permission] = {

    for{
      acc <- HostedAccount.find(By(HostedAccount.accountID,account.id))
    } yield {
      val viewsOfAccount = acc.views.toList
      val viewsOfUser = user.views.toList
      val views = viewsOfAccount.filter(v => viewsOfUser.contains(v))
      Permission(user, views)
    }
  }

  def addPermission(bankAccountId : String, view: View, user : User) : Box[Boolean] = {
    user match {
      //TODO: fix this match stuff
      case u: APIUser =>
        for{
          bankAccount <- HostedAccount.find(By(HostedAccount.accountID, bankAccountId))
        } yield {
            if(ViewPrivileges.count(By(ViewPrivileges.user,u), By(ViewPrivileges.view,view.id))==0)
              ViewPrivileges.create.
                user(u).
                view(view.id).
                save
            else
              true
          }
      case u: User => {
          logger.error("APIUser instance not found, could not grant access ")
          Empty
      }
    }
  }

  def addPermissions(bankAccountId : String, views : List[View], user : User) : Box[Boolean] ={
    user match {
      //TODO: fix this match stuff
      case u : APIUser => {
        views.foreach(v => {
          if(ViewPrivileges.count(By(ViewPrivileges.user,u), By(ViewPrivileges.view,v.id))==0){
            ViewPrivileges.create.
              user(u).
              view(v.id).
              save
          }
        })
        Full(true)
      }
      case u: User => {
        logger.error("APIUser instance not found, could not grant access ")
        Empty
      }
    }

  }
  def revokePermission(bankAccountId : String, view : View, user : User) : Box[Boolean] = {
    user match {
      //TODO: fix this match stuff
      case u:APIUser =>
        for{
          bankAccount <- HostedAccount.find(By(HostedAccount.accountID, bankAccountId))
          vp <- ViewPrivileges.find(By(ViewPrivileges.user, u), By(ViewPrivileges.view, view.id))
          deletable <- checkIfOwnerViewAndHasMoreThanOneUser(view)
        } yield {
            vp.delete_!
          }
      case u: User => {
        logger.error("APIUser instance not found, could not revoke access")
        Empty
      }
    }
  }
  
  def checkIfOwnerViewAndHasMoreThanOneUser(view: View): Box[Unit] = {
    if((view.name=="Owner") && (view.users.length <= 1)){
      Failure("only person with owner view permission, access cannot be revoked")
    }
    else{
      Full(Unit)
    }
  }

  def revokeAllPermission(bankAccountId : String, user : User) : Box[Boolean] = {
    user match {
      //TODO: fix this match stuff
      case u:APIUser =>{
        for{
          bankAccount <- HostedAccount.find(By(HostedAccount.accountID, bankAccountId))
        } yield {
          val views = ViewImpl.findAll(By(ViewImpl.account, bankAccount)).map(_.id_.get)
          ViewPrivileges.findAll(By(ViewPrivileges.user, u), ByList(ViewPrivileges.view, views)).map(_.delete_!)
          true
        }
      }
      case u: User => {
        logger.error("APIUser instance not found, could not revoke access ")
        Empty
      }
    }
  }
  
  def view(viewPermalink : String, account: BankAccount) : Box[View] = {
    //TODO: Once ViewImpl contains fields for accountId and bankId, we can skip the acount lookup
    for{
      acc <- HostedAccount.find(By(HostedAccount.accountID, account.id))
      v <- ViewImpl.find(By(ViewImpl.permalink_, viewPermalink), By(ViewImpl.account, acc))
    } yield v
  }

  def view(viewPermalink : String, accountId: String, bankId: String) : Box[View] = {
    //TODO: Once ViewImpl contains fields for accountId and bankId, we can skip the acount lookup
    for{
      account <- Connector.connector.vend.getBankAccount(bankId, accountId)
      acc <- HostedAccount.find(By(HostedAccount.accountID, account.id))
      v <- ViewImpl.find(By(ViewImpl.permalink_, viewPermalink), By(ViewImpl.account, acc))
    } yield v
  }

  def createView(bankAccount: BankAccount, view: ViewCreationJSON): Box[View] = {
    val newViewPermalink = {
      view.name.replaceAllLiterally(" ","").toLowerCase
    }

    HostedAccount.find(By(HostedAccount.accountID,bankAccount.id)) match {
      case Full(account) => {
        val existing = ViewImpl.find(
        By(ViewImpl.permalink_, newViewPermalink),
        By(ViewImpl.account, account))

        if(existing.isDefined)
          Failure(s"There is already a view with permalink $newViewPermalink on this bank account")
        else{
          val createdView = ViewImpl.create.
            name_(view.name).
            permalink_(newViewPermalink).
            account(account)

          createdView.setFromViewData(view)
          Full(createdView.saveMe)
        }

      }
      case _ => Failure(s"Account ${bankAccount.id} not found")
    }


  }

  def updateView(bankAccount : BankAccount, viewId: String, viewUpdateJson : ViewUpdateData) : Box[View] = {

    for {
      account <- HostedAccount.find(By(HostedAccount.accountID, bankAccount.id)) ~> new APIFailure {
        override val responseCode: Int = 404
        override val msg: String = s"Account ${bankAccount.id} not found"
      }
      view <- ViewImpl.find(
        By(ViewImpl.permalink_, viewId),
        By(ViewImpl.account, account)) ~> new APIFailure {
          override val responseCode: Int = 404
          override val msg: String = s"View $viewId not found"
        }
    } yield {
      view.setFromViewData(viewUpdateJson)
      view.saveMe
    }
  }

  def removeView(viewId: String, bankAccount: BankAccount): Box[Unit] = {
    if(viewId=="Owner")
      Failure("you cannot delete the Owner view")
    else
      for{
        v <- ViewImpl.find(By(ViewImpl.permalink_,viewId)) ?~ "view not found"
        if(v.delete_!)
      } yield {}
  }

  def views(bankAccount : BankAccount) : Box[List[View]] = {
    for(account <- HostedAccount.find(By(HostedAccount.accountID, bankAccount.id)))
      yield {
        account.views.toList
      }
  }

  def permittedViews(user: User, bankAccount: BankAccount): List[View] = {
    user match {
      //TODO: fix this match stuff
      case u: APIUser=> {
        val nonPublic: List[View] =
          HostedAccount.find(By(HostedAccount.accountID, bankAccount.id)) match {
            case Full(account) =>{
              val accountViews = account.views.toList
              val userViews = u.views
              accountViews.filter(v => userViews.contains(v))
            }
            case _ => Nil
          }

        nonPublic ::: bankAccount.publicViews
      }
      case _ => {
        logger.error("APIUser instance not found, could not get Permitted views")
        Nil
      }
    }
  }

  def publicViews(bankAccount : BankAccount) : Box[List[View]] = {
    for{account <- HostedAccount.find(By(HostedAccount.accountID, bankAccount.id))}
      yield{
        account.views.toList.filter(v => v.isPublic==true)
      }
  }
  
}