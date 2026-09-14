/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.counterpartyattribute

import com.openbankproject.commons.model.CounterpartyId
import com.openbankproject.commons.model.enums.CounterpartyAttributeType
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object CounterpartyAttributeX extends SimpleInjector {

  val counterpartyAttributeProvider = new Inject(() => buildOne) {}

  def buildOne: CounterpartyAttributeProviderTrait = CounterpartyAttributeProvider

  // Helper to get the count out of an option
  def countOfCounterpartyAttribute(listOpt: Option[List[CounterpartyAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait CounterpartyAttributeProviderTrait {

  def getCounterpartyAttributes(counterpartyId: CounterpartyId): Future[Box[List[CounterpartyAttribute]]]

  def getCounterpartyAttributeById(counterpartyAttributeId: String): Future[Box[CounterpartyAttribute]]

  def createOrUpdateCounterpartyAttribute(
    counterpartyId: CounterpartyId,
    counterpartyAttributeId: Option[String],
    name: String,
    attributeType: CounterpartyAttributeType.Value,
    value: String,
    isActive: Option[Boolean]): Future[Box[CounterpartyAttribute]]

  def deleteCounterpartyAttribute(counterpartyAttributeId: String): Future[Box[Boolean]]

  def deleteCounterpartyAttributesByCounterpartyId(counterpartyId: CounterpartyId): Future[Box[Boolean]]
}
