package code.crm

/* For crmEvents */



import code.crm.CrmEvent.{CrmEvent, CrmEventId}
import code.model.BankId
import code.common.{AddressT, LocationT, MetaT}
import code.model.dataAccess.ResourceUser
import code.model.dataAccess.ResourceUser
import net.liftweb.common.Logger
import net.liftweb.util
import net.liftweb.util.SimpleInjector

import java.util.Date

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

  val crmEventProvider = new Inject(buildOne _) {}

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

trait CrmEventProvider {

  private val logger = Logger(classOf[CrmEventProvider])


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
