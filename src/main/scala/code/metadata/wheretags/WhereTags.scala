package code.metadata.wheretags

import java.util.Date

import code.api.util.APIUtil
import code.model._
import code.remotedata.RemotedataWhereTags
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object WhereTags  extends SimpleInjector {

  val whereTags = new Inject(buildOne _) {}

  def buildOne: WhereTags =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MapperWhereTags
      case true => RemotedataWhereTags     // We will use Akka as a middleware
    }

}

trait WhereTags {

  //TODO: it probably makes more sense for this to return Box[GeoTag]. Leaving it as a Boolean for now...
  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                 (userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) : Boolean

  //TODO: would be nicer to change this to return Box[Unit] like in e.g. comments. Or perhaps change the way the other ones work
  //instead, with the end effect of keeping them consistent. Leaving it as a Boolean for now...
  def deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Boolean

  def getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Box[GeoTag]

  def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId) : Boolean

}

class RemotedataWhereTagsCaseClasses {
  case class addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double)
  case class deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId)
  case class getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId)
  case class bulkDeleteWhereTags(bankId: BankId, accountId: AccountId)
}

object RemotedataWhereTagsCaseClasses extends RemotedataWhereTagsCaseClasses
