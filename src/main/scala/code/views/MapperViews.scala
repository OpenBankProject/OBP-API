package code.views

import scala.collection.immutable.List
import code.model._
import code.model.dataAccess.APIUser
import code.model.dataAccess.ViewImpl
import code.model.dataAccess.ViewPrivileges
import net.liftweb.common.Loggable
import net.liftweb.mapper.{QueryParam, By}
import net.liftweb.common.{Box, Full, Empty, Failure}
import code.api.APIFailure
import code.model.ViewCreationJSON
import net.liftweb.common.Full
import code.model.Permission
import code.model.ViewUpdateData
import scala.Some
import code.bankconnectors.Connector


//TODO: get rid of references to APIUser
//TODO: Replace BankAccounts with bankPermalink + accountPermalink


private object MapperViews extends Views with Loggable {

  def permissions(account : BankAccount) : Box[List[Permission]] = {

    val views: List[ViewImpl] = ViewImpl.findAll(By(ViewImpl.isPublic_, false) ::
      ViewImpl.accountFilter(account.bankId, account.accountId): _*)
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
          v.accountPermalink.get == account.accountId.value &&
          v.bankPermalink.get == account.bankId.value
        })
        Full(Permission(user, views))
      }
      case u: User => {
        logger.error("APIUser instance not found, could not grant access ")
        Empty
      }
    }
  }

  def addPermission(view: View, user : User) : Box[Boolean] = {
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

  def addPermissions(views : List[View], user : User) : Box[Boolean] ={
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

  def revokePermission(view : View, user : User) : Box[Boolean] = {
    user match {
      //TODO: fix this match stuff
      case u:APIUser =>
        for{
          vp <- ViewPrivileges.find(By(ViewPrivileges.user, u), By(ViewPrivileges.view, view.id))
          deletable <- accessRemovableAsBox(view, u)
        } yield {
            vp.delete_!
          }
      case u: User => {
        logger.error("APIUser instance not found, could not revoke access")
        Empty
      }
    }
  }

  //returns Full if deletable, Failure if not
  def accessRemovableAsBox(view: View, user : User) : Box[Unit] = {
    if(accessRemovable(view, user)) Full(Unit)
    else Failure("only person with owner view permission, access cannot be revoked")
  }


  def accessRemovable(view: View, user : User) : Boolean = {
    if(view.permalink == "owner") {

      //if the user is an account holder, we can't revoke access to the owner view
      if(Connector.connector.vend.getAccountHolders(view.bankAccountBankId, view.bankAccountId).contains(user)) {
        false
      } else {
        // if it's the owner view, we can only revoke access if there would then still be someone else
        // with access
        view.users.length > 1
      }

    } else true
  }

  def revokeAllPermission(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = {
    user match {
      //TODO: fix this match stuff
      case u:APIUser =>{
        //TODO: make this more efficient by using one query (with a join)
        val allUserPrivs = ViewPrivileges.findAll(By(ViewPrivileges.user, u))

        val relevantAccountPrivs = allUserPrivs.filter(p => p.view.obj match {
          case Full(v) => {
            v.bankPermalink.get == bankId.value && v.accountPermalink.get == accountId.value
          }
          case _ => false
        })

        val allRelevantPrivsRevokable = relevantAccountPrivs.forall( p => p.view.obj match {
          case Full(v) => accessRemovable(v, u)
          case _ => false
        })


        if(allRelevantPrivsRevokable) {
          relevantAccountPrivs.foreach(_.delete_!)
          Full(true)
        } else {
          Failure("One of the views this user has access to is the owner view, and there would be no one with access" +
            " to this owner view if access to the user was revoked. No permissions to any views on the account have been revoked.")
        }

      }
      case u: User => {
        logger.error("APIUser instance not found, could not revoke access ")
        Empty
      }
    }
  }

  def view(viewId : ViewId, account: BankAccount) : Box[View] = {
    view(viewId, account.accountId, account.bankId)
  }

  def view(viewId : ViewId, accountId: AccountId, bankId: BankId) : Box[View] = {
    viewImpl(viewId, accountId, bankId)
  }

  def viewImpl(viewId : ViewId, account: BankAccount) : Box[ViewImpl] = {
    viewImpl(viewId, account.accountId, account.bankId)
  }

  def viewImpl(viewId : ViewId, accountId: AccountId, bankId: BankId) : Box[ViewImpl] = {
      ViewImpl.find(
        By(ViewImpl.permalink_, viewId.value) ::
        ViewImpl.accountFilter(bankId, accountId): _*
      ) ~> APIFailure(s"View with permalink $viewId not found", 404)
  }

  def createView(bankAccount: BankAccount, view: ViewCreationJSON): Box[View] = {
    val newViewPermalink = {
      view.name.replaceAllLiterally(" ","").toLowerCase
    }

    val existing = ViewImpl.count(
        By(ViewImpl.permalink_, newViewPermalink) ::
        ViewImpl.accountFilter(bankAccount.bankId, bankAccount.accountId): _*
      ) == 1

    if(existing)
      Failure(s"There is already a view with permalink $newViewPermalink on this bank account")
    else {
      val createdView = ViewImpl.create.
        name_(view.name).
        permalink_(newViewPermalink).
        bankPermalink(bankAccount.bankId.value).
        accountPermalink(bankAccount.accountId.value)

      createdView.setFromViewData(view)
      Full(createdView.saveMe)
    }

  }

  def updateView(bankAccount : BankAccount, viewId: ViewId, viewUpdateJson : ViewUpdateData) : Box[View] = {

    for {
      view <- viewImpl(viewId, bankAccount)
    } yield {
      view.setFromViewData(viewUpdateJson)
      view.saveMe
    }
  }

  def removeView(viewId: ViewId, bankAccount: BankAccount): Box[Unit] = {

    if(viewId.value=="owner")
      Failure("you cannot delete the owner view")
    else {
      for {
        view <- viewImpl(viewId, bankAccount)
        if(view.delete_!)
      } yield {
      }
    }
  }

  def views(bankAccount : BankAccount) : Box[List[View]] = {
    Full(ViewImpl.findAll(ViewImpl.accountFilter(bankAccount.bankId, bankAccount.accountId): _*))
  }

  def permittedViews(user: User, bankAccount: BankAccount): List[View] = {

    user match {
      //TODO: fix this match stuff
      case u: APIUser=> {
        //TODO: do this more efficiently?
        val allUserPrivs = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
        val userNonPublicViewsForAccount = allUserPrivs.flatMap(p => {
          p.view.obj match {
            case Full(v) => if(
              !v.isPublic &&
              v.bankPermalink.get == bankAccount.bankId.value &&
              v.accountPermalink.get == bankAccount.accountId.value){
              Some(v)
            } else None
            case _ => None
          }
        })
        userNonPublicViewsForAccount ++ bankAccount.publicViews
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
    Full(ViewImpl.findAll(ViewImpl.accountFilter(bankAccount.bankId, bankAccount.accountId): _*).filter(v => {
      v.isPublic == true
    }))
  }

  def getAllPublicAccounts() : List[BankAccount] = {
    //TODO: do this more efficiently

    val bankAndAccountPermalinks : List[(String, String)] =
      ViewImpl.findAll(By(ViewImpl.isPublic_, true)).map(v =>
        (v.bankPermalink.get, v.accountPermalink.get)
      ).distinct //we remove duplicates here

    bankAndAccountPermalinks.map {
      case (bankPermalink, accountPermalink) => {
        Connector.connector.vend.getBankAccount(BankId(bankPermalink), AccountId(accountPermalink))
      }
    }.flatten
  }

  def getPublicBankAccounts(bank : Bank) : List[BankAccount] = {
    //TODO: do this more efficiently

    val accountPermalinks : List[String] =
      ViewImpl.findAll(By(ViewImpl.isPublic_, true), By(ViewImpl.bankPermalink, bank.id.value)).map(v => {
        v.accountPermalink.get
      }).distinct //we remove duplicates here

    accountPermalinks.map(accPerma => {
      Connector.connector.vend.getBankAccount(bank.id, AccountId(accPerma))
    }).flatten
  }

  /**
   * @param user
   * @return the bank accounts the @user can see (public + private if @user is Full, public if @user is Empty)
   */
  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccount] = {
    user match {
      case Full(theuser) => {
        //TODO: get rid of this match
        theuser match {
          case u : APIUser => {
            //TODO: this could be quite a bit more efficient...

            val publicViewBankAndAccountPermalinks = ViewImpl.findAll(By(ViewImpl.isPublic_, true)).map(v => {
              (v.bankPermalink.get, v.accountPermalink.get)
            }).distinct

            val userPrivileges : List[ViewPrivileges] = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
            val userNonPublicViews : List[ViewImpl] = userPrivileges.map(_.view.obj).flatten.filter(!_.isPublic)

            val nonPublicViewBankAndAccountPermalinks = userNonPublicViews.map(v => {
              (v.bankPermalink.get, v.accountPermalink.get)
            }).distinct //we remove duplicates here

            val visibleBankAndAccountPermalinks =
              (publicViewBankAndAccountPermalinks ++ nonPublicViewBankAndAccountPermalinks).distinct

            visibleBankAndAccountPermalinks.map {
              case(bankPermalink, accountPermalink) => {
                Connector.connector.vend.getBankAccount(BankId(bankPermalink), AccountId(accountPermalink))
              }
            }.flatten
          }
          case _ => {
            logger.error("APIUser instance not found, could not get all accounts user can see")
            Nil
          }
        }

      }
      case _ => getAllPublicAccounts()
    }
  }

  /**
   * @param user
   * @return the bank accounts at @bank the @user can see (public + private if @user is Full, public if @user is Empty)
   */
  //TODO: remove Box in result
  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : Box[List[BankAccount]] = {
    user match {
      case Full(theuser) => {

        //TODO: get rid of this match
        theuser match {
          case u : APIUser => {
            //TODO: this could be quite a bit more efficient...

            val publicViewBankAndAccountPermalinks = ViewImpl.findAll(By(ViewImpl.isPublic_, true),
              By(ViewImpl.bankPermalink, bank.id.value)).map(v => {
              (v.bankPermalink.get, v.accountPermalink.get)
            }).distinct

            val userPrivileges : List[ViewPrivileges] = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
            val userNonPublicViews : List[ViewImpl] = userPrivileges.map(_.view.obj).flatten.filter(v => {
              !v.isPublic && v.bankPermalink.get == bank.id.value
            })

            val nonPublicViewBankAndAccountPermalinks = userNonPublicViews.map(v => {
              (v.bankPermalink.get, v.accountPermalink.get)
            }).distinct //we remove duplicates here

            val visibleBankAndAccountPermalinks =
              (publicViewBankAndAccountPermalinks ++ nonPublicViewBankAndAccountPermalinks).distinct

            Full(visibleBankAndAccountPermalinks.map {
              case(bankPermalink, accountPermalink) => {
                Connector.connector.vend.getBankAccount(BankId(bankPermalink), AccountId(accountPermalink))
              }
            }.flatten)
          }
          case _ => {
            logger.error("APIUser instance not found, could not get all accounts user can see")
            Full(Nil)
          }
        }
      }
      case _ => Full(getPublicBankAccounts(bank))
    }
  }

  /**
   * @return the bank accounts where the user has at least access to a non public view (is_public==false)
   */
  def getNonPublicBankAccounts(user : User) :  Box[List[BankAccount]] = {

    val accountsList =
    //TODO: get rid of this match statement
      user match {
        case u : APIUser => {
          //TODO: get rid of dependency on ViewPrivileges, ViewImpl
          //TODO: make this more efficient
          val userPrivileges : List[ViewPrivileges] = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
          val userNonPublicViews : List[ViewImpl] = userPrivileges.map(_.view.obj).flatten.filter(!_.isPublic)

          val nonPublicViewBankAndAccountPermalinks = userNonPublicViews.map(v => {
            (v.bankPermalink.get, v.accountPermalink.get)
          }).distinct //we remove duplicates here

          nonPublicViewBankAndAccountPermalinks.map {
            case(bankPermalink, accountPermalink) => {
              Connector.connector.vend.getBankAccount(BankId(bankPermalink), AccountId(accountPermalink))
            }
          }
        }
        case u: User => {
          logger.error("APIUser instance not found, could not find the non public accounts")
          Nil
        }
      }
    Full(accountsList.flatten)
  }

  /**
   * @return the bank accounts where the user has at least access to a non public view (is_public==false) for a specific bank
   */
  def getNonPublicBankAccounts(user : User, bankId : BankId) :  Box[List[BankAccount]] = {
    user match {
      case u : APIUser => {

        val userPrivileges : List[ViewPrivileges] = ViewPrivileges.findAll(By(ViewPrivileges.user, u))
        val userNonPublicViewsForBank : List[ViewImpl] =
          userPrivileges.map(_.view.obj).flatten.filter(v => !v.isPublic && v.bankPermalink.get == bankId.value)

        val nonPublicViewAccountPermalinks = userNonPublicViewsForBank.
          map(_.accountPermalink.get).distinct //we remove duplicates here

        Full(nonPublicViewAccountPermalinks.map( permalink =>
          Connector.connector.vend.getBankAccount(bankId, AccountId(permalink))
        ).flatten)
      }
      case u : User => {
        logger.error("APIUser instance not found, could not find the non public account ")
        Full(Nil)
      }
    }
  }

}