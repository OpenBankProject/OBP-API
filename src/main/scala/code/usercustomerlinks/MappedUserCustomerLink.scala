package code.usercustomerlinks

import java.util.Date
import code.util.{DefaultStringField, MappedUUID}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._

/**
 * Created by markom on 5/30/16.
 */

class MappedUserCustomerLink extends UserCustomerLink with LongKeyedMapper[MappedUserCustomerLink] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserCustomerLink

  // Name the objects m* so that we can give the overridden methods nice names.
  // Assume we'll have to override all fields so name them all m*
  object mUserCustomerLinkId extends MappedUUID(this)
  object mCustomerId extends DefaultStringField(this)
  object mUserId extends DefaultStringField(this)
  object mDateInserted extends MappedDateTime(this)
  object mIsActive extends MappedBoolean(this)

  override def userCustomerLinkId: String = mUserCustomerLinkId.get
  override def customerId: String = mCustomerId.get // id.toString
  override def userId: String = mUserId.get
  override def dateInserted: Date = mDateInserted.get
  override def isActive: Boolean = mIsActive

  override def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] = {

    val createUserCustomerLink = MappedUserCustomerLink.create
      .mUserId(userId)
      .mCustomerId(customerId)
      .mDateInserted(new Date())
      .mIsActive(isActive)
      .saveMe()

    Some(createUserCustomerLink)
  }

  override def getUserCustomerLink(customerId: String): Box[UserCustomerLink] = {
    MappedUserCustomerLink.find(
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }

  override def getUserCustomerLinkByUserId(userId: String): List[UserCustomerLink] = {
    MappedUserCustomerLink.findAll(
      By(MappedUserCustomerLink.mUserId, userId))
  }

  override def getUserCustomerLink(userId : String, customerId: String): Box[UserCustomerLink] = {
    MappedUserCustomerLink.find(
      By(MappedUserCustomerLink.mUserId, userId),
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }

  override def getUserCustomerLinks: Box[List[UserCustomerLink]] = {
    Full(MappedUserCustomerLink.findAll())
  }

  override def getUserCustomerLinksByUserId(userId : String): Box[List[UserCustomerLink]] = {
    Full(MappedUserCustomerLink.findAll(By(MappedUserCustomerLink.mUserId, userId)))
  }

}

object MappedUserCustomerLink extends MappedUserCustomerLink with LongKeyedMetaMapper[MappedUserCustomerLink] {
  override def dbIndexes = UniqueIndex(mUserCustomerLinkId) :: UniqueIndex(mUserId, mCustomerId) :: super.dbIndexes

}
