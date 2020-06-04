package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.transactionimages.{MapperTransactionImages, RemotedataTransactionImagesCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._


class RemotedataTransactionImagesActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperTransactionImages
  val cc = RemotedataTransactionImagesCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId) =>
      logger.debug(s"getImagesForTransaction($bankId, $accountId, $transactionId, $viewId)")
      sender ! (mapper.getImagesForTransaction(bankId, accountId, transactionId)(viewId))

    case cc.addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserPrimaryKey, viewId : ViewId, description : String, datePosted : Date, imageURL: String) =>
      logger.debug(s"addTransactionImage($bankId, $accountId, $transactionId, $userId, $viewId, $description, $datePosted, $imageURL)")
      sender ! (mapper.addTransactionImage(bankId, accountId, transactionId)(userId, viewId, description, datePosted, imageURL))

    case cc.deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId, imageId : String) =>
      logger.debug(s"deleteTransactionImage($bankId, $accountId, $transactionId, $imageId)")
      sender ! (mapper.deleteTransactionImage(bankId, accountId, transactionId)(imageId))

    case cc.bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId) =>
      logger.debug(s"bulkDeleteTransactionImage($bankId, $accountId)")
      sender ! (mapper.bulkDeleteTransactionImage(bankId, accountId))
      
    case cc.bulkDeleteImagesOnTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId) =>
      logger.debug(s"bulkDeleteImagesOnTransaction($bankId, $accountId, $transactionId)")
      sender ! (mapper.bulkDeleteImagesOnTransaction(bankId, accountId, transactionId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

