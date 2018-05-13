package code.metadata.counterparties

import code.api.util.APIUtil
import code.model.CounterpartyBespoke
import code.remotedata.RemotedataCounterpartyBespokes
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

object CounterpartyBespokes extends SimpleInjector {

  val counterpartyBespokers = new Inject(buildOne _) {}

  def buildOne: CounterpartyBespokes =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MapperCounterpartyBespokes
      case true => RemotedataCounterpartyBespokes     // We will use Akka as a middleware
    }

}

trait CounterpartyBespokes {
  //Note: Here is tricky, it return the MappedCounterpartyBespoke not the CounterpartyBespokeTrait, because it will be used in `one-to-many` model ...
  def createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke]): List[MappedCounterpartyBespoke]
  def getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long): List[MappedCounterpartyBespoke]
}

class RemotedataCounterpartyBespokesCaseClasses {
  case class createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke])
  case class getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long)
}

object RemotedataCounterpartyBespokesCaseClasses extends RemotedataCounterpartyBespokesCaseClasses
