package code.customer

import java.util.Date

import code.model.{BankId, User}
import code.usercustomerlinks.{MappedUserCustomerLink, MappedUserCustomerLinkProvider, UserCustomerLink}
import code.users.Users
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper.{By, _}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


object MappedCustomerProvider extends CustomerProvider {


  override def checkCustomerNumberAvailable(bankId : BankId, customerNumber : String) : Boolean = {
    val customers  = MappedCustomer.findAll(
      By(MappedCustomer.mBank, bankId.value),
      By(MappedCustomer.mNumber, customerNumber)
    )

    val available: Boolean = customers.size match {
      case 0 => true
      case _ => false
    }

    available
  }

  // TODO Rename
  override def getCustomerByUserId(bankId: BankId, userId: String): Box[Customer] = {
    // If there are more than customer linked to a user we take a first one in a list
    val customerId = UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(userId) match {
      case x :: xs => x.customerId
      case _       => "There is no linked customer to this user"
    }
    getCustomerByCustomerId(customerId)
  }

  override def getCustomerByCustomerId(customerId: String): Box[Customer] = {
    MappedCustomer.find(
      By(MappedCustomer.mCustomerId, customerId)
    )
  }

  override def getCustomersByUserId(userId: String): List[Customer] = {
    val customerIds = MappedUserCustomerLinkProvider.getUserCustomerLinksByUserId(userId).map(_.customerId)
    MappedCustomer.findAll(ByList(MappedCustomer.mCustomerId, customerIds))
  }

  override def getCustomersByUserIdFuture(userId: String): Future[Box[List[Customer]]]= {
    Future{
      Full(getCustomersByUserId(userId))
    }
  }

  override def getBankIdByCustomerId(customerId: String): Box[String] = {
    val customer: Box[MappedCustomer] = MappedCustomer.find(
      By(MappedCustomer.mCustomerId, customerId)
    )
    for (c <- customer) yield {c.mBank.get}
  }

  override def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId): Box[Customer] = {
    MappedCustomer.find(
      By(MappedCustomer.mNumber, customerNumber),
      By(MappedCustomer.mBank, bankId.value)
    )
  }

  override def getUser(bankId: BankId, customerNumber: String): Box[User] = {
    getCustomerByCustomerNumber(customerNumber, bankId).flatMap{
      x => UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByCustomerId(x.customerId)
    }.flatMap{
      y => Users.users.vend.getUserByUserId(y.userId)
    }
  }

  override def addCustomer(bankId: BankId,
                           number : String,
                           legalName : String,
                           mobileNumber : String,
                           email : String,
                           faceImage: CustomerFaceImage,
                           dateOfBirth: Date,
                           relationshipStatus: String,
                           dependents: Int,
                           dobOfDependents: List[Date],
                           highestEducationAttained: String,
                           employmentStatus: String,
                           kycStatus: Boolean,
                           lastOkDate: Date,
                           creditRating: Option[CreditRating],
                           creditLimit: Option[AmountOfMoney]
                          ) : Box[Customer] = {

    val cr = creditRating match {
      case Some(c) => MockCreditRating(rating = c.rating, source = c.source)
      case _       => MockCreditRating(rating = "", source = "")
    }

    val cl = creditLimit match {
      case Some(c) => MockCreditLimit(currency = c.currency, amount = c.amount)
      case _       => MockCreditLimit(currency = "", amount = "")
    }

    val createdCustomer = MappedCustomer.create
      .mBank(bankId.value)
      .mEmail(email)
      .mFaceImageTime(faceImage.date)
      .mFaceImageUrl(faceImage.url)
      .mLegalName(legalName)
      .mMobileNumber(mobileNumber)
      .mNumber(number)
      //.mUser(user.resourceUserId.value)
      .mDateOfBirth(dateOfBirth)
      .mRelationshipStatus(relationshipStatus)
      .mDependents(dependents)
      .mHighestEducationAttained(highestEducationAttained)
      .mEmploymentStatus(employmentStatus)
      .mKycStatus(kycStatus)
      .mLastOkDate(lastOkDate)
      .mCreditRating(cr.rating)
      .mCreditSource(cr.source)
      .mCreditLimitCurrency(cl.currency)
      .mCreditLimitAmount(cl.amount)
      .saveMe()

    Full(createdCustomer)
  }

  override def bulkDeleteCustomers(): Boolean = {
    MappedCustomer.bulkDelete_!!()
  }

}

class MappedCustomer extends Customer with LongKeyedMapper[MappedCustomer] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomer

  // Unique
  object mCustomerId extends MappedUUID(this)

  // Combination of bank id and customer number is unique
  object mBank extends UUIDString(this)
  object mNumber extends MappedString(this, 50)

  object mMobileNumber extends MappedString(this, 50)
  object mLegalName extends MappedString(this, 255)
  object mEmail extends MappedEmail(this, 200)
  object mFaceImageUrl extends MappedString(this, 2000)
  object mFaceImageTime extends MappedDateTime(this)
  object mDateOfBirth extends MappedDateTime(this)
  object mRelationshipStatus extends MappedString(this, 16)
  object mDependents extends MappedInt(this)
  object mHighestEducationAttained  extends MappedString(this, 32)
  object mEmploymentStatus extends MappedString(this, 32)
  object mCreditRating extends MappedString(this, 100)
  object mCreditSource extends MappedString(this, 100)
  object mCreditLimitCurrency extends MappedString(this, 100)
  object mCreditLimitAmount extends MappedString(this, 100)
  object mKycStatus extends MappedBoolean(this)
  object mLastOkDate extends MappedDateTime(this)

  override def customerId: String = mCustomerId.get // id.toString
  override def bankId: String = mBank.get
  override def number: String = mNumber.get
  override def mobileNumber: String = mMobileNumber.get
  override def legalName: String = mLegalName.get
  override def email: String = mEmail.get
  override def faceImage: CustomerFaceImage = new CustomerFaceImage {
    override def date: Date = mFaceImageTime.get
    override def url: String = mFaceImageUrl.get
  }
  override def dateOfBirth: Date = mDateOfBirth.get
  override def relationshipStatus: String = mRelationshipStatus.get
  override def dependents: Int = mDependents.get
  override def dobOfDependents: List[Date] = List(createdAt.get)
  override def highestEducationAttained: String = mHighestEducationAttained.get
  override def employmentStatus: String = mEmploymentStatus.get
  override def creditRating: CreditRating = new CreditRating {
    override def rating: String = mCreditRating.get
    override def source: String = mCreditSource.get
  }
  override def creditLimit: AmountOfMoney = new AmountOfMoney {
    override def currency: String = mCreditLimitCurrency.get
    override def amount: String = mCreditLimitAmount.get
  }
  override def kycStatus: Boolean = mKycStatus.get
  override def lastOkDate: Date = mLastOkDate.get
}

object MappedCustomer extends MappedCustomer with LongKeyedMetaMapper[MappedCustomer] {
  //one customer info per bank for each api user
  override def dbIndexes = UniqueIndex(mCustomerId) :: UniqueIndex(mBank, mNumber) :: super.dbIndexes
}