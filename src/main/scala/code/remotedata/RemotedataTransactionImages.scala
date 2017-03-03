package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.transactionimages.{RemoteTransactionImagesCaseClasses, TransactionImages}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataTransactionImages extends TransactionImages {

  implicit val timeout = Timeout(10000 milliseconds)
  val TIMEOUT = 10 seconds
  val rTransactionImages = RemoteTransactionImagesCaseClasses
  var actor = RemotedataActorSystem.getActor("TransactionImages")

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionImage] = {
    Await.result(
      (actor ? rTransactionImages.getImagesForTransaction(bankId, accountId, transactionId, viewId)).mapTo[List[TransactionImage]],
      TIMEOUT
    )
  }

  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                         (userId: UserId, viewId : ViewId, description : String, datePosted : Date, imageURL: String) : Box[TransactionImage] = {
    Full(
      Await.result(
        (actor ? rTransactionImages.addTransactionImage(bankId, accountId, transactionId, userId, viewId, description, datePosted, imageURL)).mapTo[TransactionImage],
        TIMEOUT
      )
    )
  }

  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Boolean] = {
    val res = try {
      Full(
        Await.result(
          (actor ? rTransactionImages.deleteTransactionImage(bankId, accountId, transactionId, imageId)).mapTo[Boolean],
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
      (actor ? rTransactionImages.bulkDeleteTransactionImage(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }


}
