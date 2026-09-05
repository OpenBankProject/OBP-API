package code.counterpartyattribute

import com.openbankproject.commons.model.{CounterpartyAttributeTrait, CounterpartyId}
import com.openbankproject.commons.model.enums.CounterpartyAttributeType
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object CounterpartyAttributeX extends SimpleInjector {

  val counterpartyAttributeProvider = new Inject(() => buildOne) {}

  def buildOne: CounterpartyAttributeProviderTrait = DoobieCounterpartyAttributeProvider

  // Helper to get the count out of an option
  def countOfCounterpartyAttribute(listOpt: Option[List[CounterpartyAttributeTrait]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait CounterpartyAttributeProviderTrait {

  def getCounterpartyAttributes(counterpartyId: CounterpartyId): Future[Box[List[CounterpartyAttributeTrait]]]

  def getCounterpartyAttributeById(counterpartyAttributeId: String): Future[Box[CounterpartyAttributeTrait]]

  def createOrUpdateCounterpartyAttribute(
    counterpartyId: CounterpartyId,
    counterpartyAttributeId: Option[String],
    name: String,
    attributeType: CounterpartyAttributeType.Value,
    value: String,
    isActive: Option[Boolean]): Future[Box[CounterpartyAttributeTrait]]

  def deleteCounterpartyAttribute(counterpartyAttributeId: String): Future[Box[Boolean]]

  def deleteCounterpartyAttributesByCounterpartyId(counterpartyId: CounterpartyId): Future[Box[Boolean]]
}
