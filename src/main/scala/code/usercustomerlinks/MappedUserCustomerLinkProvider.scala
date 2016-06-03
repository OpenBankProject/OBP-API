package code.usercustomerlinks

import java.util.Date

import code.customer.Customer
import code.model.{User, BankId}
import code.util.{DefaultStringField}
import net.liftweb.common.Box
import net.liftweb.mapper._

/**
 * Created by markom on 5/30/16.
 */
object MappedUserCustomerLinkProvider extends UserCustomerLinkProvider {

  override def createUserCustomerLink(userId: String, customerId: String, bankId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] = {

    val createUserCustomerLink = MappedUserCustomerLink.create
      .mUserId(userId)
      .mCustomerId(customerId)
      .mBankId(bankId)
      .mDateInserted(new Date())
      .mIsActive(isActive)
      .saveMe()

    Some(createUserCustomerLink)
  }

  override def getUserCustomerLink(userId : String, customerId: String): Box[UserCustomerLink] = {
    MappedUserCustomerLink.find(
      By(MappedUserCustomerLink.mUserId, userId),
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }

  override def getUserCustomerLinks: Box[List[UserCustomerLink]] = {
    //MappedUserCustomerLink.bulkDelete_!!()
    Some(MappedUserCustomerLink.findAll())
  }

}

class MappedUserCustomerLink extends UserCustomerLink with LongKeyedMapper[MappedUserCustomerLink] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserCustomerLink

  // Name the objects m* so that we can give the overridden methods nice names.
  // Assume we'll have to override all fields so name them all m*
  object mCustomerId extends DefaultStringField(this)
  object mBankId extends DefaultStringField(this)
  object mUserId extends DefaultStringField(this)
  object mDateInserted extends MappedDateTime(this)
  object mIsActive extends MappedBoolean(this)

  override def customerId: String = mCustomerId.get // id.toString
  override def userId: String = mUserId.get
  override def bankId : String = mBankId.get
  override def dateInserted: Date = mDateInserted.get
  override def isActive: Boolean = mIsActive



}

object MappedUserCustomerLink extends MappedUserCustomerLink with LongKeyedMetaMapper[MappedUserCustomerLink] {
  override def dbIndexes = UniqueIndex(mUserId, mCustomerId) :: super.dbIndexes
}
