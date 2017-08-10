package code.usercustomerlinks

import java.util.Date
import code.util.{UUIDString, MappedUUID}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._

/**
 * Created by markom on 5/30/16.
 */

object MappedUserCustomerLinkProvider extends UserCustomerLinkProvider {
  override def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] = {

    val createUserCustomerLink = MappedUserCustomerLink.create
      .mUserId(userId)
      .mCustomerId(customerId)
      .mDateInserted(new Date())
      .mIsActive(isActive)
      .saveMe()

    Some(createUserCustomerLink)
  }

  override def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink] = {
    MappedUserCustomerLink.find(
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }

  override def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink] = {
    val userCustomerLinks : List[UserCustomerLink] = MappedUserCustomerLink.findAll(
      By(MappedUserCustomerLink.mUserId, userId)).sortWith(_.id.get < _.id.get)
    userCustomerLinks
  }

  override def getUserCustomerLink(userId : String, customerId: String): Box[UserCustomerLink] = {
    MappedUserCustomerLink.find(
      By(MappedUserCustomerLink.mUserId, userId),
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }

  override def getUserCustomerLinks: Box[List[UserCustomerLink]] = {
    Full(MappedUserCustomerLink.findAll())
  }

  override def bulkDeleteUserCustomerLinks(): Boolean = {
    MappedUserCustomerLink.bulkDelete_!!()
  }
}

class MappedUserCustomerLink extends UserCustomerLink with LongKeyedMapper[MappedUserCustomerLink] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserCustomerLink

  // Name the objects m* so that we can give the overridden methods nice names.
  // Assume we'll have to override all fields so name them all m*
  object mUserCustomerLinkId extends MappedUUID(this)
  object mCustomerId extends UUIDString(this)
  object mUserId extends UUIDString(this)
  object mDateInserted extends MappedDateTime(this)
  object mIsActive extends MappedBoolean(this)

  override def userCustomerLinkId: String = mUserCustomerLinkId.get
  override def customerId: String = mCustomerId.get // id.toString
  override def userId: String = mUserId.get
  override def dateInserted: Date = mDateInserted.get
  override def isActive: Boolean = mIsActive.get
}

object MappedUserCustomerLink extends MappedUserCustomerLink with LongKeyedMetaMapper[MappedUserCustomerLink] {
  override def dbIndexes = UniqueIndex(mUserCustomerLinkId) :: UniqueIndex(mUserId, mCustomerId) :: super.dbIndexes

}
