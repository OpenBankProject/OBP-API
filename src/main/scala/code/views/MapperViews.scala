package code.views

import scala.collection.immutable.List
import code.model.BankAccount
import code.model.View
import code.model.Permission
import code.model.User
import code.model.dataAccess.APIUser
import code.model.dataAccess.ViewImpl
import code.model.dataAccess.ViewPrivileges
import net.liftweb.common.Loggable
import net.liftweb.mapper.{QueryParam, By}
import net.liftweb.common.{Box, Full, Empty, Failure}
import code.model.ViewCreationJSON
import code.model.ViewUpdateData
import code.api.APIFailure


//TODO: get rid of references to APIUser
//TODO: Replace BankAccounts with bankPermalink + accountPermalink


object MapperViews extends Views with Loggable {

  def permissions(account : BankAccount) : Box[List[Permission]] = {

    val views: List[ViewImpl] = ViewImpl.findAll(By(ViewImpl.isPublic_, false) ::
      ViewImpl.accountFilter(account.bankPermalink, account.permalink): _*)
    //all the user that have access to at least to a view
    val users = views.map(_.users.toList).flatten.distinct
    val usersPerView = views.map(v  =>(v, v.users.toList))
    val permissions = users.map(u => {
      new Permission(
        u,
        usersPerView.filter(_._2.contains(u)).map(_._1)
      )
    })

    //TODO: get rid of the Box
    Full(permissions)
  }

  def permission(account : BankAccount, user: User) : Box[Permission] = {

    user match {
      case u: APIUser => {
        //search ViewPrivileges to get all views for user and then filter the views
        // by bankPermalink and accountPermalink
        //TODO: do it in a single query with a join
        val privileges = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
        val views = privileges.flatMap(_.view.obj).filter(v => {
          v.accountPermalink.get == account.permalink &&
          v.bankPermalink.get == account.bankPermalink
        })
        Full(Permission(user, views))
      }
      case u: User => {
        logger.error("APIUser instance not found, could not grant access ")
        Empty
      }
    }
  }

  //TODO: remove bankAccountId
  def addPermission(bankAccountId : String, view: View, user : User) : Box[Boolean] = {
    user match {
      case u: APIUser => {
        //check if it exists
        if(ViewPrivileges.count(By(ViewPrivileges.user,u), By(ViewPrivileges.view,view.id))==0)
          Full(ViewPrivileges.create.
            user(u).
            view(view.id).
            save)
        else
          Full(true)
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

  def revokeAllPermission(bankPermalink : String, accountPermalink: String, user : User) : Box[Boolean] = {
    user match {
      //TODO: fix this match stuff
      case u:APIUser =>{
        //TODO: make this more efficient by using one query (with a join)
        val allUserPrivs = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
        val relevantAccountPrivs = allUserPrivs.filter(p => p.view.obj match {
          case Full(v) => v.bankPermalink.get == bankPermalink && v.accountPermalink.get == accountPermalink
          case _ => false
        })
        relevantAccountPrivs.foreach(_.delete_!)
        Full(true)
      }
      case u: User => {
        logger.error("APIUser instance not found, could not revoke access ")
        Empty
      }
    }
  }
  
  def view(viewPermalink : String, account: BankAccount) : Box[View] = {
    view(viewPermalink, account.bankPermalink, account.permalink)
  }

  def view(viewPermalink : String, accountPermalink: String, bankPermalink: String) : Box[View] = {
    ViewImpl.find(By(ViewImpl.permalink_, viewPermalink) ::
      ViewImpl.accountFilter(bankPermalink, accountPermalink): _*)
  }

  def createView(bankAccount: BankAccount, view: ViewCreationJSON): Box[View] = {
    val newViewPermalink = {
      view.name.replaceAllLiterally(" ","").toLowerCase
    }

    val existing = ViewImpl.find(By(ViewImpl.permalink_, newViewPermalink) ::
      ViewImpl.accountFilter(bankAccount.bankPermalink, bankAccount.permalink): _*)

    if(existing.isDefined)
      Failure(s"There is already a view with permalink $newViewPermalink on this bank account")
    else {
      val createdView = ViewImpl.create.
        name_(view.name).
        permalink_(newViewPermalink).
        bankPermalink(bankAccount.bankPermalink).
        accountPermalink(bankAccount.permalink)

      createdView.setFromViewData(view)
      Full(createdView.saveMe)
    }

  }

  def updateView(bankAccount : BankAccount, viewId: String, viewUpdateJson : ViewUpdateData) : Box[View] = {

    for {
      view <- ViewImpl.find(By(ViewImpl.permalink_, viewId) ::
        ViewImpl.accountFilter(bankAccount.bankPermalink, bankAccount.permalink): _*) ~> new APIFailure {
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
    else {
      for {
        view <- ViewImpl.find(By(ViewImpl.permalink_, viewId) ::
          ViewImpl.accountFilter(bankAccount.bankPermalink, bankAccount.permalink): _*) ~> new APIFailure {
          override val responseCode: Int = 404
          override val msg: String = s"View $viewId not found"
        }
        if(view.delete_!)
      } yield {
      }
    }
  }

  def views(bankAccount : BankAccount) : Box[List[View]] = {
    Full(ViewImpl.findAll(ViewImpl.accountFilter(bankAccount.bankPermalink, bankAccount.permalink): _*))
  }

  def permittedViews(user: User, bankAccount: BankAccount): List[View] = {

    user match {
      //TODO: fix this match stuff
      case u: APIUser=> {
        //TODO: do this more efficiently?
        val allUserPrivs = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
        val userViewsForAccount = allUserPrivs.flatMap(p => {
          p.view.obj match {
            case Full(v) => if(v.bankPermalink.get == bankAccount.bankPermalink &&
              v.accountPermalink.get == bankAccount.permalink){
              Some(v)
            } else None
            case _ => None
          }
        })
        userViewsForAccount
      }
      case _ => {
        logger.error("APIUser instance not found, could not get Permitted views")
        Nil
      }
    }
  }

  def publicViews(bankAccount : BankAccount) : Box[List[View]] = {
    //TODO: do this more efficiently?
    //TODO: get rid of box
    Full(ViewImpl.findAll(ViewImpl.accountFilter(bankAccount.bankPermalink, bankAccount.permalink): _*).filter(v => {
      v.isPublic == true
    }))
  }
  
}