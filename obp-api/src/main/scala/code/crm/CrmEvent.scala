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

package code.crm

/* For crmEvents */



import code.crm.CrmEvent.{CrmEvent, CrmEventId}
import code.model.dataAccess.ResourceUser
import code.model.dataAccess.ResourceUser
import net.liftweb.common.Logger
import net.liftweb.util
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable
import java.util.Date

import com.openbankproject.commons.model.{BankId, MetaT}

object CrmEvent extends util.SimpleInjector {

  case class CrmEventId(value : String)


  trait CrmEvent {
    def crmEventId: CrmEventId
    def bankId: BankId
    def user: ResourceUser
    def customerName : String
    def customerNumber : String // Is this duplicate of ResourceUser?
    def category : String
    def detail : String
    def channel : String
    def scheduledDate : Date
    def actualDate: Date
    def result: String}

  val crmEventProvider = new Inject(() => buildOne) {}

  def buildOne: CrmEventProvider = MappedCrmEventProvider

  // Helper to get the count out of an option
  def countOfCrmEvents (listOpt: Option[List[CrmEvent]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait CrmEventProvider extends MdcLoggable {


  /*
  Common logic for returning all crmEvents at a bank
   */
  final def getCrmEvents(bankId : BankId) : Option[List[CrmEvent]] = {
    // If we get crmEvents filter them
    getEventsFromProvider(bankId) match {
      case Some(allItems) => {
        val returnItems = for {
          item <- allItems // No filtering required
        } yield item
        Option(returnItems)
      }
      case None => None
    }
  }

  /*
  Common logic for returning crmEvents at a bank for one user
   */
  final def getCrmEvents(bankId : BankId, user : ResourceUser) : Option[List[CrmEvent]] = {
    getEventsFromProvider(bankId, user) // No filter required
  }

  /*
  Common logic for returning one crmEvent
 */
  final def getCrmEvent(crmEventId: CrmEventId) : Option[CrmEvent] = {
    getEventFromProvider(crmEventId) // No filter required
  }




  // For the whole bank
  protected def getEventsFromProvider(bank : BankId) : Option[List[CrmEvent]]

  // For a user
  protected def getEventsFromProvider(bank : BankId, user : ResourceUser) : Option[List[CrmEvent]]

  // One event
  protected def getEventFromProvider(crmEventId: CrmEventId) : Option[CrmEvent]




  // End of Trait
}
