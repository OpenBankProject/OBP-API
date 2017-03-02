package code.metadata.wheretags

import net.liftweb.util.SimpleInjector
import java.util.Date

import code.model._
import code.remotedata.Remotedata
import net.liftweb.common.Box

object WhereTags  extends SimpleInjector {

  val whereTags = new Inject(buildOne _) {}

  def buildOne: WhereTags = MapperWhereTags
  //def buildOne: WhereTags = Remotedata

}

trait WhereTags {

  //TODO: it probably makes more sense for this to return Box[GeoTag]. Leaving it as a Boolean for now...
  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                 (userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) : Boolean

  //TODO: would be nicer to change this to return Box[Unit] like in e.g. comments. Or perhaps change the way the other ones work
  //instead, with the end effect of keeping them consistent. Leaving it as a Boolean for now...
  def deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Boolean

  def getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Box[GeoTag]

  def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId) : Boolean

}

class RemoteWhereTagsCaseClasses {
  case class addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double)
  case class deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId)
  case class getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId)
  case class bulkDeleteWhereTags(bankId: BankId, accountId: AccountId)
}

object RemoteWhereTagsCaseClasses extends RemoteWhereTagsCaseClasses