package code.metadata.counterparties

import code.api.util.APIUtil
import com.openbankproject.commons.model.CounterpartyBespoke
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

object CounterpartyBespokes extends SimpleInjector {

  val counterpartyBespokers = new Inject(buildOne _) {}

  def buildOne: CounterpartyBespokes = MapperCounterpartyBespokes

}

trait CounterpartyBespokes {
  //Note: Here is tricky, it return the MappedCounterpartyBespoke not the CounterpartyBespokeTrait, because it will be used in `one-to-many` model ...
  def createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke]): List[MappedCounterpartyBespoke]
  def getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long): List[MappedCounterpartyBespoke]
}
