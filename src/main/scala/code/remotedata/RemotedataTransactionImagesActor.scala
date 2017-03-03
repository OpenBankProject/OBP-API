package code.remotedata

import java.util.Date

import akka.actor.Actor
import akka.event.Logging
import code.metadata.transactionimages.{MapperTransactionImages, RemoteTransactionImagesCaseClasses}
import code.model._
import net.liftweb.util.ControlHelpers.tryo


class RemotedataTransactionImagesActor extends Actor {

  val logger = Logging(context.system, this)

  val mTransactionImages = MapperTransactionImages
  val rTransactionImages = RemoteTransactionImagesCaseClasses

  def receive = {

    case rTransactionImages.getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId) =>
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

