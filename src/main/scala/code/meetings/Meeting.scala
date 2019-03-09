package code.meetings

import java.util.Date

import com.openbankproject.commons.model.{BankId, User}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List


trait Meeting {
  def meetingId: String
  def providerId: String
  def purposeId: String
  def bankId: String
  def present: MeetingPresent
  def keys: MeetingKeys
  def when: Date
  def creator: ContactDetails
  def invitees: List[Invitee]
}
case class Invitee(
  contactDetails: ContactDetails,
  status: String
)

case class ContactMedium(
  `type`: String, 
  value: String
)
case class ContactDetails(
                         name: String,
                         phone: String,
                         email: String
                         )

case class MeetingKeys (
                         sessionId: String,
                         customerToken: String,
                         staffToken: String
  )

case class MeetingPresent(
                           staffUserId: String,
                           customerUserId: String
                           )


object Meeting extends SimpleInjector {

  val meetingProvider = new Inject(buildOne _) {}

  def buildOne: MeetingProvider = MappedMeetingProvider

}

trait MeetingProvider {
  def getMeetings(
    bankId : BankId, 
    userId: User
  ) : Box[List[Meeting]]
  
  def createMeeting(
    bankId: BankId,
    staffUser: User,
    customerUser: User,
    providerId: String,
    purposeId: String,
    when: Date,
    sessionId: String,
    customerToken: String,
    staffToken: String,
    creator: ContactDetails,
    invitees: List[Invitee]
  ): Box[Meeting]
  
  def getMeeting(
    bankId: BankId,
    userId: User, 
    meetingId : String
  ) : Box[Meeting]
}




