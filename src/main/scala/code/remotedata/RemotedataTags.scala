package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.tags.{RemotedataTagsCaseClasses, Tags}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataTags extends ActorInit with Tags {

  val cc = RemotedataTagsCaseClasses

  def getTags(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[TransactionTag] =
    extractFuture(actor ? cc.getTags(bankId, accountId, transactionId, viewId))

  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag] =
    extractFutureToBox(actor ? cc.addTag(bankId, accountId, transactionId, userId, viewId, tagText, datePosted))

  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteTag(bankId, accountId, transactionId, tagId))

  def bulkDeleteTags(bankId: BankId, accountId: AccountId): Boolean =
    extractFuture(actor ? cc.bulkDeleteTags(bankId, accountId))


}
