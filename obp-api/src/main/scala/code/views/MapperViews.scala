package code.views

import bootstrap.liftweb.ToSchemify
import code.accountholders.MapperAccountHolders
import code.api.APIFailure
import code.api.Constant._
import code.api.util.APIUtil
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.util.Helper.MdcLoggable
import code.views.system.ViewDefinition.create
import code.views.system.{AccountAccess, ViewDefinition}
import com.openbankproject.commons.model.{UpdateViewJSON, _}
import net.liftweb.common._
import net.liftweb.mapper.{Ascending, By, ByList, NullRef, OrderBy, PreCache, Schemifier}
import net.liftweb.util.Helpers._
import net.liftweb.util.StringHelpers

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.immutable
import scala.concurrent.Future

//TODO: Replace BankAccountUIDs with bankPermalink + accountPermalink


object MapperViews extends Views with MdcLoggable {

  Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.modelsRemotedata: _*)
  
  private def getViewsForUser(user: User): List[View] = {
    val privileges = AccountAccess.findAll(
      By(AccountAccess.user_fk, user.userPrimaryKey.value),
      OrderBy(AccountAccess.bank_id, Ascending),
      OrderBy(AccountAccess.account_id, Ascending)
    )
    getViewsCommonPart(privileges)
  }  
  private def getViewsForUserAndAccount(user: User, account : BankIdAccountId): List[View] = {
    val privileges = AccountAccess.findAll(
      By(AccountAccess.user_fk, user.userPrimaryKey.value),
      By(AccountAccess.bank_id, account.bankId.value),
      By(AccountAccess.account_id, account.accountId.value)
    )
    getViewsCommonPart(privileges)
  }

  private def getViewsCommonPart(privileges: List[AccountAccess]): List[View] = {
    val views: List[ViewDefinition] = privileges.flatMap(
      a => 
        ViewDefinition.find(By(ViewDefinition.id_, a.view_fk.get))
        .map(v => v.bank_id(a.bank_id.get).account_id(a.account_id.get))
    ).filter(
        v =>
          if (ALLOW_PUBLIC_VIEWS) {
            true // All views
          } else {
            v.isPrivate == true // Only private views
          }
      )
    views
  }

  def permissions(account : BankIdAccountId) : List[Permission] = {
    
    val users = AccountAccess.findAll(
      By(AccountAccess.bank_id, account.bankId.value),
      By(AccountAccess.account_id, account.accountId.value)
    ).flatMap(_.user_fk.obj.toList).distinct
    
    for {
      user <- users
    } yield {
      Permission(user, getViewsForUserAndAccount(user, account))
    }
  }

  def permission(account: BankIdAccountId, user: User): Box[Permission] = {
    Full(Permission(user, getViewsForUserAndAccount(user, account)))
  }

  def getPermissionForUser(user: User): Box[Permission] = {
    Full(Permission(user, getViewsForUser(user)))
  }
  // This is an idempotent function
  private def getOrGrantAccessToCustomView(user: User, viewDefinition: View, bankId: String, accountId: String): Box[View] = {
    if (AccountAccess.count(
      By(AccountAccess.user_fk, user.userPrimaryKey.value), 
      By(AccountAccess.bank_id, bankId), 
      By(AccountAccess.account_id, accountId), 
      By(AccountAccess.view_fk, viewDefinition.id)) == 0) {
      //logger.debug(s"saving ViewPrivileges for user ${user.resourceUserId.value} for view ${vImpl.id}")
      // SQL Insert ViewPrivileges
      val saved = AccountAccess.create.
        user_fk(user.userPrimaryKey.value).
        bank_id(bankId).
        account_id(accountId).
        view_id(viewDefinition.viewId.value).
        view_fk(viewDefinition.id).
        save
      if (saved) {
        //logger.debug("saved ViewPrivileges")
        Full(viewDefinition)
      } else {
        //logger.debug("failed to save ViewPrivileges")
        Empty ~> APIFailure("Server error adding permission", 500) //TODO: move message + code logic to api level
      }
    } else Full(viewDefinition) //privilege already exists, no need to create one
  }
  // This is an idempotent function 
  private def getOrGrantAccessToSystemView(bankId: BankId, accountId: AccountId, user: User, view: View): Box[View] = {
    getOrGrantAccessToCustomView(user, view, bankId.value, accountId.value)
  }
  // TODO Accept the whole view as a parameter so we don't have to select it here.
  def grantAccessToCustomView(viewIdBankIdAccountId: ViewIdBankIdAccountId, user: User): Box[View] = {
    logger.debug(s"addPermission says viewUID is $viewIdBankIdAccountId user is $user")
    val viewId = viewIdBankIdAccountId.viewId.value
    val bankId = viewIdBankIdAccountId.bankId.value
    val accountId = viewIdBankIdAccountId.accountId.value
    val viewDefinition = ViewDefinition.findCustomView(bankId, accountId, viewId)

    viewDefinition match {
      case Full(v) => {
        if(v.isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)
        // SQL Select Count ViewPrivileges where
        // This is idempotent
        getOrGrantAccessToCustomView(user, v, viewIdBankIdAccountId.bankId.value, viewIdBankIdAccountId.accountId.value) //privilege already exists, no need to create one
      }
      case _ => {
        Empty ~> APIFailure(s"View $viewIdBankIdAccountId. not found", 404) //TODO: move message + code logic to api level
      }
    }
  }
  def grantAccessToSystemView(bankId: BankId, accountId: AccountId, view: View, user: User): Box[View] = {
    { view.isPublic && !ALLOW_PUBLIC_VIEWS } match {
      case true => Failure(PublicViewsNotAllowedOnThisInstance)
      case false => getOrGrantAccessToSystemView(bankId: BankId, accountId: AccountId, user, view)
    }
  }

  def grantAccessToMultipleViews(views: List[ViewIdBankIdAccountId], user: User): Box[List[View]] = {
    val viewDefinitions: List[(ViewDefinition, ViewIdBankIdAccountId)] = views.map {
      uid => ViewDefinition.findCustomView(uid.bankId.value,uid.accountId.value, uid.viewId.value).map((_, uid))
          .or(ViewDefinition.findSystemView(uid.viewId.value).map((_, uid)))
    }.collect { case Full(v) => v}

    if (viewDefinitions.size != views.size) {
      val failMsg = s"not all viewimpls could be found for views ${viewDefinitions} (${viewDefinitions.size} != ${views.size}"
      //logger.debug(failMsg)
      Failure(failMsg) ~>
        APIFailure(s"One or more views not found", 404) //TODO: this should probably be a 400, but would break existing behaviour
      //TODO: APIFailures with http response codes belong at a higher level in the code
    } else {
      viewDefinitions.foreach(v => {
        if(v._1.isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)
        val viewDefinition = v._1
        val viewIdBankIdAccountId = v._2
        // This is idempotent 
        getOrGrantAccessToCustomView(user, viewDefinition, viewIdBankIdAccountId.bankId.value, viewIdBankIdAccountId.accountId.value)
      })
      Full(viewDefinitions.map(_._1))
    }
  }
  def revokeAccessToMultipleViews(views: List[ViewIdBankIdAccountId], user: User): Box[List[View]] = {
    val viewDefinitions: List[(ViewDefinition, ViewIdBankIdAccountId)] = views.map {
      uid => ViewDefinition.findCustomView(uid.bankId.value,uid.accountId.value, uid.viewId.value).map((_, uid))
          .or(ViewDefinition.findSystemView(uid.viewId.value).map((_, uid)))
    }.collect { case Full(v) => v}

    if (viewDefinitions.size != views.size) {
      val failMsg = s"not all viewimpls could be found for views ${viewDefinitions} (${viewDefinitions.size} != ${views.size}"
      //logger.debug(failMsg)
      Failure(failMsg) ~>
        APIFailure(s"One or more views not found", 404) //TODO: this should probably be a 400, but would break existing behaviour
      //TODO: APIFailures with http response codes belong at a higher level in the code
    } else {
      viewDefinitions.foreach(v => {
        if(v._1.isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)
        val viewDefinition = v._1
        val viewIdBankIdAccountId = v._2
        // This is idempotent 
        revokeAccess(v._2, user)
      })
      Full(viewDefinitions.map(_._1))
    }
  }

  def revokeAccess(viewUID : ViewIdBankIdAccountId, user : User) : Box[Boolean] = {
    val isRevokedCustomViewAccess =
    for {
      viewDefinition <- ViewDefinition.findCustomView(viewUID.bankId.value, viewUID.accountId.value, viewUID.viewId.value)
      accountAccess  <- AccountAccess.find(
        By(AccountAccess.user_fk, user.userPrimaryKey.value),
        By(AccountAccess.view_fk, viewDefinition.id)
      ) ?~! CannotFindAccountAccess
      // Check if we are allowed to remove the View from the User
      _ <- canRevokeAccessAsBox(viewDefinition, user)
    } yield {
      accountAccess.delete_!
    }
    val isRevokedSystemViewAccess =
      for {
        viewDefinition <- ViewDefinition.findSystemView(viewUID.viewId.value)
        accountAccess  <- AccountAccess.find(
          By(AccountAccess.user_fk, user.userPrimaryKey.value),
          By(AccountAccess.view_fk, viewDefinition.id)
        ) ?~! CannotFindAccountAccess
        // Check if we are allowed to remove the View from the User
        _ <- canRevokeAccessAsBox(viewDefinition, user)
      } yield {
        accountAccess.delete_!
      }
    
    //For the app, there is no difference to see the two views here.
    //The following mean: it should revoke both, but if one of them is failed, it is also should return true.
    isRevokedCustomViewAccess or isRevokedSystemViewAccess
  }
  def revokeAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user : User) : Box[Boolean] = {
    val res =
    for {
      viewDefinition <- ViewDefinition.find(By(ViewDefinition.id_, view.id)) 
      aa  <- AccountAccess.find(
        By(AccountAccess.user_fk, user.userPrimaryKey.value),
        By(AccountAccess.bank_id, bankId.value),
        By(AccountAccess.account_id, accountId.value),
        By(AccountAccess.view_fk, viewDefinition.id)) ?~! CannotFindAccountAccess
      // Check if we are allowed to remove the View from the User
      _ <- canRevokeAccessAsBox(viewDefinition, user)
    } yield {
      aa.delete_!
    }
    res
  }

  //returns Full if deletable, Failure if not
  def canRevokeAccessAsBox(viewImpl : ViewDefinition, user : User) : Box[Unit] = {
    if(canRevokeAccess(viewImpl, user)) Full(Unit)
    else Failure("access cannot be revoked")
  }


  def canRevokeAccess(viewDefinition: ViewDefinition, user : User) : Boolean = {
    if(viewDefinition.viewId == ViewId(CUSTOM_OWNER_VIEW_ID)) {
      //if the user is an account holder, we can't revoke access to the owner view
      val accountHolders = MapperAccountHolders.getAccountHolders(viewDefinition.bankId, viewDefinition.accountId)
      if(accountHolders.map(h => h.userPrimaryKey).contains(user.userPrimaryKey)) {
        false
      } else {
        // if it's the owner view, we can only revoke access if there would then still be someone else
        // with access
        AccountAccess.findAll(By(AccountAccess.view_fk, viewDefinition.id)).length > 1
      }
    } else {
      true
    }
  }


  /*
  This removes the link between a User and a View (Account Access)
   */

  def revokeAllAccountAccesses(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = {
    //TODO: make this more efficient by using one query (with a join)
    val allUserPrivs = AccountAccess.findAll(By(AccountAccess.user_fk, user.userPrimaryKey.value))

    val relevantAccountPrivs = allUserPrivs.filter(p => p.bank_id == bankId.value && p.account_id == accountId.value)

    val allRelevantPrivsRevokable = relevantAccountPrivs.forall( p => p.view_fk.obj match {
      case Full(v) => canRevokeAccess(v, user)
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

  def revokeAccountAccessesByUser(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = {
    canRevokeAccessToViewCommon(bankId, accountId, user) match {
      case true =>
        val permissions = AccountAccess.findAll(
          By(AccountAccess.user_fk, user.userPrimaryKey.value),
          By(AccountAccess.bank_id, bankId.value),
          By(AccountAccess.account_id, accountId.value)
        )
        permissions.foreach(_.delete_!)
        Full(true)
      case false =>
        Failure(CannotRevokeAccountAccess)
    }
  }

  def customView(viewId : ViewId, account: BankIdAccountId) : Box[View] = {
    val view = ViewDefinition.findCustomView(account.bankId.value, account.accountId.value, viewId.value)
    if(view.isDefined && view.openOrThrowException(attemptedToOpenAnEmptyBox).isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)

    view
  }

  def customViewFuture(viewId : ViewId, account: BankIdAccountId) : Future[Box[View]] = {
    Future {
      customView(viewId, account)
    }
  }
  def systemView(viewId : ViewId) : Box[View] = {
    ViewDefinition.findSystemView(viewId.value)
  }
  def systemViewFuture(viewId : ViewId) : Future[Box[View]] = {
    Future {
      systemView(viewId)
    }
  }
  
  def getNewViewPermalink(name: String) = {
    name.replaceAllLiterally(" ", "").toLowerCase
  }
  /*
  Create View based on the Specification (name, alias behavior, what fields can be seen, actions are allowed etc. )
  * */
  def createSystemView(view: CreateViewJson) : Future[Box[View]] = Future {
    if(view.is_public) {
      Failure(SystemViewCannotBePublicError)
    } else {
      view.name.contentEquals("") match {
        case true => 
          Failure(EmptyNameOfSystemViewError)
        case false =>
          //view-permalink is view.name without spaces and lowerCase.  (view.name = my life) <---> (view-permalink = mylife)
          val newViewPermalink = getNewViewPermalink(view.name)
          val existing = ViewDefinition.count(
            By(ViewDefinition.view_id, newViewPermalink), 
            NullRef(ViewDefinition.bank_id),
            NullRef(ViewDefinition.account_id)
          ) == 1

          existing match {
            case true =>
              Failure(s"$ExistingSystemViewError $newViewPermalink")
            case false =>
              val createdView = ViewDefinition.create.name_(view.name).view_id(newViewPermalink)
              createdView.setFromViewData(view)
              createdView.isSystem_(true)
              createdView.isPublic_(false)
              Full(createdView.saveMe)
          }
      }
    }
  }

  /*
  Create View based on the Specification (name, alias behavior, what fields can be seen, actions are allowed etc. )
  * */
  def createView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View] = {

    if(view.is_public && !ALLOW_PUBLIC_VIEWS) {
      return Failure(PublicViewsNotAllowedOnThisInstance)
    }

    if(view.name.contentEquals("")) {
      return Failure("You cannot create a View with an empty Name")
    }
    //view-permalink is view.name without spaces and lowerCase.  (view.name = my life) <---> (view-permalink = mylife)
    val newViewPermalink = getNewViewPermalink(view.name)

    val existing = ViewDefinition.count(
      By(ViewDefinition.view_id, newViewPermalink) ::
        ViewDefinition.accountFilter(bankAccountId.bankId, bankAccountId.accountId): _*
    ) == 1

    if (existing)
      Failure(s"There is already a view with permalink $newViewPermalink on this bank account")
    else {
      val createdView = ViewDefinition.create.
        name_(view.name).
        view_id(newViewPermalink).
        bank_id(bankAccountId.bankId.value).
        account_id(bankAccountId.accountId.value)

      createdView.setFromViewData(view)
      Full(createdView.saveMe)
    }
  }


  /* Update the specification of the view (what data/actions are allowed) */
  def updateCustomView(bankAccountId : BankIdAccountId, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] = {

    for {
      view <- ViewDefinition.findCustomView(bankAccountId.bankId.value, bankAccountId.accountId.value, viewId.value)
    } yield {
      view.setFromViewData(viewUpdateJson)
      view.saveMe
    }
  }
  /* Update the specification of the system view (what data/actions are allowed) */
  def updateSystemView(viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Future[Box[View]] = Future {
    for {
      view <- ViewDefinition.findSystemView(viewId.value)
    } yield {
      view.setFromViewData(viewUpdateJson)
      view.saveMe
    }
  }

  def removeCustomView(viewId: ViewId, bankAccountId: BankIdAccountId): Box[Boolean] = {
    for {
      view <- ViewDefinition.findCustomView(bankAccountId.bankId.value, bankAccountId.accountId.value, viewId.value)
      _ <- AccountAccess.find(By(AccountAccess.view_fk, view.id)).isDefined match {
        case true => Failure("Account Access record uses this View.") // We want to prevent account access orphans
        case false => Full()
      }
    } yield {
      view.delete_!
    }
  }
  def removeSystemView(viewId: ViewId): Future[Box[Boolean]] = Future {
    for {
      view <- ViewDefinition.findSystemView(viewId.value)
      _ <- AccountAccess.find(By(AccountAccess.view_fk, view.id)).isDefined match {
        case true => Failure("Account Access record uses this View.") // We want to prevent account access orphans
        case false => Full()
      }
    } yield {
      view.delete_!
    }
  }

  def assignedViewsForAccount(bankAccountId : BankIdAccountId) : List[View] = {
    AccountAccess.findAll(
      By(AccountAccess.bank_id, bankAccountId.bankId.value),
      By(AccountAccess.account_id, bankAccountId.accountId.value),
      PreCache(AccountAccess.view_fk)
    ).map(_.view_fk.obj).flatten.distinct
  }
  
  def availableViewsForAccount(bankAccountId : BankIdAccountId) : List[View] = {
    ViewDefinition.findAll(
      By(ViewDefinition.bank_id, bankAccountId.bankId.value), 
      By(ViewDefinition.account_id, bankAccountId.accountId.value)) ::: // Custom views
     ViewDefinition.findAll(
       By(ViewDefinition.bank_id, bankAccountId.bankId.value),
       NullRef(ViewDefinition.account_id),
       By(ViewDefinition.isSystem_, true)) ::: // Bank specific system views
     ViewDefinition.findAll(
       NullRef(ViewDefinition.bank_id),
       NullRef(ViewDefinition.account_id), 
       By(ViewDefinition.isSystem_, true)) // Sandbox specific System views
  }
  
  def publicViews: (List[View], List[AccountAccess]) = {
    if (APIUtil.ALLOW_PUBLIC_VIEWS) {
      val publicViews = ViewDefinition.findAll(By(ViewDefinition.isPublic_, true)) // Custom and System views
      val publicAccountAccesses = AccountAccess.findAll(ByList(AccountAccess.view_fk, publicViews.map(_.id)))
      (publicViews, publicAccountAccesses)
    } else {
      (Nil, Nil)
    }
  }
  
  def publicViewsForBank(bankId: BankId): (List[View], List[AccountAccess]) ={
    if (APIUtil.ALLOW_PUBLIC_VIEWS) {
      val publicViews = 
        ViewDefinition.findAll(By(ViewDefinition.isPublic_, true), By(ViewDefinition.bank_id, bankId.value), By(ViewDefinition.isSystem_, false)) ::: // Custom views
        ViewDefinition.findAll(By(ViewDefinition.isPublic_, true), By(ViewDefinition.isSystem_, true)) ::: // System views
        ViewDefinition.findAll(By(ViewDefinition.isPublic_, true), By(ViewDefinition.bank_id, bankId.value), By(ViewDefinition.isSystem_, true)) // System views
      val publicAccountAccesses = AccountAccess.findAll(ByList(AccountAccess.view_fk, publicViews.map(_.id)))
      (publicViews.distinct, publicAccountAccesses)
    } else {
      (Nil, Nil)
    }
  }
  
  def privateViewsUserCanAccess(user: User): (List[View], List[AccountAccess]) ={
    val accountAccesses = AccountAccess.findAll(
      By(AccountAccess.user_fk, user.userPrimaryKey.value),
      PreCache(AccountAccess.view_fk)
    ).filter(r => r.view_fk.obj.isDefined && r.view_fk.obj.map(_.isPrivate).getOrElse(false) == true)
    val privateViews  = accountAccesses.map(_.view_fk.obj).flatten.distinct
    (privateViews, accountAccesses)
  }  
  def privateViewsUserCanAccessAtBank(user: User, bankId: BankId): (List[View], List[AccountAccess]) ={
    val accountAccesses = AccountAccess.findAll(
      By(AccountAccess.user_fk, user.userPrimaryKey.value),
      By(AccountAccess.bank_id, bankId.value)
    ).filter(r => r.view_fk.obj.isDefined && r.view_fk.obj.map(_.isPrivate).getOrElse(false) == true)
    PrivateViewsUserCanAccessCommon(accountAccesses)
  }

  private def PrivateViewsUserCanAccessCommon(accountAccesses: List[AccountAccess]): (List[ViewDefinition], List[AccountAccess]) = {
    val listOfTuples: List[(AccountAccess, Box[ViewDefinition])] = accountAccesses.map(x => (x, x.view_fk.obj))
    val privateViews = listOfTuples.flatMap(
      tuple => tuple._2.map(v => v.bank_id(tuple._1.bank_id.get).account_id(tuple._1.account_id.get))
    )
    (privateViews, accountAccesses)
  }

  def privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId) : List[View] =   {
    val accountAccesses = AccountAccess.findAll(
      By(AccountAccess.user_fk, user.userPrimaryKey.value),
      By(AccountAccess.bank_id, bankIdAccountId.bankId.value),
      By(AccountAccess.account_id, bankIdAccountId.accountId.value),
      PreCache(AccountAccess.view_fk)
    )
    accountAccesses.map(_.view_fk.obj).flatten.filter(view => view.isPrivate == true).distinct
  }

  /**
    * @param bankIdAccountId the IncomingAccount from Kafka
    * @param viewId This field should be selected one from Owner/Public/Accountant/Auditor, only support
    * these four values.
    * @return  This will insert a View (e.g. the owner view) for an Account (BankAccount), and return the view
    * Note:
    * updateUserAccountViews would call createAccountView once per View specified in the IncomingAccount from Kafka.
    * We should cache this function because the available views on an account will change rarely.
    *
    */
  def getOrCreateAccountView(bankIdAccountId: BankIdAccountId, viewId: String): Box[View] = {

    val bankId = bankIdAccountId.bankId
    val accountId = bankIdAccountId.accountId
    val ownerView = SYSTEM_OWNER_VIEW_ID.equals(viewId.toLowerCase)
    val publicView = CUSTOM_PUBLIC_VIEW_ID.equals(viewId.toLowerCase)
    val accountantsView = SYSTEM_ACCOUNTANT_VIEW_ID.equals(viewId.toLowerCase)
    val auditorsView = SYSTEM_AUDITOR_VIEW_ID.equals(viewId.toLowerCase)
    
    val theView =
      if (ownerView)
        getOrCreateSystemView(SYSTEM_OWNER_VIEW_ID)
      else if (publicView)
        getOrCreateCustomPublicView(bankId, accountId, "Public View")
      else if (accountantsView)
        getOrCreateSystemView(SYSTEM_ACCOUNTANT_VIEW_ID)
      else if (auditorsView)
        getOrCreateSystemView(SYSTEM_AUDITOR_VIEW_ID)
      else 
        Failure(ViewIdNotSupported+ s"Your input viewId is :$viewId")
    
    logger.debug(s"-->getOrCreateAccountView.${viewId } : ${theView} ")
    
    theView
  }
  
  def getOrCreateOwnerView(bankId: BankId, accountId: AccountId, description: String = "Owner View") : Box[View] = {
    getExistingView(bankId, accountId, CUSTOM_OWNER_VIEW_ID) match {
      case Empty => createDefaultOwnerView(bankId, accountId, description)
      case Full(v) => Full(v)
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }  
  def getOrCreateSystemView(name: String) : Box[View] = {
    getExistingSystemView(name) match {
      case Empty => createDefaultSystemView(name)
      case Full(v) => Full(v)
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }
  
  def getOrCreateFirehoseView(bankId: BankId, accountId: AccountId, description: String = "Firehose View") : Box[View] = {
    getExistingView(bankId, accountId, "firehose") match {
      case Empty => createDefaultFirehoseView(bankId, accountId, description)
      case Full(v) => Full(v)
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }

  def getOwners(view: View) : Set[User] = {
    val id: Long = ViewDefinition.findCustomView(view.uid.bankId.value, view.uid.accountId.value, view.uid.viewId.value)
      .or(ViewDefinition.findSystemView(view.viewId.value))
      .map(_.id).openOr(0)
    val privileges = AccountAccess.findAll(By(AccountAccess.view_fk, id))
    val users: List[User] = privileges.flatMap(_.user_fk.obj)
    users.toSet
  }

  def getOrCreateCustomPublicView(bankId: BankId, accountId: AccountId, description: String = "Public View") : Box[View] = {
    getExistingView(bankId, accountId, CUSTOM_PUBLIC_VIEW_ID) match {
      case Empty=> createDefaultPublicView(bankId, accountId, description)
      case Full(v)=> Full(v)
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }

  def getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String = "Accountants View") : Box[View] = {
    getExistingView(bankId, accountId, "accountant") match {
      case Empty => createDefaultAccountantsView(bankId, accountId, description)
      case Full(v) => Full(v)
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }

  def getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String = "Auditors View") : Box[View] = {
    getExistingView(bankId, accountId, "auditor") match {
      case Empty => createDefaultAuditorsView(bankId, accountId, description)
      case Full(v) => Full(v)
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }

  //Note: this method is only for scala-test,
  def createCustomRandomView(bankId: BankId, accountId: AccountId) : Box[View] = {
    val entity = ViewDefinition.create.
      isSystem_(false).
      isFirehose_(false).
      name_("_" + randomString(5)).
      metadataView_(CUSTOM_OWNER_VIEW_ID).
      description_(randomString(3)).
      view_id("_" + randomString(3)).
      isPublic_(false).
      bank_id(bankId.value).
      account_id(accountId.value).
      usePrivateAliasIfOneExists_(false).
      usePublicAliasIfOneExists_(false).
      hideOtherAccountMetadataIfAlias_(false).
      canSeeTransactionThisBankAccount_(true).
      canSeeTransactionOtherBankAccount_(true).
      canSeeTransactionMetadata_(true).
      canSeeTransactionDescription_(true).
      canSeeTransactionAmount_(true).
      canSeeTransactionType_(true).
      canSeeTransactionCurrency_(true).
      canSeeTransactionStartDate_(true).
      canSeeTransactionFinishDate_(true).
      canSeeTransactionBalance_(true).
      canSeeComments_(true).
      canSeeOwnerComment_(true).
      canSeeTags_(true).
      canSeeImages_(true).
      canSeeBankAccountOwners_(true).
      canSeeBankAccountType_(true).
      canSeeBankAccountBalance_(true).
      canSeeBankAccountCurrency_(true).
      canSeeBankAccountLabel_(true).
      canSeeBankAccountNationalIdentifier_(true).
      canSeeBankAccountSwift_bic_(true).
      canSeeBankAccountIban_(true).
      canSeeBankAccountNumber_(true).
      canSeeBankAccountBankName_(true).
      canSeeBankAccountBankPermalink_(true).
      canSeeOtherAccountNationalIdentifier_(true).
      canSeeOtherAccountSWIFT_BIC_(true).
      canSeeOtherAccountIBAN_(true).
      canSeeOtherAccountBankName_(true).
      canSeeOtherAccountNumber_(true)

    entity.
      canSeeOtherAccountMetadata_(true).
      canSeeOtherAccountKind_(true).
      canSeeMoreInfo_(true).
      canSeeUrl_(true).
      canSeeImageUrl_(true).
      canSeeOpenCorporatesUrl_(true).
      canSeeCorporateLocation_(true).
      canSeePhysicalLocation_(true).
      canSeePublicAlias_(true).
      canSeePrivateAlias_(true).
      canAddMoreInfo_(true).
      canAddURL_(true).
      canAddImageURL_(true).
      canAddOpenCorporatesUrl_(true).
      canAddCorporateLocation_(true).
      canAddPhysicalLocation_(true).
      canAddPublicAlias_(true).
      canAddPrivateAlias_(true).
      canDeleteCorporateLocation_(true).
      canDeletePhysicalLocation_(true).
      canEditOwnerComment_(true).
      canAddComment_(true).
      canDeleteComment_(true).
      canAddTag_(true).
      canDeleteTag_(true).
      canAddImage_(true).
      canDeleteImage_(true).
      canAddWhereTag_(true).
      canSeeWhereTag_(true).
      canDeleteWhereTag_(true).
      canSeeBankRoutingScheme_(true). //added following in V300
      canSeeBankRoutingAddress_(true).
      canSeeBankAccountRoutingScheme_(true).
      canSeeBankAccountRoutingAddress_(true).
      canSeeOtherBankRoutingScheme_(true).
      canSeeOtherBankRoutingAddress_(true).
      canSeeOtherAccountRoutingScheme_(true).
      canSeeOtherAccountRoutingAddress_(true).
      canAddTransactionRequestToOwnAccount_(false).//added following two for payments
      canAddTransactionRequestToAnyAccount_(false).
      canSeeBankAccountCreditLimit_(true).
      saveMe

    Full(entity)
  }
  

  def createDefaultFirehoseView(bankId: BankId, accountId: AccountId, name: String): Box[View] = {
    createAndSaveFirehoseView(bankId, accountId, "Firehose View")
  }
  
  def createDefaultOwnerView(bankId: BankId, accountId: AccountId, name: String): Box[View] = {
    createAndSaveOwnerView(bankId, accountId, "Owner View")
  }  
  def createDefaultSystemView(name: String): Box[View] = {
    createAndSaveSystemView(name)
  }

  def createDefaultPublicView(bankId: BankId, accountId: AccountId, name: String): Box[View] = {
    if(!ALLOW_PUBLIC_VIEWS) {
      return Failure(PublicViewsNotAllowedOnThisInstance)
    }
    createAndSaveDefaultPublicView(bankId, accountId, "Public View")
  }

  def createDefaultAccountantsView(bankId: BankId, accountId: AccountId, name: String): Box[View] = {
    createAndSaveDefaultAccountantsView(bankId, accountId, "Accountants View")
  }

  def createDefaultAuditorsView(bankId: BankId, accountId: AccountId, name: String): Box[View] = {
    createAndSaveDefaultAuditorsView(bankId, accountId, "Auditors View")
  }

  def getExistingView(bankId: BankId, accountId: AccountId, name: String): Box[View] = {
    val res = ViewDefinition.findCustomView(bankId.value, accountId.value, name)
    if(res.isDefined && res.openOrThrowException(attemptedToOpenAnEmptyBox).isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)
    res
  }
  def getExistingSystemView(name: String): Box[View] = {
    val res = ViewDefinition.findSystemView(name)
    if(res.isDefined && res.openOrThrowException(attemptedToOpenAnEmptyBox).isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)
    res
  }

  def removeAllPermissions(bankId: BankId, accountId: AccountId) : Boolean = {
    val views = ViewDefinition.findAll(
      By(ViewDefinition.bank_id, bankId.value),
      By(ViewDefinition.account_id, accountId.value)
    )
    var privilegesDeleted = true
    views.map (x => {
      privilegesDeleted &&= AccountAccess.bulkDelete_!!(By(AccountAccess.view_fk, x.id_.get))
    } )
      privilegesDeleted
  }

  def removeAllViews(bankId: BankId, accountId: AccountId) : Boolean = {
    ViewDefinition.bulkDelete_!!(
      By(ViewDefinition.bank_id, bankId.value),
      By(ViewDefinition.account_id, accountId.value)
    )
  }

  def bulkDeleteAllPermissionsAndViews() : Boolean = {
    ViewDefinition.bulkDelete_!!()
    AccountAccess.bulkDelete_!!()
    true
  }

  def unsavedOwnerView(bankId : BankId, accountId: AccountId, description: String) : ViewDefinition = {
    val entity = create
      .isSystem_(false)
      .isFirehose_(true) // TODO This should be set to false. i.e. Firehose views should be separate
      .bank_id(bankId.value)
      .account_id(accountId.value)
      .name_("Owner")
      .view_id(CUSTOM_OWNER_VIEW_ID)
      .description_(description)
      .isPublic_(false) //(default is false anyways)
      .usePrivateAliasIfOneExists_(false) //(default is false anyways)
      .usePublicAliasIfOneExists_(false) //(default is false anyways)
      .hideOtherAccountMetadataIfAlias_(false) //(default is false anyways)
      .canSeeTransactionThisBankAccount_(true)
      .canSeeTransactionOtherBankAccount_(true)
      .canSeeTransactionMetadata_(true)
      .canSeeTransactionDescription_(true)
      .canSeeTransactionAmount_(true)
      .canSeeTransactionType_(true)
      .canSeeTransactionCurrency_(true)
      .canSeeTransactionStartDate_(true)
      .canSeeTransactionFinishDate_(true)
      .canSeeTransactionBalance_(true)
      .canSeeComments_(true)
      .canSeeOwnerComment_(true)
      .canSeeTags_(true)
      .canSeeImages_(true)
      .canSeeBankAccountOwners_(true)
      .canSeeBankAccountType_(true)
      .canSeeBankAccountBalance_(true)
      .canSeeBankAccountCurrency_(true)
      .canSeeBankAccountLabel_(true)
      .canSeeBankAccountNationalIdentifier_(true)
      .canSeeBankAccountSwift_bic_(true)
      .canSeeBankAccountIban_(true)
      .canSeeBankAccountNumber_(true)
      .canSeeBankAccountBankName_(true)
      .canSeeBankAccountBankPermalink_(true)
      .canSeeOtherAccountNationalIdentifier_(true)
      .canSeeOtherAccountSWIFT_BIC_(true)
      .canSeeOtherAccountIBAN_(true)
      .canSeeOtherAccountBankName_(true)

    entity
      .canSeeOtherAccountNumber_(true)
      .canSeeOtherAccountMetadata_(true)
      .canSeeOtherAccountKind_(true)
      .canSeeMoreInfo_(true)
      .canSeeUrl_(true)
      .canSeeImageUrl_(true)
      .canSeeOpenCorporatesUrl_(true)
      .canSeeCorporateLocation_(true)
      .canSeePhysicalLocation_(true)
      .canSeePublicAlias_(true)
      .canSeePrivateAlias_(true)
      .canAddMoreInfo_(true)
      .canAddURL_(true)
      .canAddImageURL_(true)
      .canAddOpenCorporatesUrl_(true)
      .canAddCorporateLocation_(true)
      .canAddPhysicalLocation_(true)
      .canAddPublicAlias_(true)
      .canAddPrivateAlias_(true)
      .canAddCounterparty_(true)
      .canDeleteCorporateLocation_(true)
      .canDeletePhysicalLocation_(true)
      .canEditOwnerComment_(true)
      .canAddComment_(true)
      .canDeleteComment_(true)
      .canAddTag_(true)
      .canDeleteTag_(true)
      .canAddImage_(true)
      .canDeleteImage_(true)
      .canAddWhereTag_(true)
      .canSeeWhereTag_(true)
      .canDeleteWhereTag_(true)
      .canSeeBankRoutingScheme_(true) //added following in V300
      .canSeeBankRoutingAddress_(true)
      .canSeeBankAccountRoutingScheme_(true)
      .canSeeBankAccountRoutingAddress_(true)
      .canSeeOtherBankRoutingScheme_(true)
      .canSeeOtherBankRoutingAddress_(true)
      .canSeeOtherAccountRoutingScheme_(true)
      .canSeeOtherAccountRoutingAddress_(true)
      .canAddTransactionRequestToOwnAccount_(true) //added following two for payments
      .canAddTransactionRequestToAnyAccount_(true)
  }
  def unsavedSystemView(name: String) : ViewDefinition = {
    val entity = create
      .isSystem_(true)
      .isFirehose_(true) // TODO This should be set to false. i.e. Firehose views should be separate
      .bank_id(null)
      .account_id(null)
      .name_(StringHelpers.capify(name))
      .view_id(name)
      .description_(name)
      .isPublic_(false) //(default is false anyways)
      .usePrivateAliasIfOneExists_(false) //(default is false anyways)
      .usePublicAliasIfOneExists_(false) //(default is false anyways)
      .hideOtherAccountMetadataIfAlias_(false) //(default is false anyways)
      .canSeeTransactionThisBankAccount_(true)
      .canSeeTransactionOtherBankAccount_(true)
      .canSeeTransactionMetadata_(true)
      .canSeeTransactionDescription_(true)
      .canSeeTransactionAmount_(true)
      .canSeeTransactionType_(true)
      .canSeeTransactionCurrency_(true)
      .canSeeTransactionStartDate_(true)
      .canSeeTransactionFinishDate_(true)
      .canSeeTransactionBalance_(true)
      .canSeeComments_(true)
      .canSeeOwnerComment_(true)
      .canSeeTags_(true)
      .canSeeImages_(true)
      .canSeeBankAccountOwners_(true)
      .canSeeBankAccountType_(true)
      .canSeeBankAccountBalance_(true)
      .canSeeBankAccountCurrency_(true)
      .canSeeBankAccountLabel_(true)
      .canSeeBankAccountNationalIdentifier_(true)
      .canSeeBankAccountSwift_bic_(true)
      .canSeeBankAccountIban_(true)
      .canSeeBankAccountNumber_(true)
      .canSeeBankAccountBankName_(true)
      .canSeeBankAccountBankPermalink_(true)
      .canSeeOtherAccountNationalIdentifier_(true)
      .canSeeOtherAccountSWIFT_BIC_(true)
      .canSeeOtherAccountIBAN_(true)
      .canSeeOtherAccountBankName_(true)

    entity
      .canSeeOtherAccountNumber_(true)
      .canSeeOtherAccountMetadata_(true)
      .canSeeOtherAccountKind_(true)
      .canSeeMoreInfo_(true)
      .canSeeUrl_(true)
      .canSeeImageUrl_(true)
      .canSeeOpenCorporatesUrl_(true)
      .canSeeCorporateLocation_(true)
      .canSeePhysicalLocation_(true)
      .canSeePublicAlias_(true)
      .canSeePrivateAlias_(true)
      .canAddMoreInfo_(true)
      .canAddURL_(true)
      .canAddImageURL_(true)
      .canAddOpenCorporatesUrl_(true)
      .canAddCorporateLocation_(true)
      .canAddPhysicalLocation_(true)
      .canAddPublicAlias_(true)
      .canAddPrivateAlias_(true)
      .canAddCounterparty_(true)
      .canDeleteCorporateLocation_(true)
      .canDeletePhysicalLocation_(true)
      .canEditOwnerComment_(true)
      .canAddComment_(true)
      .canDeleteComment_(true)
      .canAddTag_(true)
      .canDeleteTag_(true)
      .canAddImage_(true)
      .canDeleteImage_(true)
      .canAddWhereTag_(true)
      .canSeeWhereTag_(true)
      .canDeleteWhereTag_(true)
      .canSeeBankRoutingScheme_(true) //added following in V300
      .canSeeBankRoutingAddress_(true)
      .canSeeBankAccountRoutingScheme_(true)
      .canSeeBankAccountRoutingAddress_(true)
      .canSeeOtherBankRoutingScheme_(true)
      .canSeeOtherBankRoutingAddress_(true)
      .canSeeOtherAccountRoutingScheme_(true)
      .canSeeOtherAccountRoutingAddress_(true)
      .canAddTransactionRequestToOwnAccount_(true) //added following two for payments
      .canAddTransactionRequestToAnyAccount_(true)
  }
  
  def unsavedFirehoseView(bankId : BankId, accountId: AccountId, description: String) : ViewDefinition = {
    val entity = create
      .isSystem_(true)
      .isFirehose_(true) // Of the autogenerated views, only firehose should be firehose (except public)
      .bank_id(bankId.value)
      .account_id(accountId.value)
      .name_("Firehose")
      .view_id("firehose")
      .description_(description)
      .isPublic_(false) //(default is false anyways)
      .usePrivateAliasIfOneExists_(false) //(default is false anyways)
      .usePublicAliasIfOneExists_(false) //(default is false anyways)
      .hideOtherAccountMetadataIfAlias_(false) //(default is false anyways)
      .canSeeTransactionThisBankAccount_(true)
      .canSeeTransactionOtherBankAccount_(true)
      .canSeeTransactionMetadata_(true)
      .canSeeTransactionDescription_(true)
      .canSeeTransactionAmount_(true)
      .canSeeTransactionType_(true)
      .canSeeTransactionCurrency_(true)
      .canSeeTransactionStartDate_(true)
      .canSeeTransactionFinishDate_(true)
      .canSeeTransactionBalance_(true)
      .canSeeComments_(true)
      .canSeeOwnerComment_(true)
      .canSeeTags_(true)
      .canSeeImages_(true)
      .canSeeBankAccountOwners_(true)
      .canSeeBankAccountType_(true)
      .canSeeBankAccountBalance_(true)
      .canSeeBankAccountCurrency_(true)
      .canSeeBankAccountLabel_(true)
      .canSeeBankAccountNationalIdentifier_(true)
      .canSeeBankAccountSwift_bic_(true)
      .canSeeBankAccountIban_(true)
      .canSeeBankAccountNumber_(true)
      .canSeeBankAccountBankName_(true)
      .canSeeBankAccountBankPermalink_(true)
      .canSeeOtherAccountNationalIdentifier_(true)
      .canSeeOtherAccountSWIFT_BIC_(true)
      .canSeeOtherAccountIBAN_(true)
      .canSeeOtherAccountBankName_(true)

    entity
      .canSeeOtherAccountNumber_(true)
      .canSeeOtherAccountMetadata_(true)
      .canSeeOtherAccountKind_(true)
      .canSeeMoreInfo_(true)
      .canSeeUrl_(true)
      .canSeeImageUrl_(true)
      .canSeeOpenCorporatesUrl_(true)
      .canSeeCorporateLocation_(true)
      .canSeePhysicalLocation_(true)
      .canSeePublicAlias_(true)
      .canSeePrivateAlias_(true)
      .canAddMoreInfo_(true)
      .canAddURL_(true)
      .canAddImageURL_(true)
      .canAddOpenCorporatesUrl_(true)
      .canAddCorporateLocation_(true)
      .canAddPhysicalLocation_(true)
      .canAddPublicAlias_(true)
      .canAddPrivateAlias_(true)
      .canAddCounterparty_(true)
      .canDeleteCorporateLocation_(true)
      .canDeletePhysicalLocation_(true)
      .canEditOwnerComment_(true)
      .canAddComment_(true)
      .canDeleteComment_(true)
      .canAddTag_(true)
      .canDeleteTag_(true)
      .canAddImage_(true)
      .canDeleteImage_(true)
      .canAddWhereTag_(true)
      .canSeeWhereTag_(true)
      .canDeleteWhereTag_(true)
      .canSeeBankRoutingScheme_(true) //added following in V300
      .canSeeBankRoutingAddress_(true)
      .canSeeBankAccountRoutingScheme_(true)
      .canSeeBankAccountRoutingAddress_(true)
      .canSeeOtherBankRoutingScheme_(true)
      .canSeeOtherBankRoutingAddress_(true)
      .canSeeOtherAccountRoutingScheme_(true)
      .canSeeOtherAccountRoutingAddress_(true)
      .canAddTransactionRequestToOwnAccount_(false) //added following two for payments
      .canAddTransactionRequestToAnyAccount_(false)
  }
  
  def createAndSaveFirehoseView(bankId : BankId, accountId: AccountId, description: String) : Box[View] = {
    val res = unsavedFirehoseView(bankId, accountId, description).saveMe
    Full(res)
  }
  
  def createAndSaveOwnerView(bankId : BankId, accountId: AccountId, description: String) : Box[View] = {
    val res = unsavedOwnerView(bankId, accountId, description).saveMe
    Full(res)
  }  
  def createAndSaveSystemView(name: String) : Box[View] = {
    val res = unsavedSystemView(name).saveMe
    Full(res)
  }

  def unsavedDefaultPublicView(bankId : BankId, accountId: AccountId, description: String) : ViewDefinition = {
    val entity = create.
      isSystem_(false).
      isFirehose_(true). // This View is public so it might as well be firehose too.
      name_("_Public").
      description_(description).
      view_id(CUSTOM_PUBLIC_VIEW_ID). //public is only for custom views
      isPublic_(true).
      bank_id(bankId.value).
      account_id(accountId.value).
      usePrivateAliasIfOneExists_(false).
      usePublicAliasIfOneExists_(true).
      hideOtherAccountMetadataIfAlias_(true).
      canSeeTransactionThisBankAccount_(true).
      canSeeTransactionOtherBankAccount_(true).
      canSeeTransactionMetadata_(true).
      canSeeTransactionDescription_(false).
      canSeeTransactionAmount_(true).
      canSeeTransactionType_(true).
      canSeeTransactionCurrency_(true).
      canSeeTransactionStartDate_(true).
      canSeeTransactionFinishDate_(true).
      canSeeTransactionBalance_(true).
      canSeeComments_(true).
      canSeeOwnerComment_(true).
      canSeeTags_(true).
      canSeeImages_(true).
      canSeeBankAccountOwners_(true).
      canSeeBankAccountType_(true).
      canSeeBankAccountBalance_(true).
      canSeeBankAccountCurrency_(true).
      canSeeBankAccountLabel_(true).
      canSeeBankAccountNationalIdentifier_(true).
      canSeeBankAccountIban_(true).
      canSeeBankAccountNumber_(true).
      canSeeBankAccountBankName_(true).
      canSeeBankAccountBankPermalink_(true).
      canSeeOtherAccountNationalIdentifier_(true).
      canSeeOtherAccountIBAN_(true).
      canSeeOtherAccountBankName_(true).
      canSeeOtherAccountNumber_(true).
      canSeeOtherAccountMetadata_(true).
      canSeeOtherAccountKind_(true)
    entity.
      canSeeMoreInfo_(true).
      canSeeUrl_(true).
      canSeeImageUrl_(true).
      canSeeOpenCorporatesUrl_(true).
      canSeeCorporateLocation_(true).
      canSeePhysicalLocation_(true).
      canSeePublicAlias_(true).
      canSeePrivateAlias_(true).
      canAddMoreInfo_(true).
      canAddURL_(true).
      canAddImageURL_(true).
      canAddOpenCorporatesUrl_(true).
      canAddCorporateLocation_(true).
      canAddPhysicalLocation_(true).
      canAddPublicAlias_(true).
      canAddPrivateAlias_(true).
      canAddCounterparty_(true).
      canDeleteCorporateLocation_(true).
      canDeletePhysicalLocation_(true).
      canEditOwnerComment_(true).
      canAddComment_(true).
      canDeleteComment_(true).
      canAddTag_(true).
      canDeleteTag_(true).
      canAddImage_(true).
      canDeleteImage_(true).
      canAddWhereTag_(true).
      canSeeWhereTag_(true).
      canSeeBankRoutingScheme_(true). //added following in V300
      canSeeBankRoutingAddress_(true).
      canSeeBankAccountRoutingScheme_(true).
      canSeeBankAccountRoutingAddress_(true).
      canSeeOtherBankRoutingScheme_(true).
      canSeeOtherBankRoutingAddress_(true).
      canSeeOtherAccountRoutingScheme_(true).
      canSeeOtherAccountRoutingAddress_(true).
      canAddTransactionRequestToOwnAccount_(false). //added following two for payments
      canAddTransactionRequestToAnyAccount_(false)
  }

  def createAndSaveDefaultPublicView(bankId : BankId, accountId: AccountId, description: String) : Box[View] = {
    if(!ALLOW_PUBLIC_VIEWS) {
      return Failure(PublicViewsNotAllowedOnThisInstance)
    }
    val res = unsavedDefaultPublicView(bankId, accountId, description).saveMe
    Full(res)
  }

  /*
 Accountants
   */

  def unsavedDefaultAccountantsView(bankId : BankId, accountId: AccountId, description: String) : ViewDefinition = {
    val entityt = create.
      isSystem_(false).
      isFirehose_(true). // TODO This should be set to false. i.e. Firehose views should be separate
      name_("Accountant"). // Use the singular form
      description_(description).
      view_id("accountant"). // Use the singular form
      isPublic_(false).
      bank_id(bankId.value).
      account_id(accountId.value).
      usePrivateAliasIfOneExists_(false).
      usePublicAliasIfOneExists_(true).
      hideOtherAccountMetadataIfAlias_(true).
      canSeeTransactionThisBankAccount_(true).
      canSeeTransactionOtherBankAccount_(true).
      canSeeTransactionMetadata_(true).
      canSeeTransactionDescription_(false).
      canSeeTransactionAmount_(true).
      canSeeTransactionType_(true).
      canSeeTransactionCurrency_(true).
      canSeeTransactionStartDate_(true).
      canSeeTransactionFinishDate_(true).
      canSeeTransactionBalance_(true).
      canSeeComments_(true).
      canSeeOwnerComment_(true).
      canSeeTags_(true).
      canSeeImages_(true).
      canSeeBankAccountOwners_(true).
      canSeeBankAccountType_(true).
      canSeeBankAccountBalance_(true).
      canSeeBankAccountCurrency_(true).
      canSeeBankAccountLabel_(true).
      canSeeBankAccountNationalIdentifier_(true).
      canSeeBankAccountIban_(true).
      canSeeBankAccountNumber_(true).
      canSeeBankAccountBankName_(true).
      canSeeBankAccountBankPermalink_(true).
      canSeeOtherAccountNationalIdentifier_(true).
      canSeeOtherAccountIBAN_(true).
      canSeeOtherAccountBankName_(true).
      canSeeOtherAccountNumber_(true).
      canSeeOtherAccountMetadata_(true).
      canSeeOtherAccountKind_(true)
    entityt.
      canSeeMoreInfo_(true).
      canSeeUrl_(true).
      canSeeImageUrl_(true).
      canSeeOpenCorporatesUrl_(true).
      canSeeCorporateLocation_(true).
      canSeePhysicalLocation_(true).
      canSeePublicAlias_(true).
      canSeePrivateAlias_(true).
      canAddMoreInfo_(true).
      canAddURL_(true).
      canAddImageURL_(true).
      canAddOpenCorporatesUrl_(true).
      canAddCorporateLocation_(true).
      canAddPhysicalLocation_(true).
      canAddPublicAlias_(true).
      canAddPrivateAlias_(true).
      canAddCounterparty_(true).
      canDeleteCorporateLocation_(true).
      canDeletePhysicalLocation_(true).
      canEditOwnerComment_(true).
      canAddComment_(true).
      canDeleteComment_(true).
      canAddTag_(true).
      canDeleteTag_(true).
      canAddImage_(true).
      canDeleteImage_(true).
      canAddWhereTag_(true).
      canSeeWhereTag_(true).
      canDeleteWhereTag_(true).
      canSeeBankRoutingScheme_(true). //added following in V300
      canSeeBankRoutingAddress_(true).
      canSeeBankAccountRoutingScheme_(true).
      canSeeBankAccountRoutingAddress_(true).
      canSeeOtherBankRoutingScheme_(true).
      canSeeOtherBankRoutingAddress_(true).
      canSeeOtherAccountRoutingScheme_(true).
      canSeeOtherAccountRoutingAddress_(true).
      canAddTransactionRequestToOwnAccount_(true). //added following two for payments
      canAddTransactionRequestToAnyAccount_(false)
  }

  def createAndSaveDefaultAccountantsView(bankId : BankId, accountId: AccountId, description: String) : Box[View] = {
    val res = unsavedDefaultAccountantsView(bankId, accountId, description).saveMe
    Full(res)
  }


  /*
Auditors
 */

  def unsavedDefaultAuditorsView(bankId : BankId, accountId: AccountId, description: String) : ViewDefinition = {
    val entity = create.
      isSystem_(false).
      isFirehose_(true). // TODO This should be set to false. i.e. Firehose views should be separate
      name_("Auditor"). // Use the singular form
      description_(description).
      view_id("auditor"). // Use the singular form
      isPublic_(false).
      bank_id(bankId.value).
      account_id(accountId.value).
      usePrivateAliasIfOneExists_(false).
      usePublicAliasIfOneExists_(true).
      hideOtherAccountMetadataIfAlias_(true).
      canSeeTransactionThisBankAccount_(true).
      canSeeTransactionOtherBankAccount_(true).
      canSeeTransactionMetadata_(true).
      canSeeTransactionDescription_(false).
      canSeeTransactionAmount_(true).
      canSeeTransactionType_(true).
      canSeeTransactionCurrency_(true).
      canSeeTransactionStartDate_(true).
      canSeeTransactionFinishDate_(true).
      canSeeTransactionBalance_(true).
      canSeeComments_(true).
      canSeeOwnerComment_(true).
      canSeeTags_(true).
      canSeeImages_(true).
      canSeeBankAccountOwners_(true).
      canSeeBankAccountType_(true).
      canSeeBankAccountBalance_(true).
      canSeeBankAccountCurrency_(true).
      canSeeBankAccountLabel_(true).
      canSeeBankAccountNationalIdentifier_(true).
      canSeeBankAccountIban_(true).
      canSeeBankAccountNumber_(true).
      canSeeBankAccountBankName_(true).
      canSeeBankAccountBankPermalink_(true).
      canSeeOtherAccountNationalIdentifier_(true).
      canSeeOtherAccountIBAN_(true).
      canSeeOtherAccountBankName_(true).
      canSeeOtherAccountNumber_(true).
      canSeeOtherAccountMetadata_(true)
    entity.
      canSeeOtherAccountKind_(true).
      canSeeMoreInfo_(true).
      canSeeUrl_(true).
      canSeeImageUrl_(true).
      canSeeOpenCorporatesUrl_(true).
      canSeeCorporateLocation_(true).
      canSeePhysicalLocation_(true).
      canSeePublicAlias_(true).
      canSeePrivateAlias_(true).
      canAddMoreInfo_(true).
      canAddURL_(true).
      canAddImageURL_(true).
      canAddOpenCorporatesUrl_(true).
      canAddCorporateLocation_(true).
      canAddPhysicalLocation_(true).
      canAddPublicAlias_(true).
      canAddPrivateAlias_(true).
      canAddCounterparty_(true).
      canDeleteCorporateLocation_(true).
      canDeletePhysicalLocation_(true).
      canEditOwnerComment_(true).
      canAddComment_(true).
      canDeleteComment_(true).
      canAddTag_(true).
      canDeleteTag_(true).
      canAddImage_(true).
      canDeleteImage_(true).
      canAddWhereTag_(true).
      canSeeWhereTag_(true).
      canDeleteWhereTag_(true).
      canSeeBankRoutingScheme_(true). //added following in V300
      canSeeBankRoutingAddress_(true).
      canSeeBankAccountRoutingScheme_(true).
      canSeeBankAccountRoutingAddress_(true).
      canSeeOtherBankRoutingScheme_(true).
      canSeeOtherBankRoutingAddress_(true).
      canSeeOtherAccountRoutingScheme_(true).
      canSeeOtherAccountRoutingAddress_(true).
      canAddTransactionRequestToOwnAccount_(false).//added following two for payments
      canAddTransactionRequestToAnyAccount_(false)
  }

  def createAndSaveDefaultAuditorsView(bankId : BankId, accountId: AccountId, description: String) : Box[View] = {
    val res = unsavedDefaultAuditorsView(bankId, accountId, description).saveMe
    Full(res)
  }

}
