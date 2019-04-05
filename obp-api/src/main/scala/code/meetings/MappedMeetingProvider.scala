package code.meetings

import java.util.Date

import code.api.util.ErrorMessages
import code.model.dataAccess.ResourceUser
import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.{BankId, ContactDetails, Invitee, Meeting, MeetingKeys, MeetingPresent, User}
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List

object MappedMeetingProvider extends MeetingProvider {


  override def getMeeting(bankId : BankId, user: User, meetingId : String): Box[Meeting] = {
    // Return a Box so we can handle errors later.
    MappedMeeting.find(
      // TODO Need to check permissions (user)
      By(MappedMeeting.mBankId, bankId.toString),
      By(MappedMeeting.mMeetingId, meetingId)
      , OrderBy(MappedMeeting.mWhen, Descending))
  }


  override def getMeetings(bankId : BankId, user: User): Box[List[Meeting]] = {
    // Return a Box so we can handle errors later.
   tryo{MappedMeeting.findAll(By(
//      TODO Need to check permissions (user)
     MappedMeeting.mBankId, bankId.toString),
     OrderBy(MappedMeeting.mWhen, Descending))}
  }



  override def createMeeting(
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
    invitees: List[Invitee],
  ): Box[Meeting] =
  {
   for{
     createdMeeting <- tryo {MappedMeeting.create
       .mBankId(bankId.value.toString)
      //.mStaffUserId(staffUser.apiId.value)
      .mCustomerUserId(customerUser.userPrimaryKey.value)
      .mProviderId(providerId)
      .mPurposeId(purposeId)
      .mWhen(when)
      .mSessionId(sessionId)
      .mCustomerToken(customerToken)
      .mStaffToken(staffToken)
      .mCreatorName(creator.name) 
      .mCreatorPhone(creator.phone)
      .mCreatorEmail(creator.email)
      .saveMe()} ?~! ErrorMessages.CreateMeetingException
     
     _ <- tryo {for(invitee <- invitees) {
      val meetingInvitee = MappedMeetingInvitee.create
        .mMappedMeeting(createdMeeting)
        .mName(invitee.contactDetails.name)
        .mPhone(invitee.contactDetails.phone) 
        .mEmail(invitee.contactDetails.email) 
        .mStatus(invitee.status)
        .saveMe()
      createdMeeting.mInvitees += meetingInvitee
      createdMeeting.save()
    }} ?~! ErrorMessages.CreateMeetingInviteeException
   } yield {
     createdMeeting
   }
  }

}





class MappedMeeting extends Meeting with LongKeyedMapper[MappedMeeting] with IdPK with CreatedUpdated with OneToMany[Long, MappedMeeting]{

  def getSingleton = MappedMeeting

  // Name the objects m* so that we can give the overriden methods nice names.
  // Assume we'll have to override all fields so name them all m*

  object mMeetingId extends MappedUUID(this)

  // With
  object mBankId extends UUIDString(this)
  object mCustomerUserId extends MappedLongForeignKey(this, ResourceUser)
  object mStaffUserId extends MappedLongForeignKey(this, ResourceUser)

  // What
  object mProviderId extends MappedString(this, 64)
  object mPurposeId extends MappedString(this, 64)

  // Keys to the "meeting room"
  object mSessionId extends MappedString(this, 255)
  object mCustomerToken extends MappedString(this, 255)
  object mStaffToken extends MappedString(this, 255)

  object mWhen extends MappedDateTime(this)
  //Creator
  object mCreatorName extends MappedString(this, 255)
  object mCreatorPhone extends MappedString(this, 32)
  object mCreatorEmail extends MappedEmail(this, 100)

  //Invitees
  object mInvitees extends MappedOneToMany(MappedMeetingInvitee, MappedMeetingInvitee.mMappedMeeting, OrderBy(MappedMeetingInvitee.id, Ascending))
  
  override def meetingId: String = mMeetingId.get.toString

  override def when: Date = mWhen.get

  override def providerId : String = mProviderId.get
  override def purposeId : String = mPurposeId.get
  override def bankId : String = mBankId.get.toString

  override def keys = MeetingKeys(mSessionId.get, mCustomerToken.get, mStaffToken.get)
  override def present = MeetingPresent(staffUserId = mStaffUserId.foreign.map(_.userId).getOrElse(""),
                                        customerUserId = mCustomerUserId.foreign.map(_.userId).getOrElse(""))

  override def creator = ContactDetails(mCreatorName.get,mCreatorPhone.get,mCreatorEmail.get)
  override def invitees = mInvitees.map(invitee => Invitee(ContactDetails(invitee.mName.get, invitee.mPhone.get, invitee.mEmail.get),invitee.mStatus.get)).toList
  
}

object MappedMeeting extends MappedMeeting with LongKeyedMetaMapper[MappedMeeting] {
  //one Meeting info per bank for each api user
  override def dbIndexes = UniqueIndex(mMeetingId) :: super.dbIndexes
}

class MappedMeetingInvitee extends LongKeyedMapper[MappedMeetingInvitee] with IdPK {
  def getSingleton = MappedMeetingInvitee

  object mMappedMeeting extends MappedLongForeignKey(this, MappedMeeting)
  object mName extends MappedString(this, 255)
  object mPhone extends MappedString(this, 255)
  object mEmail extends MappedEmail(this, 100)
  object mStatus extends MappedString(this, 255)
}
object MappedMeetingInvitee extends MappedMeetingInvitee with LongKeyedMetaMapper[MappedMeetingInvitee]{}