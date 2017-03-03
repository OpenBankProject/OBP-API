package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.wheretags.{RemotedataWhereTagsCaseClasses, WhereTags}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataWhereTags extends ActorInit with WhereTags {

  val cc = RemotedataWhereTagsCaseClasses

  def getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Box[GeoTag] = {
    Await.result(
      (ac ? cc.getWhereTagForTransaction(bankId, accountId, transactionId, viewId)).mapTo[Box[GeoTag]],
      TIMEOUT
    )
  }

  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                 (userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    Await.result(
      (ac ? cc.addWhereTag(bankId, accountId, transactionId, userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Boolean = {
    Await.result(
      (ac ? cc.deleteWhereTag(bankId, accountId, transactionId, viewId)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (ac ? cc.bulkDeleteWhereTags(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }

}
