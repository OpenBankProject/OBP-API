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

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionImage] =
    extractFuture(actor ? cc.getImagesForTransaction(bankId, accountId, transactionId, viewId))

  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                         (userId: UserId, viewId : ViewId, description : String, datePosted : Date, imageURL: String) : Box[TransactionImage] =
    extractFutureToBox(actor ? cc.addTransactionImage(bankId, accountId, transactionId, userId, viewId, description, datePosted, imageURL))

  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteTransactionImage(bankId, accountId, transactionId, imageId))

  def bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId): Boolean =
    extractFuture(actor ? cc.bulkDeleteTransactionImage(bankId, accountId))

}
