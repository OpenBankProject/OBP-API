package code.remotedata

import java.util.Date
import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.tags.{RemotedataTagsCaseClasses, Tags}
import code.model._
import net.liftweb.common.Box
import scala.collection.immutable.List


object RemotedataTags extends ObpActorInit with Tags {

  val cc = RemotedataTagsCaseClasses

  def getTags(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[TransactionTag] =
    extractFuture(actor ? cc.getTags(bankId, accountId, transactionId, viewId))

  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag] =
    extractFutureToBox(actor ? cc.addTag(bankId, accountId, transactionId, userId, viewId, tagText, datePosted))

  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteTag(bankId, accountId, transactionId, tagId))

  def bulkDeleteTags(bankId: BankId, accountId: AccountId): Boolean =
    extractFuture(actor ? cc.bulkDeleteTags(bankId, accountId))


}
