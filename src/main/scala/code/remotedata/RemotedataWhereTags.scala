package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.wheretags.{RemotedataWhereTagsCaseClasses, WhereTags}
import code.model._
import net.liftweb.common.Box


object RemotedataWhereTags extends ObpActorInit with WhereTags {

  val cc = RemotedataWhereTagsCaseClasses

  def getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Box[GeoTag] =
      extractFutureToBox(actor ? cc.getWhereTagForTransaction(bankId, accountId, transactionId, viewId))

  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                 (userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) : Boolean =
      extractFuture(actor ? cc.addWhereTag(bankId, accountId, transactionId, userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double))

  def deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Boolean =
      extractFuture(actor ? cc.deleteWhereTag(bankId, accountId, transactionId, viewId))

  def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId): Boolean =
      extractFuture(actor ? cc.bulkDeleteWhereTags(bankId, accountId))


}
