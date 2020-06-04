package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.transactionimages.{RemotedataTransactionImagesCaseClasses, TransactionImages}
import code.model._
import com.openbankproject.commons.model._

import scala.collection.immutable.List
import net.liftweb.common.Box

object RemotedataTransactionImages extends ObpActorInit with TransactionImages {

  val cc = RemotedataTransactionImagesCaseClasses

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionImage] = getValueFromFuture(
    (actor ? cc.getImagesForTransaction(bankId, accountId, transactionId, viewId)).mapTo[List[TransactionImage]]
  )

  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                         (userId: UserPrimaryKey, viewId : ViewId, description : String, datePosted : Date, imageURL: String) : Box[TransactionImage] = getValueFromFuture(
    (actor ? cc.addTransactionImage(bankId, accountId, transactionId, userId, viewId, description, datePosted, imageURL)).mapTo[Box[TransactionImage]]
  )
  
  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.deleteTransactionImage(bankId, accountId, transactionId, imageId)).mapTo[Box[Boolean]]
  ) 
  
  def bulkDeleteImagesOnTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId) : Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteImagesOnTransaction(bankId, accountId, transactionId)).mapTo[Boolean]
  )
  
  def bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteTransactionImage(bankId, accountId)).mapTo[Boolean]
  )
  
}
