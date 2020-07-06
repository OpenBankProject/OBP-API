package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.tags.{RemotedataTagsCaseClasses, Tags}
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box

import scala.collection.immutable.List


object RemotedataTags extends ObpActorInit with Tags {

  val cc = RemotedataTagsCaseClasses

  def getTags(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[TransactionTag] = getValueFromFuture(
    (actor ? cc.getTags(bankId, accountId, transactionId, viewId)).mapTo[List[TransactionTag]]
  )

  def getTagsOnAccount(bankId : BankId, accountId : AccountId)(viewId : ViewId) : List[TransactionTag] = getValueFromFuture(
    (actor ? cc.getTagsOnAccount(bankId, accountId, viewId)).mapTo[List[TransactionTag]]
  )

  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag] = getValueFromFuture(
    (actor ? cc.addTag(bankId, accountId, transactionId, userId, viewId, tagText, datePosted)).mapTo[Box[TransactionTag]]
  )
  def addTagOnAccount(bankId : BankId, accountId : AccountId)(userId: UserPrimaryKey, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag] = getValueFromFuture(
    (actor ? cc.addTagOnAccount(bankId, accountId, userId, viewId, tagText, datePosted)).mapTo[Box[TransactionTag]]
  )

  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.deleteTag(bankId, accountId, transactionId, tagId)).mapTo[Box[Boolean]]
  )
  def deleteTagOnAccount(bankId : BankId, accountId : AccountId)(tagId : String) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.deleteTagOnAccount(bankId, accountId, tagId)).mapTo[Box[Boolean]]
  )

  def bulkDeleteTags(bankId: BankId, accountId: AccountId): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteTags(bankId, accountId)).mapTo[Boolean]
  )
  
  def bulkDeleteTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteTagsOnTransaction(bankId, accountId, transactionId)).mapTo[Boolean]
  )


}
