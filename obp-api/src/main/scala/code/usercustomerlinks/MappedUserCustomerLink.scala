package code.usercustomerlinks

import java.util.Date

import code.api.util.ErrorMessages
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

object MappedUserCustomerLinkProvider extends UserCustomerLinkProvider {
  def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] = {

    val createUserCustomerLink = MappedUserCustomerLink.create
      .mUserId(userId)
      .mCustomerId(customerId)
      .mDateInserted(new Date())
      .mIsActive(isActive)
      .saveMe()

    Some(createUserCustomerLink)
  }
  def getOCreateUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] = {
    getUserCustomerLink(userId, customerId) match {
      case Empty =>
        val createUserCustomerLink = MappedUserCustomerLink.create
          .mUserId(userId)
          .mCustomerId(customerId)
          .mDateInserted(new Date())
          .mIsActive(isActive)
          .saveMe()
        Some(createUserCustomerLink)
      case everythingElse => everythingElse
    }
  }

  def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink] = {
    MappedUserCustomerLink.find(
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }
  def getUserCustomerLinksByCustomerId(customerId: String): List[UserCustomerLink] = {
    MappedUserCustomerLink.findAll(
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }

  def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink] = {
    val userCustomerLinks : List[UserCustomerLink] = MappedUserCustomerLink.findAll(
      By(MappedUserCustomerLink.mUserId, userId)).sortWith(_.id.get < _.id.get)
    userCustomerLinks
  }

  def getUserCustomerLink(userId : String, customerId: String): Box[UserCustomerLink] = {
    MappedUserCustomerLink.find(
      By(MappedUserCustomerLink.mUserId, userId),
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }

  def getUserCustomerLinks: Box[List[UserCustomerLink]] = {
    Full(MappedUserCustomerLink.findAll())
  }

  def bulkDeleteUserCustomerLinks(): Boolean = {
    MappedUserCustomerLink.bulkDelete_!!()
  }

  def deleteUserCustomerLink(userCustomerLinkId: String): Future[Box[Boolean]] = {
    Future {
      MappedUserCustomerLink.find(By(MappedUserCustomerLink.mUserCustomerLinkId, userCustomerLinkId)) match {
        case Full(t) => Full(t.delete_!)
        case Empty => Empty ?~! ErrorMessages.UserCustomerLinkNotFound
        case Failure(msg, exception, chain) => Failure(msg, exception, chain)
      }
    }
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
