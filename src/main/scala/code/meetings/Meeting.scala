package code.meetings

import java.util.Date

import code.model.{User, BankId}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


trait Meeting {
  def meetingId: String
  def providerId: String
  def purposeId: String
  def bankId: String
  def present: MeetingPresent
  def keys: MeetingKeys
  def when: Date
}


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
  def getMeetings(bankId : BankId, userId: User) : Box[List[Meeting]]
  def createMeeting(bankId: BankId, staffUser: User, customerUser : User, providerId : String, purposeId : String, when: Date, sessionId: String, customerToken: String, staffToken: String): Box[Meeting]
  def getMeeting(bankId : BankId, userId: User, meetingId : String) : Box[Meeting]
}




