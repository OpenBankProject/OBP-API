package code.remotedata

import java.net.URL
import java.util.Date
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Props => ActorProps}
import akka.event.Logging
import akka.util.Timeout
import bootstrap.liftweb.ToSchemify
import code.accountholder.{MapperAccountHolders, RemoteAccountHoldersCaseClasses}
import code.metadata.comments.{MappedComment, MappedComments, RemoteCommentsCaseClasses}
import code.metadata.counterparties.{CounterpartyTrait, MapperCounterparties, RemoteCounterpartiesCaseClasses}
import code.metadata.tags.{MappedTags, RemoteTagsCaseClasses}
import code.metadata.transactionimages.{MappedTransactionImage, MapperTransactionImages, RemoteTransactionImagesCaseClasses}
import code.metadata.wheretags.{MapperWhereTags, RemoteWhereTagsCaseClasses}
import code.model._
import code.model.dataAccess.ResourceUser
import code.users.{LiftUsers, RemoteUserCaseClasses}
import code.views.{MapperViews, RemoteViewCaseClasses}
import com.typesafe.config.ConfigFactory
import net.liftweb.common._
import net.liftweb.db.StandardDBVendor
import net.liftweb.http.LiftRules
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.Props
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataActor extends Actor {

  val logger = Logging(context.system, this)

  Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.modelsRemotedata: _*)

  val mViews = MapperViews
  val rViews = RemoteViewCaseClasses

  val mUsers = LiftUsers
  val rUsers = RemoteUserCaseClasses

  val mCounterparties = MapperCounterparties
  val rCounterparties = RemoteCounterpartiesCaseClasses

  val mAccountHolders = MapperAccountHolders
  val rAccountHolders = RemoteAccountHoldersCaseClasses

  val mComments = MappedComments
  val rComments = RemoteCommentsCaseClasses

  val mTags = MappedTags
  val rTags = RemoteTagsCaseClasses

  val mWhereTags = MapperWhereTags
  val rWhereTags = RemoteWhereTagsCaseClasses

  val mTransactionImages = MapperTransactionImages
  val rTransactionImages = RemoteTransactionImagesCaseClasses

  def receive = {

    case rViews.addPermissions(views : List[ViewUID], user : User) =>

      logger.info("addPermissions(" + views +"," + user +")")

      {
        for {
          res <- mViews.addPermissions(views, user)
        } yield {
          sender ! res.asInstanceOf[List[View]]
        }
      }.getOrElse( context.stop(sender) )


    case rViews.addPermission(viewUID : ViewUID, user : User) =>

      logger.info("addPermission(" + viewUID +"," + user +")")

      {
        for {
          res <- mViews.addPermission(viewUID, user)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )


    case rViews.permission(account : BankAccountUID, user: User) =>

      logger.info("permission(" + account +"," + user +")")

      {
        for {
          res <- mViews.permission(account, user)
        } yield {
          sender ! res.asInstanceOf[Permission]
        }
      }.getOrElse( context.stop(sender) )


    //TODO Fix return values in order to better describe failures
    case rViews.revokePermission(viewUID : ViewUID, user : User) =>

      logger.info("revokePermission(" + viewUID +"," + user +")")


      val res = mViews.revokePermission(viewUID, user)
      res match {
        case Full(r) => sender ! r
        case f => sender ! f
      }

    case rViews.revokeAllPermissions(bankId : BankId, accountId : AccountId, user : User) =>

      logger.info("revokeAllPermissions(" + bankId +"," + accountId +","+ user +")")

      {
        for {
          res <- mViews.revokeAllPermissions(bankId, accountId, user)
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case rViews.view(viewUID : ViewUID) =>

      logger.info("view(" + viewUID +")")

      {
        for {
          res <- mViews.view(viewUID)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )


    case rViews.view(viewId: ViewId, bankAccountId: BankAccountUID) =>

      logger.info("view(" + viewId +", "+ bankAccountId + ")")

      {
        for {
          res <- mViews.view(viewId, bankAccountId)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )

    case rViews.createView(bankAccountId : BankAccountUID, view: CreateViewJSON) =>
      logger.info("createView(" + bankAccountId +","+ view +")")
      sender ! mViews.createView(bankAccountId, view)

    case rViews.updateView(bankAccountId : BankAccountUID, viewId : ViewId, viewUpdateJson : UpdateViewJSON) =>
      logger.info("updateView(" + bankAccountId +","+ viewId +","+ viewUpdateJson +")")
      sender ! mViews.updateView(bankAccountId, viewId, viewUpdateJson)

    //case r.view(viewId: ViewId, bankAccountId: BankAccountUID) =>
    //  logger.info("view(" + viewId +","+ bankAccountId +")")
    //  sender ! v.view(ViewId(viewId.value), bankAccountId)

    case rViews.removeView(viewId : ViewId, bankAccountId: BankAccountUID) =>
      logger.info("removeView(" + viewId +","+ bankAccountId +")")
      sender ! mViews.removeView(viewId, bankAccountId)

    case rViews.permissions(bankAccountId : BankAccountUID) =>
      logger.info("premissions(" + bankAccountId +")")
      sender ! mViews.permissions(bankAccountId)

    case rViews.views(bankAccountId : BankAccountUID) =>
      logger.info("views(" + bankAccountId +")")
      sender ! mViews.views(bankAccountId)

    case rViews.permittedViews(user: User, bankAccountId: BankAccountUID) =>
      logger.info("permittedViews(" + user +", " + bankAccountId +")")
      sender ! mViews.permittedViews(user, bankAccountId)

    case rViews.publicViews(bankAccountId : BankAccountUID) =>
      logger.info("publicViews(" + bankAccountId +")")
      sender ! mViews.publicViews(bankAccountId)

    case rViews.getAllPublicAccounts() =>
      logger.info("getAllPublicAccounts()")
      sender ! mViews.getAllPublicAccounts

    case rViews.getPublicBankAccounts(bank : Bank) =>
      logger.info("getPublicBankAccounts(" + bank +")")
      sender ! mViews.getPublicBankAccounts(bank)

    case rViews.getAllAccountsUserCanSee(user : Box[User]) =>
      logger.info("getAllAccountsUserCanSee(" + user +")")
      sender ! mViews.getAllAccountsUserCanSee(user)

    case rViews.getAllAccountsUserCanSee(user : User) =>
      logger.info("getAllAccountsUserCanSee(" + user +")")
      sender ! mViews.getAllAccountsUserCanSee(Full(user))

    case rViews.getAllAccountsUserCanSee(bank: Bank, user : Box[User]) =>
      logger.info("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! mViews.getAllAccountsUserCanSee(bank, user)

    case rViews.getAllAccountsUserCanSee(bank: Bank, user : User) =>
      logger.info("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! mViews.getAllAccountsUserCanSee(bank, Full(user))

    case rViews.getNonPublicBankAccounts(user: User, bankId: BankId) =>
      logger.info("getNonPublicBankAccounts(" + user +", "+ bankId +")")
      sender ! mViews.getNonPublicBankAccounts(user, bankId)

    case rViews.getNonPublicBankAccounts(user: User) =>
      logger.info("getNonPublicBankAccounts(" + user +")")
      sender ! mViews.getNonPublicBankAccounts(user)

    case rViews.createOwnerView(bankId, accountId, description) =>
      logger.info("createOwnerView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mViews.createOwnerView(bankId, accountId, description).orNull

    case rViews.createPublicView(bankId, accountId, description) =>
      logger.info("createPublicView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mViews.createPublicView(bankId, accountId, description).orNull

    case rViews.createAccountantsView(bankId, accountId, description) =>
      logger.info("createAccountantsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mViews.createAccountantsView(bankId, accountId, description).orNull

    case rViews.createAuditorsView(bankId, accountId, description) =>
      logger.info("createAuditorsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mViews.createAuditorsView(bankId, accountId, description).orNull

    case rViews.createRandomView(bankId, accountId) =>
      logger.info("createRandomView(" + bankId +", "+ accountId +")")
      sender ! mViews.createRandomView(bankId, accountId).orNull

    case rViews.getOwners(view) =>
      logger.info("getOwners(" + view +")")
     sender ! mViews.getOwners(view)

    case rViews.grantAccessToView(user, view) =>
      logger.info("grantAccessToView(" + user +", "+ view +")")
      sender ! mViews.grantAccessToView(user, view)

    case rViews.grantAccessToAllExistingViews(user) =>
      logger.info("grantAccessToAllExistingViews(" + user +")")
      sender ! mViews.grantAccessToAllExistingViews(user)

    case rViews.removeAllPermissions(bankId, accountId) =>
      logger.info("removeAllPermissions(" + bankId +", "+ accountId +")")
      sender ! mViews.removeAllPermissions(bankId, accountId)

    case rViews.removeAllViews(bankId, accountId) =>
      logger.info("removeAllViews(" + bankId +", "+ accountId +")")
      sender ! mViews.removeAllViews(bankId, accountId)

    // Resource User part
    case rUsers.getUserByResourceUserId(id: Long) =>
      logger.info("getUserByResourceUserId(" + id +")")

      {
        for {
          res <- mUsers.getUserByResourceUserId(id)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getResourceUserByResourceUserId(id: Long) =>
      logger.info("getResourceUserByResourceUserId(" + id +")")

      {
        for {
          res <- mUsers.getResourceUserByResourceUserId(id)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getUserByProviderId(provider : String, idGivenByProvider : String) =>
      logger.info("getUserByProviderId(" + provider +"," + idGivenByProvider +")")

      {
        for {
          res <- mUsers.getUserByProviderId(provider, idGivenByProvider)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getUserByUserId(userId: String) =>
      logger.info("getUserByUserId(" + userId +")")

      {
        for {
          res <- mUsers.getUserByUserId(userId)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getUserByUserName(userName: String) =>
      logger.info("getUserByUserName(" + userName +")")

      {
        for {
          res <- mUsers.getUserByUserName(userName)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getUserByEmail(email: String) =>
      logger.info("getUserByEmail(" + email +")")

      {
        for {
          res <- mUsers.getUserByEmail(email)
        } yield {
          sender ! res
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getAllUsers() =>
      logger.info("getAllUsers()")

      {
        for {
          res <- mUsers.getAllUsers()
        } yield {
          sender ! res
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.info("createResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")

      {
        for {
          res <- mUsers.createResourceUser(provider, providerId, name, email, userId)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.info("createUnsavedResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")

      {
        for {
          res <- mUsers.createUnsavedResourceUser(provider, providerId, name, email, userId)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.saveResourceUser(resourceUser: ResourceUser) =>
      logger.info("saveResourceUser")

      {
        for {
          res <- mUsers.saveResourceUser(resourceUser)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.deleteResourceUser(id: Long) =>
      logger.info("deleteResourceUser(" + id +")")

      {
        for {
          res <- tryo{mUsers.deleteResourceUser(id)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case rCounterparties.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)=>
      logger.info("checkCounterpartyAvailable(" + name +", "+ thisBankId +", "+ thisAccountId +", "+ thisViewId +")")
      sender ! mCounterparties.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)

    case rCounterparties.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId, name, otherBankId, otherAccountId,
                                            otherAccountRoutingScheme, otherAccountRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress,
                                            isBeneficiary) =>
      logger.info("createCounterparty(" + createdByUserId +", "+ thisBankId +", "+ thisAccountId +", "+ thisViewId +", "+ name +", "+ otherBankId + otherAccountId +", "
                    + otherAccountRoutingScheme +", "+ otherAccountRoutingAddress +", "+ otherBankRoutingScheme +", "+ otherBankRoutingAddress +", "+ isBeneficiary+ ")")

      {
        for {
          res <- mCounterparties.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId, name, otherBankId, otherAccountId,
                                                    otherAccountRoutingScheme, otherAccountRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress,
                                                    isBeneficiary)
        } yield {
          sender ! res.asInstanceOf[CounterpartyTrait]
        }
      }.getOrElse( context.stop(sender) )



    case rCounterparties.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty) =>
      logger.info("getOrCreateMetadata(" + originalPartyBankId +", " +originalPartyAccountId+otherParty+")")

      {
        for {
          res <- mCounterparties.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty)
        } yield {
          sender ! res.asInstanceOf[CounterpartyMetadata]
        }
      }.getOrElse( context.stop(sender) )

    case rCounterparties.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId) =>
      logger.info("getOrCreateMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")

      Full({
             for {
               res <- Full(mCounterparties.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId))
             } yield {
               sender ! res.asInstanceOf[List[CounterpartyMetadata]]
             }
           }).getOrElse(context.stop(sender))


    case rCounterparties.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String) =>
        logger.info("getMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")

      {
        for {
          res <- mCounterparties.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String)
        } yield {
          sender ! res.asInstanceOf[CounterpartyMetadata]
        }
      }.getOrElse( context.stop(sender) )



    case rCounterparties.getCounterparty(counterPartyId: String) =>
      logger.info("getCounterparty(" + counterPartyId +")")

      {
        for {
          res <- mCounterparties.getCounterparty(counterPartyId: String)
        } yield {
          sender ! res.asInstanceOf[CounterpartyTrait]
        }
      }.getOrElse( context.stop(sender) )


    case rCounterparties.getCounterpartyByIban(iban: String) =>

      logger.info("getOrCreateMetadata(" + iban +")")

      {
        for {
          res <- mCounterparties.getCounterpartyByIban(iban: String)
        } yield {
          sender ! res.asInstanceOf[CounterpartyTrait]
        }
      }.getOrElse( context.stop(sender) )


    case rAccountHolders.createAccountHolder(userId: Long, bankId: String, accountId: String, source: String) =>

      logger.info("createAccountHolder(" + userId +", "+ bankId +", "+ accountId +", "+ source +")")

        {
        for {
          res <- tryo{mAccountHolders.createAccountHolder(userId, bankId, accountId, source)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case rAccountHolders.getAccountHolders(bankId: BankId, accountId: AccountId) =>

      logger.info("getAccountHolders(" + bankId +", "+ accountId +")")

        {
        for {
          res <- tryo{mAccountHolders.getAccountHolders(bankId, accountId)}
        } yield {
          sender ! res.asInstanceOf[Set[User]]
        }
      }.getOrElse( context.stop(sender) )


    case rAccountHolders.bulkDeleteAllAccountHolders() =>

      logger.info("bulkDeleteAllAccountHolders()")

        {
        for {
          res <- mAccountHolders.bulkDeleteAllAccountHolders()
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case rUsers.bulkDeleteAllResourceUsers() =>

      logger.info("bulkDeleteAllResourceUsers()")

      {
        for {
          res <- mUsers.bulkDeleteAllResourceUsers()
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )



    case rViews.bulkDeleteAllPermissionsAndViews() =>

      logger.info("bulkDeleteAllPermissionsAndViews()")

      {
        for {
          res <- tryo{mViews.bulkDeleteAllPermissionsAndViews()}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    // METADATA
    // Comments
    case rComments.getComments(bankId, accountId, transactionId, viewId) =>
      logger.info("getComments(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! mComments.getComments(bankId, accountId, transactionId)(viewId)

    case rComments.addComment(bankId, accountId, transactionId, userId, viewId, text, datePosted) =>
      logger.info("addComment(" + bankId +", "+ accountId +", "+ transactionId +", "+ text +", "+ text +", "+ datePosted +")")

      {
        for {
          res <- mComments.addComment(bankId, accountId, transactionId)(userId, viewId, text, datePosted)
        } yield {
          sender ! res.asInstanceOf[Comment]
        }
      }.getOrElse( context.stop(sender) )

    case rComments.deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId, commentId : String) =>
      logger.info("deleteComment(" + bankId +", "+ accountId +", "+ transactionId + commentId +")")

      {
        for {
          res <- mComments.deleteComment(bankId, accountId, transactionId)(commentId)
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case rComments.bulkDeleteComments(bankId: BankId, accountId: AccountId) =>

      logger.info("bulkDeleteComments(" + bankId +", "+ accountId + ")")

      {
        for {
          res <- tryo{mComments.bulkDeleteComments(bankId, accountId)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    // Tags
    case rTags.getTags(bankId, accountId, transactionId, viewId) =>
      logger.info("getTags(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! mTags.getTags(bankId, accountId, transactionId)(viewId)

    case rTags.addTag(bankId, accountId, transactionId, userId, viewId, text, datePosted) =>
      logger.info("addTag(" + bankId +", "+ accountId +", "+ transactionId +", "+ text +", "+ text +", "+ datePosted +")")

      {
        for {
          res <- mTags.addTag(bankId, accountId, transactionId)(userId, viewId, text, datePosted)
        } yield {
          sender ! res.asInstanceOf[TransactionTag]
        }
      }.getOrElse( context.stop(sender) )

    case rTags.deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, tagId : String) =>
      logger.info("deleteTag(" + bankId +", "+ accountId +", "+ transactionId + tagId +")")

      {
        for {
          res <- mTags.deleteTag(bankId, accountId, transactionId)(tagId)
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case rTags.bulkDeleteTags(bankId: BankId, accountId: AccountId) =>

      logger.info("bulkDeleteTags(" + bankId +", "+ accountId + ")")

      {
        for {
          res <- tryo{mTags.bulkDeleteTags(bankId, accountId)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    // WhereTags
    case rWhereTags.getWhereTagForTransaction(bankId, accountId, transactionId, viewId) =>
      logger.info("getWhereTagForTransaction(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! mWhereTags.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId)

    case rWhereTags.bulkDeleteWhereTags(bankId: BankId, accountId: AccountId) =>

      logger.info("bulkDeleteWhereTags(" + bankId +", "+ accountId + ")")

      {
        for {
          res <- tryo{mWhereTags.bulkDeleteWhereTags(bankId, accountId)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case rWhereTags.deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId) =>

      logger.info("deleteWhereTag(" + bankId +", "+ accountId + ", "+ transactionId + ", "+ viewId + ")")

      {
        for {
          res <- tryo{mWhereTags.deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case rWhereTags.addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) =>

      logger.info("addWhereTag(" + bankId +", "+ accountId + ", "+ transactionId + ", "+ userId + ", " + viewId + ", "+ datePosted +  ", "+ longitude +  ", "+ latitude + ")")

      {
        for {
          res <- tryo{mWhereTags.addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    // TransactionImages
    case rTransactionImages.getImagesForTransaction(bankId, accountId, transactionId, viewId) =>
      logger.info("getImagesForTransaction(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! mTransactionImages.getImagesForTransaction(bankId, accountId, transactionId)(viewId)

    case rTransactionImages.addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserId, viewId : ViewId, description : String, datePosted : Date, imageURL: String) =>
      logger.info("addTransactionImage( " + bankId +", "+ accountId +", "+ transactionId +", "+ userId +", "+ viewId + ", "+ description + ", " + datePosted + ", " + imageURL + ")")

      {
        for {
          res <- mTransactionImages.addTransactionImage(bankId, accountId, transactionId)(userId, viewId, description, datePosted, imageURL)
        } yield {
          sender ! res.asInstanceOf[TransactionImage]
        }
      }.getOrElse( context.stop(sender) )

    case rTransactionImages.deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId, imageId : String) =>
      logger.info("deleteTransactionImage(" + bankId +", "+ accountId +", "+ transactionId + imageId +")")

      {
        for {
          res <- mTransactionImages.deleteTransactionImage(bankId, accountId, transactionId)(imageId)
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case rTransactionImages.bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId) =>

      logger.info("bulkDeleteTransactionImage(" + bankId +", "+ accountId + ")")

      {
        for {
          res <- tryo{mTransactionImages.bulkDeleteTransactionImage(bankId, accountId)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )





    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}












object RemotedataActorSystem extends Loggable {
  implicit val timeout = Timeout(1 seconds)

  def startRemoteWorkerSystem(): Unit = {
    val remote = ActorSystem("OBPDataWorkerSystem", ConfigFactory.load("obpremotedata"))
    val actor = remote.actorOf(ActorProps[RemotedataActor], name = "OBPRemoteDataActor")
    logger.info("Started OBPDataWorkerSystem")
  }

  def startLocalWorkerSystem(): Unit = {
    val remote = ActorSystem("OBPDataWorkerSystem", ConfigFactory.load("obplocaldata"))
    val actor = remote.actorOf(ActorProps[RemotedataActor], name = "OBPLocalDataActor")
    logger.info("Started OBPDataWorkerSystem locally")
  }

  def setupRemotedataDB(): Unit = {
    // set up the way to connect to the relational DB we're using (ok if other connector than relational)
    if (!DB.jndiJdbcConnAvailable_?) {
      val driver =
        Props.mode match {
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development => Props.get("remotedata.db.driver") openOr "org.h2.Driver"
          case _ => "org.h2.Driver"
        }
      val vendor =
        Props.mode match {
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development =>
            new StandardDBVendor(driver,
              Props.get("remotedata.db.url") openOr "jdbc:h2:./lift_proto.remotedata.db;AUTO_SERVER=TRUE",
              Props.get("remotedata.db.user"), Props.get("remotedata.db.password"))
          case _ =>
            new StandardDBVendor(
              driver,
              "jdbc:h2:mem:OBPData;DB_CLOSE_DELAY=-1",
              Empty, Empty)
        }

      logger.debug("Using database driver: " + driver)
      LiftRules.unloadHooks.append(vendor.closeAllConnections_! _)

      DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier, vendor)
    }
  }

  // Entry point if running as standalone remote data server, without jetty
  def main (args: Array[String]): Unit = {
    if (args.length >= 1 && args(0) == "standalone") {
      setupRemotedataDB()
      showLogoAfterDelay()
      startRemoteWorkerSystem()
    }
  }

  def showLogoAfterDelay() = {
    val actorSystem = ActorSystem()
    implicit val executor = actorSystem.dispatcher
    val scheduler = actorSystem.scheduler
    scheduler.scheduleOnce(
      Duration(4, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = {
          println(
            """
              |     ______    _______    _______                 __         _______   __
              |    /    " \  |   _  "\  |   __ "\               /""\       |   __ "\ |" \
              |   // ____  \ (. |_)  :) (. |__) :)   _____     /    \      (. |__) :)||  |
              |  /  /    ) :)|:     \/  |:  ____/   //   ")   /' /\  \     |:  ____/ |:  |
              | (: (____/ // (|  _  \\  (|  /      (_____/   //  __'  \    (|  /     |.  |
              |  \        /  |: |_)  :)/|__/ \              /   /  \\  \  /|__/ \    /\  |\
              |   \"_____/   (_______/(_______)            (___/    \___)(_______)  (__\_|_)
              |       _______    _______  ___      ___     ______  ___________  _______
              |      /"      \  /"     "||"  \    /"  |   /    " \("     _   ")/"     "|
              |     |:        |(: ______) \   \  //   |  // ____  \)__/  \\__/(: ______)
              |     |_____/   ) \/    |   /\\  \/.    | /  /    ) :)  \\_ /    \/    |
              |      //      /  // ___)_ |: \.        |(: (____/ //   |.  |    // ___)_
              |     |:  __   \ (:      "||.  \    /:  | \        /    \:  |   (:      "|
              |     |__|  \___) \_______)|___|\__/|___|  \"_____/      \__|    \_______)
              |               __       ______  ___________  ______     _______
              |              /""\     /" _  "\("     _   ")/    " \   /"      \
              |             /    \   (: ( \___))__/  \\__/// ____  \ |:        |
              |            /' /\  \   \/ \        \\_ /  /  /    ) :)|_____/   )
              |           //  __'  \  //  \ _     |.  | (: (____/ //  //      /
              |          /   /  \\  \(:   _) \    \:  |  \        /  |:  __   \
              |         (___/    \___)\_______)    \__|   \"_____/   |__|  \___)
              |""".stripMargin)
        }
      }
    )
  }

}
