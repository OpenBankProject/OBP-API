package code.views

import bootstrap.liftweb.ToSchemify
import code.accountholders.MapperAccountHolders
import code.api.APIFailure
import code.api.Constant._
import code.api.util.{APIUtil, CallContext}
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.util.Helper.MdcLoggable
import code.views.system.ViewDefinition.create
import code.views.system.{AccountAccess, ViewDefinition, ViewPermission}
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

  logger.debug("Run Schemifier.schemify in MapperViews object")
  Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.modelsRemotedata: _*)
  
  private def getViewsForUser(user: User): List[View] = {
    val accountAccessList = AccountAccess.findAll(
      By(AccountAccess.user_fk, user.userPrimaryKey.value),
      OrderBy(AccountAccess.bank_id, Ascending),
      OrderBy(AccountAccess.account_id, Ascending)
    )
    getViewsCommonPart(accountAccessList)
  }  
  private def getViewsForUserAndAccount(user: User, account : BankIdAccountId): List[View] = {
    val accountAccessList = AccountAccess.findAll(
      By(AccountAccess.user_fk, user.userPrimaryKey.value),
      By(AccountAccess.bank_id, account.bankId.value),
      By(AccountAccess.account_id, account.accountId.value)
    )
    getViewsCommonPart(accountAccessList)
  }

  private def getViewFromAccountAccess(accountAccess: AccountAccess) = {
    if (isValidSystemViewId(accountAccess.view_id.get)) {
      ViewDefinition.findSystemView(accountAccess.view_id.get)
        .map(v => v.bank_id(accountAccess.bank_id.get).account_id(accountAccess.account_id.get)) // in case system view do not contains the bankId, and accountId.
    } else {
      ViewDefinition.findCustomView(accountAccess.bank_id.get, accountAccess.account_id.get, accountAccess.view_id.get)
    }
  }
  
  private def getViewsCommonPart(accountAccessList: List[AccountAccess]): List[View] = {
    //we need to get views from accountAccess
    val views: List[ViewDefinition] = accountAccessList.flatMap(getViewFromAccountAccess).filter(
        v =>
          if (allowPublicViews) {
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

  def getViewByBankIdAccountIdViewIdUserPrimaryKey(bankIdAccountIdViewId: BankIdAccountIdViewId,  userPrimaryKey: UserPrimaryKey): Box[View] = {
    val accountAccessList = AccountAccess.findByBankIdAccountIdViewIdUserPrimaryKey(
      bankId = bankIdAccountIdViewId.bankId,
      accountId = bankIdAccountIdViewId.accountId,
      viewId = bankIdAccountIdViewId.viewId,
      userPrimaryKey = userPrimaryKey
    )
    accountAccessList.map(getViewFromAccountAccess).flatten
  }

  def getPermissionForUser(user: User): Box[Permission] = {
    Full(Permission(user, getViewsForUser(user)))
  }
  // This is an idempotent function
  private def getOrGrantAccessToViewCommon(user: User, viewDefinition: View, bankId: String, accountId: String): Box[View] = {
    if (AccountAccess.findByUniqueIndex(
      BankId(bankId),
      AccountId(accountId), 
      viewDefinition.viewId,
      user.userPrimaryKey, 
      ALL_CONSUMERS).isEmpty) {
      logger.debug(s"getOrGrantAccessToViewCommon AccountAccess.create" +
        s"user(UserId(${user.userId}), ViewId(${viewDefinition.viewId.value}), bankId($bankId), accountId($accountId), consumerId($ALL_CONSUMERS)")
      // SQL Insert AccountAccessList
      val saved = AccountAccess.create.
        user_fk(user.userPrimaryKey.value).
        bank_id(bankId).
        account_id(accountId).
        view_id(viewDefinition.viewId.value).
        consumer_id(ALL_CONSUMERS).
        save
      if (saved) {
        //logger.debug("saved AccountAccessList")
        Full(viewDefinition)
      } else {
        //logger.debug("failed to save AccountAccessList")
        Empty ~> APIFailure("Server error adding permission", 500) //TODO: move message + code logic to api level
      }
    } else {
      logger.debug(s"getOrGrantAccessToViewCommon AccountAccess is already existing (UserId(${user.userId}), ViewId(${viewDefinition.viewId.value}), bankId($bankId), accountId($accountId))")
      Full(viewDefinition)
    } //accountAccess already exists, no need to create one
  }
  // This is an idempotent function 
  private def getOrGrantAccessToSystemView(bankId: BankId, accountId: AccountId, user: User, view: View): Box[View] = {
    getOrGrantAccessToViewCommon(user, view, bankId.value, accountId.value)
  }
  // TODO Accept the whole view as a parameter so we don't have to select it here.
  def grantAccessToCustomView(bankIdAccountIdViewId: BankIdAccountIdViewId, user: User): Box[View] = {
    logger.debug(s"addPermission says viewUID is $bankIdAccountIdViewId user is $user")
    val viewId = bankIdAccountIdViewId.viewId.value
    val bankId = bankIdAccountIdViewId.bankId.value
    val accountId = bankIdAccountIdViewId.accountId.value
    val viewDefinition = ViewDefinition.findCustomView(bankId, accountId, viewId)

    viewDefinition match {
      case Full(v) => {
        if(v.isPublic && !allowPublicViews) return Failure(PublicViewsNotAllowedOnThisInstance)
        // SQL Select Count AccountAccessList where
        // This is idempotent
        getOrGrantAccessToViewCommon(user, v, bankIdAccountIdViewId.bankId.value, bankIdAccountIdViewId.accountId.value) //accountAccess already exists, no need to create one
      }
      case _ => {
        Empty ~> APIFailure(s"View $bankIdAccountIdViewId. not found", 404) //TODO: move message + code logic to api level
      }
    }
  }
  def grantAccessToSystemView(bankId: BankId, accountId: AccountId, view: View, user: User): Box[View] = {
    { view.isPublic && !allowPublicViews } match {
      case true => Failure(PublicViewsNotAllowedOnThisInstance)
      case false => getOrGrantAccessToSystemView(bankId: BankId, accountId: AccountId, user, view)
    }
  }

  def grantAccessToMultipleViews(views: List[BankIdAccountIdViewId], user: User, callContext: Option[CallContext]): Box[List[View]] = {
    val viewDefinitions: List[(ViewDefinition, BankIdAccountIdViewId)] = views.map {
      uid => ViewDefinition.findCustomView(uid.bankId.value,uid.accountId.value, uid.viewId.value).map((_, uid))
          .or(ViewDefinition.findSystemView(uid.viewId.value).map((_, uid)))
    }.collect { case Full(v) => v}

    if (viewDefinitions.size != views.size) {
      val failMsg = s"View definitions could be found only for views ${viewDefinitions.map(_._1.viewIdInternal)} Missing views: ${viewDefinitions.map(_._2).diff(views)}"
      //logger.debug(failMsg)
      Failure(failMsg) ~>
        APIFailure(s"One or more views not found", 404) //TODO: this should probably be a 400, but would break existing behaviour
      //TODO: APIFailures with http response codes belong at a higher level in the code
    } else {
      viewDefinitions.foreach(v => {
        if(v._1.isPublic && !allowPublicViews) return Failure(PublicViewsNotAllowedOnThisInstance)
        val viewDefinition = v._1
        val bankIdAccountIdViewId = v._2
        // This is idempotent 
        getOrGrantAccessToViewCommon(user, viewDefinition, bankIdAccountIdViewId.bankId.value, bankIdAccountIdViewId.accountId.value)
      })
      Full(viewDefinitions.map(_._1))
    }
  }
  def revokeAccessToMultipleViews(views: List[BankIdAccountIdViewId], user: User): Box[List[View]] = {
    val viewDefinitions: List[(ViewDefinition, BankIdAccountIdViewId)] = views.map {
      uid => ViewDefinition.findCustomView(uid.bankId.value,uid.accountId.value, uid.viewId.value).map((_, uid))
          .or(ViewDefinition.findSystemView(uid.viewId.value).map((_, uid)))
    }.collect { case Full(v) => v}

    if (viewDefinitions.size != views.size) {
      val failMsg = s"View definitions could be found only for views ${viewDefinitions.map(_._1.viewIdInternal)} Missing views: ${viewDefinitions.map(_._2).diff(views)}"
      //logger.debug(failMsg)
      Failure(failMsg) ~>
        APIFailure(s"One or more views not found", 404) //TODO: this should probably be a 400, but would break existing behaviour
      //TODO: APIFailures with http response codes belong at a higher level in the code
    } else {
      viewDefinitions.foreach(v => {
        if(v._1.isPublic && !allowPublicViews) return Failure(PublicViewsNotAllowedOnThisInstance)
        // This is idempotent 
        revokeAccess(v._2, user)
      })
      Full(viewDefinitions.map(_._1))
    }
  }

  def revokeAccess(bankIdAccountIdViewId : BankIdAccountIdViewId, user : User) : Box[Boolean] = {
    val isRevokedCustomViewAccess =
    for {
      customViewDefinition <- ViewDefinition.findCustomView(bankIdAccountIdViewId.bankId.value, bankIdAccountIdViewId.accountId.value, bankIdAccountIdViewId.viewId.value)
      accountAccess  <- AccountAccess.findByBankIdAccountIdViewIdUserPrimaryKey(
        bankIdAccountIdViewId.bankId,
        bankIdAccountIdViewId.accountId,
        bankIdAccountIdViewId.viewId,
        user.userPrimaryKey
      ) ?~! CannotFindAccountAccess
    } yield {
      accountAccess.delete_!
    }
    
    val isRevokedSystemViewAccess =
      for {
        systemViewDefinition <- ViewDefinition.findSystemView(bankIdAccountIdViewId.viewId.value)
        accountAccess  <- AccountAccess.findByBankIdAccountIdViewIdUserPrimaryKey(
          bankIdAccountIdViewId.bankId,
          bankIdAccountIdViewId.accountId,
          bankIdAccountIdViewId.viewId,
          user.userPrimaryKey
        ) ?~! CannotFindAccountAccess
        // Check if we are allowed to remove the View from the User
        _ <- canRevokeOwnerAccessAsBox(bankIdAccountIdViewId.bankId, bankIdAccountIdViewId.accountId,systemViewDefinition, user)
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
      systemViewDefinition <- ViewDefinition.find(By(ViewDefinition.id_, view.id))
      accountAccess  <- AccountAccess.findByBankIdAccountIdViewIdUserPrimaryKey(
        bankId,
        accountId,
        view.viewId,
        user.userPrimaryKey
      ) ?~! CannotFindAccountAccess
      // Check if we are allowed to remove the View from the User
      _ <- canRevokeOwnerAccessAsBox(bankId: BankId, accountId: AccountId, systemViewDefinition, user)
    } yield {
      accountAccess.delete_!
    }
    res
  }
  
  //Custom View will have bankId and accountId inside the `View`, so no need both in the parameters
  def revokeAccessToCustomViewForConsumer(view : View, consumerId : String) : Box[Boolean] = {
    for {
      customViewDefinition <- ViewDefinition.findCustomView(view.bankId.value, view.accountId.value, view.viewId.value)
      accountAccess  <- AccountAccess.findByBankIdAccountIdViewIdConsumerId(
        customViewDefinition.bankId,
        customViewDefinition.accountId,
        customViewDefinition.viewId,
        consumerId
      ) ?~! CannotFindAccountAccess
    } yield {
      accountAccess.delete_!
    }
  }
  
  //System View only have the viewId in inside the `View`, both bankId and accountId are empty in the `View`. So we need both in the parameters
  def revokeAccessToSystemViewForConsumer(bankId: BankId, accountId: AccountId, view : View, consumerId : String) : Box[Boolean] = {
    for {
      systemViewDefinition <- ViewDefinition.find(By(ViewDefinition.id_, view.id))
      accountAccess  <- AccountAccess.findByBankIdAccountIdViewIdConsumerId(
        bankId,
        accountId,
        systemViewDefinition.viewId,
        consumerId
      ) ?~! CannotFindAccountAccess
    } yield {
      accountAccess.delete_!
    }
  }

  //returns Full if deletable, Failure if not
  def canRevokeOwnerAccessAsBox(bankId: BankId, accountId: AccountId, viewImpl : ViewDefinition, user : User) : Box[Unit] = {
    if(canRevokeOwnerAccess(bankId: BankId, accountId: AccountId, viewImpl, user)) Full(Unit)
    else Failure("access cannot be revoked")
  }


  def canRevokeOwnerAccess(bankId: BankId, accountId: AccountId, viewDefinition: ViewDefinition, user : User) : Boolean = {
    if(viewDefinition.viewId == ViewId(SYSTEM_OWNER_VIEW_ID)) {
      //if the user is an account holder, we can't revoke access to the owner view
      val accountHolders = MapperAccountHolders.getAccountHolders(viewDefinition.bankId, viewDefinition.accountId)
      if(accountHolders.map(h => h.userPrimaryKey).contains(user.userPrimaryKey)) {
        false
      } else {
        // if it's the owner view, we can only revoke access if there would then still be someone else
        // with access
        AccountAccess.findAllByBankIdAccountIdViewId(
          bankId: BankId, 
          accountId: AccountId,
          viewDefinition.viewId
        ).length > 1
      }
    } else {
      true
    }
  }


  /**
   * remove all the accountAccess for one user and linked account.
   * we already has the guard `canRevokeAccessToAllViews` on the top level.
   */
  def revokeAllAccountAccess(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = {
    AccountAccess.find(
      By(AccountAccess.bank_id, bankId.value),
      By(AccountAccess.account_id, accountId.value),
      By(AccountAccess.user_fk, user.userPrimaryKey.value)
    ).foreach(_.delete_!)
    Full(true)
  }

  def revokeAccountAccessByUser(bankId : BankId, accountId: AccountId, user : User, callContext: Option[CallContext]) : Box[Boolean] = {
    canRevokeAccessToAllViews(bankId, accountId, user, callContext) match {
      case true =>
        val permissions = AccountAccess.findAll(
          By(AccountAccess.user_fk, user.userPrimaryKey.value),
          By(AccountAccess.bank_id, bankId.value),
          By(AccountAccess.account_id, accountId.value)
        )
        permissions.foreach(_.delete_!)
        Full(true)
      case false =>
        Failure(UserLacksPermissionCanRevokeAccessToViewForTargetAccount)
    }
  }

  def customView(viewId : ViewId, account: BankIdAccountId) : Box[View] = {
    val view = ViewDefinition.findCustomView(account.bankId.value, account.accountId.value, viewId.value)
    if(view.isDefined && view.openOrThrowException(attemptedToOpenAnEmptyBox).isPublic && !allowPublicViews) return Failure(PublicViewsNotAllowedOnThisInstance)

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
  def getSystemViews() : Future[List[View]] = {
    Future {
      ViewDefinition.findAll(
        NullRef(ViewDefinition.bank_id),
        NullRef(ViewDefinition.account_id),
        By(ViewDefinition.isSystem_, true)
      )
    }
  }
  def systemViewFuture(viewId : ViewId) : Future[Box[View]] = {
    Future {
      systemView(viewId)
    }
  }
  
  def createViewIdByName(name: String) = {
    name.replaceAllLiterally(" ", "").toLowerCase
  }
  /*
  Create View based on the Specification (name, alias behavior, what fields can be seen, actions are allowed etc. )
  * */
  def createSystemView(view: CreateViewJson) : Future[Box[View]] = Future {
    if(view.is_public) {
      Failure(SystemViewCannotBePublicError)
    }else if (!isValidSystemViewName(view.name)) {
      Failure(InvalidSystemViewFormat+s"Current view_name (${view.name})")
    } else {
      view.name.contentEquals("") match {
        case true => 
          Failure(EmptyNameOfSystemViewError)
        case false =>
          //view-permalink is view.name without spaces and lowerCase.  (view.name = my life) <---> (view-permalink = mylife)
          val viewId = createViewIdByName(view.name)
          val existing = ViewDefinition.count(
            By(ViewDefinition.view_id, viewId), 
            NullRef(ViewDefinition.bank_id),
            NullRef(ViewDefinition.account_id)
          ) == 1

          existing match {
            case true =>
              Failure(s"$SystemViewAlreadyExistsError Current VIEW_ID($viewId)")
            case false =>
              val createdView = ViewDefinition.create.name_(view.name).view_id(viewId)
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
  def createCustomView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View] = {

    if(!isValidCustomViewName(view.name)) {
      return Failure(InvalidCustomViewFormat)
    }
    
    if(view.is_public && !allowPublicViews) {
      return Failure(PublicViewsNotAllowedOnThisInstance)
    }

    if(view.name.contentEquals("")) {
      return Failure("You cannot create a View with an empty Name")
    }
    //view-permalink is view.name without spaces and lowerCase.  (view.name = my life) <---> (view-permalink = mylife)
    val viewId = createViewIdByName(view.name)

    val existing = ViewDefinition.count(
      By(ViewDefinition.view_id, viewId) ::
        ViewDefinition.accountFilter(bankAccountId.bankId, bankAccountId.accountId): _*
    ) == 1

    if (existing)
      Failure(s"$CustomViewAlreadyExistsError Current BankId(${bankAccountId.bankId.value}), AccountId(${bankAccountId.accountId.value}), ViewId($viewId).")
    else {
      val createdView = ViewDefinition.create.
        name_(view.name).
        view_id(viewId).
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
      customView <- ViewDefinition.findCustomView(bankAccountId.bankId.value, bankAccountId.accountId.value, viewId.value)
      _ <- AccountAccess.findAllByBankIdAccountIdViewId(
        bankAccountId.bankId,
        bankAccountId.accountId,
        viewId
      ).length > 0 match {
        case true => Failure("Account Access record uses this View.") // We want to prevent account access orphans
        case false => Full()
      }
    } yield {
      customView.delete_!
    }
  }
  def removeSystemView(viewId: ViewId): Future[Box[Boolean]] = Future {
    for {
      view <- ViewDefinition.findSystemView(viewId.value)
      _ <- AccountAccess.findAllBySystemViewId(viewId).length > 0 match {
        case true => Failure("Account Access record uses this View.") // We want to prevent account access orphans
        case false => Full()
      }
    } yield {
      view.delete_!
    }
  }

  def assignedViewsForAccount(bankAccountId : BankIdAccountId) : List[View] = {
    AccountAccess.findAllByBankIdAccountId(
      bankAccountId.bankId,
      bankAccountId.accountId
    ).map(getViewFromAccountAccess).flatten.distinct
  }
  
  //this is more like possible views, it contains the system views+custom views
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
  
  private def getAccountAccessFromPublicViews(publicViews: List[ViewDefinition])={
    val publicSystemViews = publicViews.filter(_.isSystem)
    val publicCustomViews = publicViews.filter(!_.isSystem)
    val publicSystemViewAccountAccess = AccountAccess.findAll(
      ByList(AccountAccess.view_id, publicSystemViews.map(_.viewId.value)),
    )
    val publicCustomViewAccountAccess = AccountAccess.findAll(
      ByList(AccountAccess.bank_id, publicCustomViews.map(_.bankId.value)),
      ByList(AccountAccess.account_id, publicCustomViews.map(_.accountId.value)),
      ByList(AccountAccess.view_id, publicCustomViews.map(_.viewId.value)),
    )
    publicCustomViewAccountAccess++publicSystemViewAccountAccess
  }
  def publicViews: (List[View], List[AccountAccess]) = {
    if (APIUtil.allowPublicViews) {
      val publicViews = ViewDefinition.findAll(By(ViewDefinition.isPublic_, true)) //Both Custom and System views
      val publicAccountAccess = getAccountAccessFromPublicViews(publicViews)
      (publicViews, publicAccountAccess)
    } else {
      (Nil, Nil)
    }
  }
  
  def publicViewsForBank(bankId: BankId): (List[View], List[AccountAccess]) ={
    if (APIUtil.allowPublicViews) {
      val publicViews = 
        ViewDefinition.findAll(By(ViewDefinition.isPublic_, true), By(ViewDefinition.bank_id, bankId.value), By(ViewDefinition.isSystem_, false)) ::: // Custom views
        ViewDefinition.findAll(By(ViewDefinition.isPublic_, true), By(ViewDefinition.isSystem_, true)) ::: // System views
        ViewDefinition.findAll(By(ViewDefinition.isPublic_, true), By(ViewDefinition.bank_id, bankId.value), By(ViewDefinition.isSystem_, true)) // System views
      val publicAccountAccess = getAccountAccessFromPublicViews(publicViews)
      (publicViews.distinct, publicAccountAccess)
    } else {
      (Nil, Nil)
    }
  }
  
  def privateViewsUserCanAccess(user: User): (List[View], List[AccountAccess]) ={
    val accountAccess = AccountAccess.findAllByUserPrimaryKey(user.userPrimaryKey)
    .filter(accountAccess => {
      val view = getViewFromAccountAccess(accountAccess)
      view.isDefined && view.map(_.isPrivate)==Full(true)
    })
    val privateViews = accountAccess.map(getViewFromAccountAccess).flatten.distinct
    (privateViews, accountAccess)
  }
  def privateViewsUserCanAccess(user: User, viewIds: List[ViewId]): (List[View], List[AccountAccess]) ={
    val accountAccess = AccountAccess.findAll(
      By(AccountAccess.user_fk, user.userPrimaryKey.value),
      ByList(AccountAccess.view_id, viewIds.map(_.value))
    ).filter(accountAccess => {
      val view = getViewFromAccountAccess(accountAccess)
      view.isDefined && view.map(_.isPrivate) == Full(true)
    })
    PrivateViewsUserCanAccessCommon(accountAccess)
  }
  def privateViewsUserCanAccessAtBank(user: User, bankId: BankId): (List[View], List[AccountAccess]) ={
    val accountAccess = AccountAccess.findAll(
      By(AccountAccess.user_fk, user.userPrimaryKey.value),
      By(AccountAccess.bank_id, bankId.value)
    ).filter(accountAccess => {
      val view = getViewFromAccountAccess(accountAccess)
      view.isDefined && view.map(_.isPrivate) == Full(true)
    })
    PrivateViewsUserCanAccessCommon(accountAccess)
  }
  def getAccountAccessAtBankThroughView(user: User, bankId: BankId, viewId: ViewId): (List[View], List[AccountAccess]) ={
    val accountAccess = AccountAccess.findAll(
      By(AccountAccess.user_fk, user.userPrimaryKey.value),
      By(AccountAccess.bank_id, bankId.value),
      By(AccountAccess.view_id, viewId.value)
    ).filter(accountAccess => {
      val view = getViewFromAccountAccess(accountAccess)
      view.isDefined && view.map(_.isPrivate) == Full(true)
    })
    PrivateViewsUserCanAccessCommon(accountAccess)
  }

  private def PrivateViewsUserCanAccessCommon(accountAccess: List[AccountAccess]): (List[ViewDefinition], List[AccountAccess]) = {
    val listOfTuples: List[(AccountAccess, Box[ViewDefinition])] = accountAccess.map(
      accountAccess => (accountAccess, getViewFromAccountAccess(accountAccess))
    )
    val privateViews = listOfTuples.flatMap(
      tuple => tuple._2.map(v => v.bank_id(tuple._1.bank_id.get).account_id(tuple._1.account_id.get))
    )
    (privateViews, accountAccess)
  }

  def privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId) : List[View] =   {
    val accountAccess = AccountAccess.findByBankIdAccountIdUserPrimaryKey(
      bankIdAccountId.bankId,
      bankIdAccountId.accountId,
      user.userPrimaryKey
    )
    accountAccess.map(getViewFromAccountAccess).flatten.filter(view => view.isPrivate == true).distinct
  }

  
  def getOrCreateSystemViewFromCbs(viewId: String): Box[View] = {
    logger.debug(s"-->getOrCreateSystemViewFromCbs--- start--${viewId}  ")

    val theView = if (VIEWS_GENERATED_FROM_CBS_WHITE_LIST.contains(viewId)) {
      getOrCreateSystemView(viewId)
    } else {
      val errorMessage = ViewIdNotSupported + code.api.Constant.VIEWS_GENERATED_FROM_CBS_WHITE_LIST.mkString(", ") + s"Your input viewId is :$viewId"
      logger.error(errorMessage)
      Failure(errorMessage)
    }
    logger.debug(s"-->getOrCreateSystemViewFromCbs --- finish.${viewId } : ${theView} ")
    theView
  }

  private def migrateViewPermissions(view: View): Unit = {
    val permissionNames = List(
      "canSeeTransactionOtherBankAccount",
      "canSeeTransactionMetadata",
      "canSeeTransactionDescription",
      "canSeeTransactionAmount",
      "canSeeTransactionType",
      "canSeeTransactionCurrency",
      "canSeeTransactionStartDate",
      "canSeeTransactionFinishDate",
      "canSeeTransactionBalance",
      "canSeeComments",
      "canSeeOwnerComment",
      "canSeeTags",
      "canSeeImages",
      "canSeeBankAccountOwners",
      "canSeeBankAccountType",
      "canSeeBankAccountBalance",
      "canQueryAvailableFunds",
      "canSeeBankAccountLabel",
      "canSeeBankAccountNationalIdentifier",
      "canSeeBankAccountSwift_bic",
      "canSeeBankAccountIban",
      "canSeeBankAccountNumber",
      "canSeeBankAccountBankName",
      "canSeeBankAccountBankPermalink",
      "canSeeBankRoutingScheme",
      "canSeeBankRoutingAddress",
      "canSeeBankAccountRoutingScheme",
      "canSeeBankAccountRoutingAddress",
      "canSeeOtherAccountNationalIdentifier",
      "canSeeOtherAccountSWIFT_BIC",
      "canSeeOtherAccountIBAN",
      "canSeeOtherAccountBankName",
      "canSeeOtherAccountNumber",
      "canSeeOtherAccountMetadata",
      "canSeeOtherAccountKind",
      "canSeeOtherBankRoutingScheme",
      "canSeeOtherBankRoutingAddress",
      "canSeeOtherAccountRoutingScheme",
      "canSeeOtherAccountRoutingAddress",
      "canSeeMoreInfo",
      "canSeeUrl",
      "canSeeImageUrl",
      "canSeeOpenCorporatesUrl",
      "canSeeCorporateLocation",
      "canSeePhysicalLocation",
      "canSeePublicAlias",
      "canSeePrivateAlias",
      "canAddMoreInfo",
      "canAddURL",
      "canAddImageURL",
      "canAddOpenCorporatesUrl",
      "canAddCorporateLocation",
      "canAddPhysicalLocation",
      "canAddPublicAlias",
      "canAddPrivateAlias",
      "canAddCounterparty",
      "canGetCounterparty",
      "canDeleteCounterparty",
      "canDeleteCorporateLocation",
      "canDeletePhysicalLocation",
      "canEditOwnerComment",
      "canAddComment",
      "canDeleteComment",
      "canAddTag",
      "canDeleteTag",
      "canAddImage",
      "canDeleteImage",
      "canAddWhereTag",
      "canSeeWhereTag",
      "canDeleteWhereTag",
      "canAddTransactionRequestToOwnAccount",
      "canAddTransactionRequestToAnyAccount",
      "canSeeBankAccountCreditLimit",
      "canCreateDirectDebit",
      "canCreateStandingOrder",
      "canRevokeAccessToCustomViews",
      "canGrantAccessToCustomViews",
      "canSeeTransactionRequests",
      "canSeeTransactionRequestTypes",
      "canSeeAvailableViewsForBankAccount",
      "canUpdateBankAccountLabel",
      "canCreateCustomView",
      "canDeleteCustomView",
      "canUpdateCustomView",
      "canGetCustomView",
      "canSeeViewsWithPermissionsForAllUsers",
      "canSeeViewsWithPermissionsForOneUser"
    )

    permissionNames.foreach { permissionName =>
      // Get permission value
      val permissionValue = view.getClass.getMethod(permissionName).invoke(view).asInstanceOf[Boolean]

      ViewPermission.findViewPermissions(view.viewId).find(_.permission.get == permissionName) match {
        case Some(permission) if !permissionValue =>
          ViewPermission.delete_!(permission)
        case Some(permission) if permissionValue =>
          // View definition is in accordance with View permission
        case _ =>
          ViewPermission.create
            .bank_id(null)
            .account_id(null)
            .view_id(view.viewId.value)
            .permission(permissionName)
            .save
      }
    }
  }
  
  def getOrCreateSystemView(viewId: String) : Box[View] = {
    getExistingSystemView(viewId) match {
      case Empty =>
        val view = createDefaultSystemView(viewId)
        view.map(v => migrateViewPermissions(v))
        view
      case Full(v) =>
        migrateViewPermissions(v)
        Full(v)
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }
  

  /**
   * if return the system view owner, it may return all the users, all the user if have its own account, it should have the `owner` view access.
   * @param view
   * @return
   */
  def getOwners(view: View) : Set[User] = {
    val accountAccessList = AccountAccess.findAllByView(view)
    val users: List[User] = accountAccessList.flatMap(_.user_fk.obj)
    users.toSet
  }

  def getOrCreateCustomPublicView(bankId: BankId, accountId: AccountId, description: String = "Public View") : Box[View] = {
    getExistingCustomView(bankId, accountId, CUSTOM_PUBLIC_VIEW_ID) match {
      case Empty=> createDefaultCustomPublicView(bankId, accountId, description)
      case Full(v)=> Full(v)
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }

  def createDefaultSystemView(viewId: String): Box[View] = {
    createAndSaveSystemView(viewId)
  }

  def createDefaultCustomPublicView(bankId: BankId, accountId: AccountId, description: String): Box[View] = {
    if(!allowPublicViews) {
      return Failure(PublicViewsNotAllowedOnThisInstance)
    }
    createAndSaveDefaultPublicCustomView(bankId, accountId, description)
  }

  def getExistingCustomView(bankId: BankId, accountId: AccountId, viewId: String): Box[View] = {
    val res = ViewDefinition.findCustomView(bankId.value, accountId.value, viewId)
    if(res.isDefined && res.openOrThrowException(attemptedToOpenAnEmptyBox).isPublic && !allowPublicViews) return Failure(PublicViewsNotAllowedOnThisInstance)
    res
  }
  def getExistingSystemView(viewId: String): Box[View] = {
    val res = ViewDefinition.findSystemView(viewId)
    logger.debug(s"-->getExistingSystemView(viewId($viewId)) = result ${res} ")
    if(res.isDefined && res.openOrThrowException(attemptedToOpenAnEmptyBox).isPublic && !allowPublicViews) return Failure(PublicViewsNotAllowedOnThisInstance)
    res
  }

  def removeAllPermissions(bankId: BankId, accountId: AccountId) : Boolean = {
    AccountAccess.bulkDelete_!!(
      By(AccountAccess.bank_id, bankId.value),
      By(AccountAccess.account_id, accountId.value)
    )
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

  def unsavedSystemView(viewId: String): ViewDefinition = {
    val entity = create
      .isSystem_(true)
      .isFirehose_(false)
      .bank_id(null)
      .account_id(null)
      .name_(StringHelpers.capify(viewId))
      .view_id(viewId)
      .description_(viewId)
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
      .canGetCounterparty_(true)
      .canDeleteCounterparty_(true)
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
      
      // TODO  Allow use only for certain cases
      .canAddTransactionRequestToOwnAccount_(true) //added following two for payments
      .canAddTransactionRequestToAnyAccount_(true)
      
      .canSeeAvailableViewsForBankAccount_(false)
      .canSeeTransactionRequests_(false)
      .canSeeTransactionRequestTypes_(false)
      .canUpdateBankAccountLabel_(false)
      .canSeeViewsWithPermissionsForOneUser_(false)
      .canSeeViewsWithPermissionsForAllUsers_(false)
      .canRevokeAccessToCustomViews_(false)
      .canGrantAccessToCustomViews_(false)
      .canCreateCustomView_(false)
      .canDeleteCustomView_(false)
      .canUpdateCustomView_(false)
      .canGetCustomView_(false)

    viewId match {
      case SYSTEM_OWNER_VIEW_ID | SYSTEM_STANDARD_VIEW_ID =>
        entity // Make additional setup to the existing view
          .canSeeAvailableViewsForBankAccount_(true)
          .canSeeTransactionRequests_(true)
          .canSeeTransactionRequestTypes_(true)
          .canUpdateBankAccountLabel_(true)
          .canSeeViewsWithPermissionsForOneUser_(true)
          .canSeeViewsWithPermissionsForAllUsers_(true)
          .canGrantAccessToViews_(DEFAULT_CAN_GRANT_AND_REVOKE_ACCESS_TO_VIEWS.mkString(","))
          .canRevokeAccessToViews_(DEFAULT_CAN_GRANT_AND_REVOKE_ACCESS_TO_VIEWS.mkString(","))
      case SYSTEM_STAGE_ONE_VIEW_ID =>
        entity // Make additional setup to the existing view
          .canSeeTransactionDescription_(false)
          .canAddTransactionRequestToAnyAccount_(false)
      case SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID =>
        entity // Make additional setup to the existing view
          .canRevokeAccessToCustomViews_(true)
          .canGrantAccessToCustomViews_(true)
          .canCreateCustomView_(true)
          .canDeleteCustomView_(true)
          .canUpdateCustomView_(true)
          .canGetCustomView_(true)
      case SYSTEM_FIREHOSE_VIEW_ID =>
        entity // Make additional setup to the existing view
          .isFirehose_(true)
      case SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID | 
           SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID =>
        create // A new one
          .isSystem_(true)
          .isFirehose_(false)
          .name_(StringHelpers.capify(viewId))
          .view_id(viewId)
          .description_(viewId)
      case SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID =>
        create // A new one
          .isSystem_(true)
          .isFirehose_(false)
          .name_(StringHelpers.capify(viewId))
          .view_id(viewId)
          .description_(viewId)
          .canSeeTransactionThisBankAccount_(true)
          .canSeeTransactionOtherBankAccount_(true)
          .canSeeTransactionAmount_(true)
          .canSeeTransactionCurrency_(true)
          .canSeeTransactionBalance_(true)
          .canSeeTransactionStartDate_(true)
          .canSeeTransactionFinishDate_(true)
          .canSeeTransactionDescription_(true)
      case SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID =>
        create // A new one
          .isSystem_(true)
          .isFirehose_(false)
          .name_(StringHelpers.capify(viewId))
          .view_id(viewId)
          .description_(viewId)
          .canAddTransactionRequestToAnyAccount_(true)
      case _ =>
        entity
    }
  }
  
  def createAndSaveSystemView(viewId: String) : Box[View] = {
    logger.debug(s"-->createAndSaveSystemView.viewId.start${viewId} ")
    val res = unsavedSystemView(viewId).saveMe
    logger.debug(s"-->createAndSaveSystemView.finish: ${res} ")
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
      canGetCounterparty_(true).
      canDeleteCounterparty_(false).
      canDeleteCorporateLocation_(false).
      canDeletePhysicalLocation_(false).
      canEditOwnerComment_(true).
      canAddComment_(true).
      canDeleteComment_(false).
      canAddTag_(true).
      canDeleteTag_(false).
      canAddImage_(true).
      canDeleteImage_(false).
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
      canAddTransactionRequestToAnyAccount_(false).
      canSeeTransactionRequests_(false).
      canSeeTransactionRequestTypes_(false).
      canUpdateBankAccountLabel_(false).
      canCreateCustomView_(false).
      canDeleteCustomView_(false).
      canUpdateCustomView_(false).
      canGetCustomView_(false)
  }

  def createAndSaveDefaultPublicCustomView(bankId : BankId, accountId: AccountId, description: String) : Box[View] = {
    if(!allowPublicViews) {
      return Failure(PublicViewsNotAllowedOnThisInstance)
    }
    val res = unsavedDefaultPublicView(bankId, accountId, description).saveMe
    Full(res)
  }

}
