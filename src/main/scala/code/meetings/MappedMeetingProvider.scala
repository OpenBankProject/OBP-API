package code.meetings

import java.util.Date

import code.model.{BankId, User}
import code.model.dataAccess.ResourceUser
import code.util.{MappedUUID, DefaultStringField}
import net.liftweb.common.Box
import net.liftweb.mapper._


object MappedMeetingProvider extends MeetingProvider {


  override def getMeeting(bankId : BankId, userId: User, meetingId : String): Box[Meeting] = {
    // Return a Box so we can handle errors later.
    MappedMeeting.find(
      // TODO Need to check permissions (user)
      By(MappedMeeting.mBankId, bankId.toString),
      By(MappedMeeting.mMeetingId, meetingId)
      , OrderBy(MappedMeeting.mWhen, Descending))
  }


  override def getMeetings(bankId : BankId, userId: User): Box[List[Meeting]] = {
    // Return a Box so we can handle errors later.
   Some(MappedMeeting.findAll(By(
     // TODO Need to check permissions (user)
     MappedMeeting.mBankId, bankId.toString),
     OrderBy(MappedMeeting.mWhen, Descending)))
  }



  override def createMeeting(bankId: BankId, staffUser: User, customerUser : User, providerId : String, purposeId : String, when: Date, sessionId: String, customerToken: String, staffToken: String) : Box[Meeting] = {

    val createdMeeting = MappedMeeting.create
      .mBankId(bankId.value.toString)
      //.mStaffUserId(staffUser.apiId.value)
      .mCustomerUserId(customerUser.apiId.value)
      .mProviderId(providerId)
      .mPurposeId(purposeId)
      .mWhen(when)
      .mSessionId(sessionId)
      .mCustomerToken(customerToken)
      .mStaffToken(staffToken)
      .saveMe()

    Some(createdMeeting)
  }

}





class MappedMeeting extends Meeting with LongKeyedMapper[MappedMeeting] with IdPK with CreatedUpdated {

  def getSingleton = MappedMeeting

  // Name the objects m* so that we can give the overriden methods nice names.
  // Assume we'll have to override all fields so name them all m*

  object mMeetingId extends MappedUUID(this)

  // With
  object mBankId extends DefaultStringField(this)
  object mCustomerUserId extends MappedLongForeignKey(this, ResourceUser)
  object mStaffUserId extends MappedLongForeignKey(this, ResourceUser)

  // What
  object mProviderId extends DefaultStringField(this)
  object mPurposeId extends DefaultStringField(this)

  // Keys to the "meeting room"
  object mSessionId extends DefaultStringField(this)
  object mCustomerToken extends DefaultStringField(this)
  object mStaffToken extends DefaultStringField(this)

  object mWhen extends MappedDateTime(this)

  override def meetingId: String = mMeetingId.get.toString

  override def when: Date = mWhen.get

  override def providerId : String = mProviderId.get
  override def purposeId : String = mPurposeId.get
  override def bankId : String = mBankId.get.toString

  override def keys = MeetingKeys(mSessionId.get, mCustomerToken.get, mStaffToken.get)
  override def present = MeetingPresent(staffUserId = mStaffUserId.get.toString,
                                        customerUserId = mCustomerUserId.get.toString)


}

object MappedMeeting extends MappedMeeting with LongKeyedMetaMapper[MappedMeeting] {
  //one Meeting info per bank for each api user
  override def dbIndexes = UniqueIndex(mMeetingId) :: super.dbIndexes
}