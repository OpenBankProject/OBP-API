package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.transactionimages.{RemotedataTransactionImagesCaseClasses, TransactionImages}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataTransactionImages extends ActorInit with TransactionImages {

  val cc = RemotedataTransactionImagesCaseClasses

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionImage] = {
    Await.result(
      (actor ? cc.getImagesForTransaction(bankId, accountId, transactionId, viewId)).mapTo[List[TransactionImage]],
      TIMEOUT
    )
  }

  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                         (userId: UserId, viewId : ViewId, description : String, datePosted : Date, imageURL: String) : Box[TransactionImage] = {
    Full(
      Await.result(
        (actor ? cc.addTransactionImage(bankId, accountId, transactionId, userId, viewId, description, datePosted, imageURL)).mapTo[TransactionImage],
        TIMEOUT
      )
    )
  }

  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Boolean] = {
    val res = try {
      Full(
        Await.result(
          (actor ? cc.deleteTransactionImage(bankId, accountId, transactionId, imageId)).mapTo[Boolean],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Cannot delete the TransactionImage", 404)
      case e: Throwable => throw e
    }
    res
  }

  def bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (actor ? cc.bulkDeleteTransactionImage(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }


}
