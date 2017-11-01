package code.views

import bootstrap.liftweb.ToSchemify
import code.accountholder.{AccountHolders, MapperAccountHolders}
import code.api.APIFailure
import code.model.dataAccess.ViewImpl.create
import code.model.dataAccess.{ResourceUser, ViewImpl, ViewPrivileges}
import code.model.{CreateViewJson, Permission, UpdateViewJSON, User, _}
import net.liftweb.common._
import net.liftweb.mapper.{By, Schemifier}
import net.liftweb.util.Helpers._

import scala.collection.immutable.List
import code.util.Helper.MdcLoggable
import net.liftweb.util.Props
import code.api.util.ErrorMessages._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

//TODO: Replace BankAccountUIDs with bankPermalink + accountPermalink


object MapperViews extends Views with MdcLoggable {

  Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.modelsRemotedata: _*)
  
  val ALLOW_PUBLIC_VIEWS: Boolean = Props.getBool("allow_public_views").openOr(false)

  def permissions(account : BankIdAccountId) : List[Permission] = {

    val views: List[ViewImpl] = ViewImpl.findAll(By(ViewImpl.isPublic_, false) ::
      ViewImpl.accountFilter(account.bankId, account.accountId): _*)
    //all the user that have access to at least to a view
    val users = views.map(_.users).flatten.distinct
    val usersPerView = views.map(v  =>(v, v.users))
    val permissions = users.map(u => {
      new Permission(
        u,
        usersPerView.filter(_._2.contains(u)).map(_._1)
      )
    })

    permissions
  }

  def permission(account: BankIdAccountId, user: User): Box[Permission] = {

    //search ViewPrivileges to get all views for user and then filter the views
    // by bankPermalink and accountPermalink
    //TODO: do it in a single query with a join
    val privileges = ViewPrivileges.findAll(By(ViewPrivileges.user, user.resourceUserId.value))
    val views = privileges.flatMap(_.view.obj).filter(v =>
      if (ALLOW_PUBLIC_VIEWS) {
        v.accountId == account.accountId &&
          v.bankId == account.bankId
      } else {
        v.accountId == account.accountId &&
          v.bankId == account.bankId &&
          !v.isPublic
      }
    )
    Full(Permission(user, views))
  }

  /**
    * This gives the user access to the view.
    * Note: This method is a little different with addPermission,
    * The parameter is the view object, and this view can be changed to ViewImpl
    */
  def getOrCreateViewPrivilege(view: View, user: User): Box[View] = {
    
    val viewImpl = view.asInstanceOf[ViewImpl]

    if(viewImpl.isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)
    // SQL Select Count ViewPrivileges where
    getOrCreateViewPrivilege(user, viewImpl)
  }
  
  private def getOrCreateViewPrivilege(user: User, viewImpl: ViewImpl): Box[ViewImpl] = {
    if (ViewPrivileges.count(By(ViewPrivileges.user, user.resourceUserId.value), By(ViewPrivileges.view, viewImpl.id)) == 0) {
      //logger.debug(s"saving ViewPrivileges for user ${user.resourceUserId.value} for view ${vImpl.id}")
      // SQL Insert ViewPrivileges
      val saved = ViewPrivileges.create.
        user(user.resourceUserId.value).
        view(viewImpl.id).
        save
      if (saved) {
        //logger.debug("saved ViewPrivileges")
        Full(viewImpl)
      } else {
        //logger.debug("failed to save ViewPrivileges")
        Empty ~> APIFailure("Server error adding permission", 500) //TODO: move message + code logic to api level
      }
    } else Full(viewImpl) //privilege already exists, no need to create one
  }
  // TODO Accept the whole view as a parameter so we don't have to select it here.
  def addPermission(viewIdBankIdAccountId: ViewIdBankIdAccountId, user: User): Box[View] = {
    logger.debug(s"addPermission says viewUID is $viewIdBankIdAccountId user is $user")
    val viewImpl = ViewImpl.find(viewIdBankIdAccountId) // SQL Select View where

    viewImpl match {
      case Full(vImpl) => {
        if(vImpl.isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)
        // SQL Select Count ViewPrivileges where
        getOrCreateViewPrivilege(user, vImpl) //privilege already exists, no need to create one
      }
      case _ => {
        Empty ~> APIFailure(s"View $viewIdBankIdAccountId. not found", 404) //TODO: move message + code logic to api level
      }
    }
  }

  def addPermissions(views: List[ViewIdBankIdAccountId], user: User): Box[List[View]] = {
    val viewImpls = views.map(uid => ViewImpl.find(uid)).collect { case Full(v) => v}

    if (viewImpls.size != views.size) {
      val failMsg = s"not all viewimpls could be found for views ${viewImpls} (${viewImpls.size} != ${views.size}"
      //logger.debug(failMsg)
      Failure(failMsg) ~>
        APIFailure(s"One or more views not found", 404) //TODO: this should probably be a 400, but would break existing behaviour
      //TODO: APIFailures with http response codes belong at a higher level in the code
    } else {
      viewImpls.foreach(v => {
        if(v.isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)
        if (ViewPrivileges.count(By(ViewPrivileges.user, user.resourceUserId.value), By(ViewPrivileges.view, v.id)) == 0) {
          ViewPrivileges.create.
            user(user.resourceUserId.value).
            view(v.id).
            save
        }
      })
      //TODO: this doesn't handle the case where one viewImpl fails to be saved
      Full(viewImpls)
    }
  }

  def revokePermission(viewUID : ViewIdBankIdAccountId, user : User) : Box[Boolean] = {
    val res =
    for {
      viewImpl <- ViewImpl.find(viewUID)
      vp: ViewPrivileges  <- ViewPrivileges.find(By(ViewPrivileges.user, user.resourceUserId.value), By(ViewPrivileges.view, viewImpl.id))
      deletable <- accessRemovableAsBox(viewImpl, user)
    } yield {
      vp.delete_!
    }
    res
  }

  //returns Full if deletable, Failure if not
  def accessRemovableAsBox(viewImpl : ViewImpl, user : User) : Box[Unit] = {
    if(accessRemovable(viewImpl, user)) Full(Unit)
    else Failure("access cannot be revoked")
  }


  def accessRemovable(viewImpl: ViewImpl, user : User) : Boolean = {
    if(viewImpl.viewId == ViewId("owner")) {

      //if the user is an account holder, we can't revoke access to the owner view
      val accountHolders = MapperAccountHolders.getAccountHolders(viewImpl.bankId, viewImpl.accountId)
      if(accountHolders.map {h =>
        h.resourceUserId
      }.contains(user.resourceUserId)) {
        false
      } else {
        // if it's the owner view, we can only revoke access if there would then still be someone else
        // with access
        viewImpl.users.length > 1
      }

    } else true
  }




  /*
  This removes the link between a User and a View (View Privileges)
   */

  def revokeAllPermissions(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = {
    //TODO: make this more efficient by using one query (with a join)
    val allUserPrivs = ViewPrivileges.findAll(By(ViewPrivileges.user, user.resourceUserId.value))

    val relevantAccountPrivs = allUserPrivs.filter(p => p.view.obj match {
      case Full(v) => {
        v.bankId == bankId && v.accountId == accountId
      }
      case _ => false
    })

    val allRelevantPrivsRevokable = relevantAccountPrivs.forall( p => p.view.obj match {
      case Full(v) => accessRemovable(v, user)
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

  def view(viewId : ViewId, account: BankIdAccountId) : Box[View] = {
    val view = ViewImpl.find(ViewIdBankIdAccountId(viewId, account.bankId, account.accountId))

    if(view.isDefined && view.openOrThrowException("Attempted to open an empty Box.").isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)

    view
  }

  def viewFuture(viewId : ViewId, account: BankIdAccountId) : Future[Box[View]] = {
    Future {
      view(viewId, account)
    }
  }

  def view(viewUID : ViewIdBankIdAccountId) : Box[View] = {
    val view=ViewImpl.find(viewUID)

    if(view.isDefined && view.openOrThrowException("Attempted to open an empty Box.").isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)

    view
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
    //view-permalink is view.name without spaces.  (view.name = my life) <---> (view-permalink = mylife)
    val newViewPermalink = {
      view.name.replaceAllLiterally(" ", "").toLowerCase
    }

    val existing = ViewImpl.count(
      By(ViewImpl.permalink_, newViewPermalink) ::
        ViewImpl.accountFilter(bankAccountId.bankId, bankAccountId.accountId): _*
    ) == 1

    if (existing)
      Failure(s"There is already a view with permalink $newViewPermalink on this bank account")
    else {
      val createdView = ViewImpl.create.
        name_(view.name).
        permalink_(newViewPermalink).
        bankPermalink(bankAccountId.bankId.value).
        accountPermalink(bankAccountId.accountId.value)

      createdView.setFromViewData(view)
      Full(createdView.saveMe)
    }
  }


  /* Update the specification of the view (what data/actions are allowed) */
  def updateView(bankAccountId : BankIdAccountId, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] = {

    for {
      view <- ViewImpl.find(viewId, bankAccountId)
    } yield {
      view.setFromViewData(viewUpdateJson)
      view.saveMe
    }
  }

  def removeView(viewId: ViewId, bankAccountId: BankIdAccountId): Box[Unit] = {

    if(viewId.value == "owner")
      Failure("you cannot delete the owner view")
    else {
      for {
        view <- ViewImpl.find(viewId, bankAccountId)
        if(view.delete_!)
      } yield {
      }
    }
  }

  /**
    * Get the view list by bankAccountUUID.
    * @param bankAccountId find the views by this bankaccountUUID.
    * @return if find, return the view list. Or return the Nil.
    */
  def views(bankAccountId : BankIdAccountId) : List[View] = {
    if (ALLOW_PUBLIC_VIEWS)
      ViewImpl.findAll(ViewImpl.accountFilter(bankAccountId.bankId, bankAccountId.accountId): _*)
    else
      ViewImpl.findAll(By(ViewImpl.isPublic_, false):: ViewImpl.accountFilter(bankAccountId.bankId, bankAccountId.accountId): _*)
  }

  /**
    * This method is belong to Views trait, check the permitted views of input account for input user.
    * Select all the views by user and bankAccountUID.
    *
    * @param user the user need to be checked for the views
    * @param bankAccountId the bankAccountUID, the account will be checked the views.
    * @return if find, return the view list. or return Nil.
    */
  def permittedViews(user: User, bankAccountId: BankIdAccountId): List[View] = {
    //TODO: do this more efficiently?
    //select all views by user.
    val allUserPrivs = ViewPrivileges.findAll(By(ViewPrivileges.user, user.resourceUserId.value))
    //select the nonpublic views by BankAccountUid
    val userNonPublicViewsForAccount = allUserPrivs.flatMap(p => {
      p.view.obj match {
        case Full(v) => if(
          !v.isPublic &&
            v.bankId == bankAccountId.bankId&&
            v.accountId == bankAccountId.accountId){
          Some(v)
        } else None
        case _ => None
      }
    })
    // merge the nonPublic and public views
    userNonPublicViewsForAccount ++ publicViews(bankAccountId)
  }

  def publicViews(bankAccountId : BankIdAccountId) : List[View] = {
    if(ALLOW_PUBLIC_VIEWS)
      ViewImpl.findAll(By(ViewImpl.isPublic_,true)::ViewImpl.accountFilter(bankAccountId.bankId, bankAccountId.accountId): _*)
    else
      Nil
  }

  /**
    * An account is considered public if it contains a public view
    * @return the list of all bankAccountUUIDs which contains a public view
    */
  def getAllPublicAccounts() : List[BankIdAccountId] = {
    if (ALLOW_PUBLIC_VIEWS)
      ViewImpl
        .findAll(By(ViewImpl.isPublic_, true)) //find all the public views
        .map(v =>BankIdAccountId(v.bankId, v.accountId)) //generate the BankAccountUID
        .distinct //we remove duplicates here
    else
      Nil
  }

  def getPublicBankAccounts(bank : Bank) : List[BankIdAccountId] = {
      if (ALLOW_PUBLIC_VIEWS)
        ViewImpl
          .findAll(By(ViewImpl.isPublic_, true), By(ViewImpl.bankPermalink, bank.bankId.value)) //find all the public views
          .map(v => {BankIdAccountId(bank.bankId, v.accountId) }) //generate the BankAccountUID
          .distinct //we remove duplicates here
      else
        Nil
  }

  /**
   * @param user
   * @return the bank accounts the @user can see (public + private if @user is Full, public if @user is Empty)
   */
  def getAllAccountsUserCanSee(user : Box[User]) : List[BankIdAccountId] = {
    user match {
      case Full(user) => {
        val publicViewBankAndAccounts=
          if (ALLOW_PUBLIC_VIEWS)
            ViewImpl
              .findAll(By(ViewImpl.isPublic_, true)) // find all the public view in ViewImpl table, it has no relevent with user, all the user can get the public view.
              .map(v => {BankIdAccountId(v.bankId, v.accountId)}) //generate the BankAccountUID
          else
            Nil

        val nonPublicViewBankAndAccounts = ViewPrivileges
          .findAll(By(ViewPrivileges.user, user.resourceUserId.value)) // find all the views link to the user, means the views that user can access.
          .map(_.view.obj).flatten.filter(!_.isPublic) //select all the non-public views
          .map(v => { BankIdAccountId(v.bankId, v.accountId)}) //generate the BankAccountUID

        //we remove duplicates here, because some accounts, has both public views and non-public views
        (publicViewBankAndAccounts ++ nonPublicViewBankAndAccounts).distinct
      }
      case _ => getAllPublicAccounts()
    }
  }

  /**
   * @param user
   * @return the bank accounts at @bank the @user can see (public + private if @user is Full, public if @user is Empty)
   */
  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : List[BankIdAccountId] = {
    user match {
      case Full(user) => {
        val publicViewBankAndAccounts=
          if (ALLOW_PUBLIC_VIEWS)
            ViewImpl
              .findAll(By(ViewImpl.isPublic_, true),By(ViewImpl.bankPermalink, bank.bankId.value)) // find all the public view in ViewImpl table, it has no relevant with user, all the user can get the public view.
              .map(v => {BankIdAccountId(v.bankId, v.accountId)}) //generate the BankAccountUID
          else
            Nil

        val nonPublicViewBankAndAccounts = ViewPrivileges
          .findAll(By(ViewPrivileges.user, user.resourceUserId.value)) // find all the views link to the user, means the views that user can access.
          .map(_.view.obj).flatten.filter(v => !v.isPublic && v.bankId ==bank.bankId) //select all the non-public views according to bankId
          .map(v => { BankIdAccountId(v.bankId, v.accountId)}) //generate the BankAccountUID

        //we remove duplicates here, because some accounts, has both public views and non-public views
        (publicViewBankAndAccounts ++ nonPublicViewBankAndAccounts).distinct
      }
      case _ => getPublicBankAccounts(bank)
    }
  }

  /**
   * @return the bank accounts where the user has at least access to a non public view (is_public==false)
   */
  def getNonPublicBankAccounts(user : User) :  List[BankIdAccountId] = {
    ViewPrivileges
    .findAll(By(ViewPrivileges.user, user.resourceUserId.value)) // find all the views link to the user, means the views that user can access.
      .map(_.view.obj).flatten.filter(!_.isPublic) //select all the non-public views
      .map(v => { BankIdAccountId(v.bankId, v.accountId)}) //generate the BankAccountUID
      .distinct//we remove duplicates here
  }

  /**
    * @return the bank accounts where the user has at least access to a non public view (is_public==false)
    */
  def getNonPublicBankAccountsFuture(user : User) :  Future[List[BankIdAccountId]] = {
    Future {
      getNonPublicBankAccounts(user)
    }
  }

  /**
   * @return the bank accounts where the user has at least access to a non public view (is_public==false) for a specific bank
   */
  def getNonPublicBankAccounts(user : User, bankId : BankId) :  List[BankIdAccountId] = {
    ViewPrivileges
      .findAll(By(ViewPrivileges.user, user.resourceUserId.value)) // find all the views link to the user, means the views that user can access.
      .map(_.view.obj).flatten.filter(v => !v.isPublic && v.bankId == bankId) //select all the non-public views according to bankId
      .map(v => { BankIdAccountId(v.bankId, v.accountId)}) //generate the BankAccountUID
      .distinct//we remove duplicates here
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
    val ownerView = "Owner".equals(viewId)
    val publicView = "Public".equals(viewId)
    val accountantsView = "Accountant".equals(viewId)
    val auditorsView = "Auditor".equals(viewId)
    
    val theView =
      if (ownerView)
        Views.views.vend.getOrCreateOwnerView(bankId, accountId, "Owner View")
      else if (publicView)
        Views.views.vend.getOrCreatePublicView(bankId, accountId, "Public View")
      else if (accountantsView)
        Views.views.vend.getOrCreateAccountantsView(bankId, accountId, "Accountants View")
      else if (auditorsView)
        Views.views.vend.getOrCreateAuditorsView(bankId, accountId, "Auditors View")
      else 
        Failure(ViewIdNotSupported+ s"Your input viewId is :$viewId")
    
    logger.debug(s"-->getOrCreateAccountView.${viewId } : ${theView} ")
    
    theView
  }
  
  def getOrCreateOwnerView(bankId: BankId, accountId: AccountId, description: String = "Owner View") : Box[View] = {
    getExistingView(bankId, accountId, "Owner") match {
      case Empty => createDefaultOwnerView(bankId, accountId, description)
      case Full(v) => Full(v)
    }
  }

  def getOwners(view: View) : Set[User] = {
    val viewUid = ViewImpl.find(view.uid)
    val privileges = ViewPrivileges.findAll(By(ViewPrivileges.view, viewUid))
    val users: List[User] = privileges.flatMap(_.user.obj)
    users.toSet
  }

  def getOrCreatePublicView(bankId: BankId, accountId: AccountId, description: String = "Public View") : Box[View] = {
    getExistingView(bankId, accountId, "Public") match {
      case Empty=> createDefaultPublicView(bankId, accountId, description)
      case Full(v)=> Full(v)
    }
  }

  def getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String = "Accountants View") : Box[View] = {
    getExistingView(bankId, accountId, "Accountant") match {
      case Empty => createDefaultAccountantsView(bankId, accountId, description)
      case Full(v) => Full(v)
    }
  }

  def getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String = "Auditors View") : Box[View] = {
    getExistingView(bankId, accountId, "Auditor") match {
      case Empty => createDefaultAuditorsView(bankId, accountId, description)
      case Full(v) => Full(v)
    }
  }

  def createRandomView(bankId: BankId, accountId: AccountId) : Box[View] = {
    Full(ViewImpl.create.
      isSystem_(false).
      name_(randomString(5)).
      description_(randomString(3)).
      permalink_(randomString(3)).
      isPublic_(false).
      bankPermalink(bankId.value).
      accountPermalink(accountId.value).
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
      canSeeOtherAccountIBAN_ (true).
      canSeeOtherAccountBankName_(true).
      canSeeOtherAccountNumber_(true).
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
      canAddTransactionRequestToAnyAccount_(false)
      saveMe)
  }

  //TODO This is used only for tests, but might impose security problem
  /**
    * Grant user all views in the ViewImpl table. It is only used in Scala Tests.
    * @param user the user who will get the access to all views in ViewImpl table. 
    * @return if no exception, it always return true
    */
  def grantAccessToAllExistingViews(user : User) = {
    ViewImpl.findAll.foreach(v => {
      //Get All the views from ViewImpl table, and create the link user <--> each view. The link record the access permission. 
      if ( ViewPrivileges.find(By(ViewPrivileges.view, v), By(ViewPrivileges.user, user.resourceUserId.value) ).isEmpty )
        //If the user and one view has no link, it will create one .
        ViewPrivileges.create.
          view(v).
          user(user.resourceUserId.value).
          save
      })
    true
  }
  /**
    * "Grant view access"  means to create the link between User <---> View.
    *  All these links are handled in ViewPrivileges table. 
    *  If ViewPrivileges.count(By(ViewPrivileges.view, v), By(ViewPrivileges.user, user.resourceUserId.value) ) == 0,
    *  this means there is no link between v <--> user. 
    *  So Just create one . 
    * 
    * @param user the user will to be granted access to.
    * @param view the view will be granted access. 
    *             
    * @return create the link between user<--> view, return true. 
    *         otherwise(If there existed view/ if there is no view ), it return false.
    *         
    */
  def grantAccessToView(user : User, view : View): Boolean = {
    val v = ViewImpl.find(view.uid).orNull
    if ( ViewPrivileges.count(By(ViewPrivileges.view, v), By(ViewPrivileges.user, user.resourceUserId.value) ) == 0 )
    ViewPrivileges.create.
      view(v). //explodes if no viewImpl exists, but that's okay, the test should fail then
      user(user.resourceUserId.value).
      save
    else
      false
  }
  
  /**
    * Find view by bankId , accountId and viewName. If it is exsting in ViewImple table, return true.
    * Otherwise, return false.
    * 
    * But not used yet !
    */
  def viewExists(bankId: BankId, accountId: AccountId, name: String): Boolean = {
    val res =
      if (ALLOW_PUBLIC_VIEWS)
        ViewImpl.findAll(
          By(ViewImpl.bankPermalink, bankId.value),
          By(ViewImpl.accountPermalink, accountId.value),
          By(ViewImpl.name_, name)
        )
      else
        ViewImpl.findAll(
          By(ViewImpl.bankPermalink, bankId.value),
          By(ViewImpl.accountPermalink, accountId.value),
          By(ViewImpl.name_, name),
          By(ViewImpl.isPublic_, false)
        )
    res.nonEmpty
  }

  def createDefaultOwnerView(bankId: BankId, accountId: AccountId, name: String): Box[View] = {
    createAndSaveOwnerView(bankId, accountId, "Owner View")
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
    val res = ViewImpl.find(
        By(ViewImpl.bankPermalink, bankId.value),
        By(ViewImpl.accountPermalink, accountId.value),
        By(ViewImpl.name_, name)
      )
    if(res.isDefined && res.openOrThrowException("Attempted to open an empty Box.").isPublic && !ALLOW_PUBLIC_VIEWS) return Failure(PublicViewsNotAllowedOnThisInstance)
    res
  }

  def removeAllPermissions(bankId: BankId, accountId: AccountId) : Boolean = {
    val views = ViewImpl.findAll(
      By(ViewImpl.bankPermalink, bankId.value),
      By(ViewImpl.accountPermalink, accountId.value)
    )
    var privilegesDeleted = true
    views.map (x => {
      privilegesDeleted &&= ViewPrivileges.bulkDelete_!!(By(ViewPrivileges.view, x.id_.get))
    } )
      privilegesDeleted
  }

  def removeAllViews(bankId: BankId, accountId: AccountId) : Boolean = {
    ViewImpl.bulkDelete_!!(
      By(ViewImpl.bankPermalink, bankId.value),
      By(ViewImpl.accountPermalink, accountId.value)
    )
  }

  def bulkDeleteAllPermissionsAndViews() : Boolean = {
    ViewImpl.bulkDelete_!!()
    ViewPrivileges.bulkDelete_!!()
    true
  }

  def unsavedOwnerView(bankId : BankId, accountId: AccountId, description: String) : ViewImpl = {
    create
      .isSystem_(true)
      .bankPermalink(bankId.value)
      .accountPermalink(accountId.value)
      .name_("Owner")
      .permalink_("owner")
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
      .canInitiateTransaction_(true)
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

  def createAndSaveOwnerView(bankId : BankId, accountId: AccountId, description: String) : Box[View] = {
    val res = unsavedOwnerView(bankId, accountId, description).saveMe
    Full(res)
  }

  def unsavedDefaultPublicView(bankId : BankId, accountId: AccountId, description: String) : ViewImpl = {
    create.
      isSystem_(true).
      name_("Public").
      description_(description).
      permalink_("public").
      isPublic_(true).
      bankPermalink(bankId.value).
      accountPermalink(accountId.value).
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
      canSeeBankAccountSwift_bic_(true).
      canSeeBankAccountIban_(true).
      canSeeBankAccountNumber_(true).
      canSeeBankAccountBankName_(true).
      canSeeBankAccountBankPermalink_(true).
      canSeeOtherAccountNationalIdentifier_(true).
      canSeeOtherAccountSWIFT_BIC_(true).
      canSeeOtherAccountIBAN_ (true).
      canSeeOtherAccountBankName_(true).
      canSeeOtherAccountNumber_(true).
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

  def unsavedDefaultAccountantsView(bankId : BankId, accountId: AccountId, description: String) : ViewImpl = {
    create.
      isSystem_(true).
      name_("Accountant"). // Use the singular form
      description_(description).
      permalink_("accountant"). // Use the singular form
      isPublic_(false).
      bankPermalink(bankId.value).
      accountPermalink(accountId.value).
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
      canSeeBankAccountSwift_bic_(true).
      canSeeBankAccountIban_(true).
      canSeeBankAccountNumber_(true).
      canSeeBankAccountBankName_(true).
      canSeeBankAccountBankPermalink_(true).
      canSeeOtherAccountNationalIdentifier_(true).
      canSeeOtherAccountSWIFT_BIC_(true).
      canSeeOtherAccountIBAN_ (true).
      canSeeOtherAccountBankName_(true).
      canSeeOtherAccountNumber_(true).
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

  def unsavedDefaultAuditorsView(bankId : BankId, accountId: AccountId, description: String) : ViewImpl = {
    create.
      isSystem_(true).
      name_("Auditor"). // Use the singular form
      description_(description).
      permalink_("auditor"). // Use the singular form
      isPublic_(false).
      bankPermalink(bankId.value).
      accountPermalink(accountId.value).
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
      canSeeBankAccountSwift_bic_(true).
      canSeeBankAccountIban_(true).
      canSeeBankAccountNumber_(true).
      canSeeBankAccountBankName_(true).
      canSeeBankAccountBankPermalink_(true).
      canSeeOtherAccountNationalIdentifier_(true).
      canSeeOtherAccountSWIFT_BIC_(true).
      canSeeOtherAccountIBAN_ (true).
      canSeeOtherAccountBankName_(true).
      canSeeOtherAccountNumber_(true).
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
