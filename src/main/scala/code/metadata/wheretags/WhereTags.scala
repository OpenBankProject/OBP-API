package code.metadata.wheretags

import net.liftweb.util.SimpleInjector
import java.util.Date
import code.model.{AccountId, BankId, GeoTag}

object WhereTags  extends SimpleInjector {

  val whereTags = new Inject(buildOne _) {}

  def buildOne: WhereTags = MongoTransactionWhereTags

}

trait WhereTags {

  //TODO: it probably makes more sense for this to return Box[GeoTag]. Leaving it as a Boolean for now...
  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: String)
                 (userId: String, viewId : Long, datePosted : Date, longitude : Double, latitude : Double) : Boolean

  //TODO: would be nicer to change this to return Box[Unit] like in e.g. comments. Or perhaps change the way the other ones work
  //instead, with the end effect of keeping them consistent. Leaving it as a Boolean for now...
  def deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: String)(viewId : Long) : Boolean

  def getWhereTagsForTransaction(bankId : BankId, accountId : AccountId, transactionId: String)() : List[GeoTag]

}
