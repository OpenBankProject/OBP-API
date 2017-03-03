package code.remotedata

import java.util.Date

import akka.actor.Actor
import akka.event.Logging
import code.metadata.transactionimages.{MapperTransactionImages, RemotedataTransactionImagesCaseClasses}
import code.model._
import net.liftweb.util.ControlHelpers.tryo


class RemotedataTransactionImagesActor extends Actor {

  val logger = Logging(context.system, this)

  val mapper = MapperTransactionImages
  val cc = RemotedataTransactionImagesCaseClasses

  def receive = {

    case cc.getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId) =>

      logger.info("getImagesForTransaction(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")

      sender ! mapper.getImagesForTransaction(bankId, accountId, transactionId)(viewId)

    case cc.addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserId, viewId : ViewId, description : String, datePosted : Date, imageURL: String) =>

      logger.info("addTransactionImage( " + bankId +", "+ accountId +", "+ transactionId +", "+ userId +", "+ viewId + ", "+ description + ", " + datePosted + ", " + imageURL + ")")

      {
        for {
          res <- mapper.addTransactionImage(bankId, accountId, transactionId)(userId, viewId, description, datePosted, imageURL)
        } yield {
          sender ! res.asInstanceOf[TransactionImage]
        }
      }.getOrElse( context.stop(sender) )

    case cc.deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId, imageId : String) =>

      logger.info("deleteTransactionImage(" + bankId +", "+ accountId +", "+ transactionId + imageId +")")

      {
        for {
          res <- mapper.deleteTransactionImage(bankId, accountId, transactionId)(imageId)
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case cc.bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId) =>

      logger.info("bulkDeleteTransactionImage(" + bankId +", "+ accountId + ")")

      {
        for {
          res <- tryo{mapper.bulkDeleteTransactionImage(bankId, accountId)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

