package code.meetings

import java.util.Date

import com.openbankproject.commons.model.{BankId, ContactDetails, Invitee, Meeting, User}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

case class ContactMedium(
  `type`: String, 
  value: String
)


object Meetings extends SimpleInjector {

  val meetingProvider = new Inject(buildOne _) {}

  def buildOne: MeetingProvider = MappedMeetingProvider

}

trait MeetingProvider {
  def getMeetings(
    bankId : BankId, 
    user: User
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
    user: User, 
    meetingId : String
  ) : Box[Meeting]
}




