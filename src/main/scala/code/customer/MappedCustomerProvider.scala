package code.customer

import java.lang
import java.util.Date

import code.api.util.APIUtil
import code.bankconnectors._
import code.model.{BankId, User}
import code.usercustomerlinks.{MappedUserCustomerLink, MappedUserCustomerLinkProvider, UserCustomerLink}
import code.users.Users
import code.util.Helper.MdcLoggable
import code.util.{MappedUUID, UUIDString}
import com.github.dwickern.macros.NameOf
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper.{By, _}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


object MappedCustomerProvider extends CustomerProvider with MdcLoggable {

  override def getCustomersFuture(bankId : BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] = Future {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedCustomer](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedCustomer](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedCustomer.mLastOkDate, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedCustomer.mLastOkDate, date) }.headOption
    val ordering = queryParams.collect {
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(MappedCustomer.mLastOkDate, Ascending)
          case OBPDescending => OrderBy(MappedCustomer.mLastOkDate, Descending)
        }
    }
    val optionalParams : Seq[QueryParam[MappedCustomer]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering).flatten
    val mapperParams = Seq(By(MappedCustomer.mBank, bankId.value)) ++ optionalParams
    Full(MappedCustomer.findAll(mapperParams:_*))
  }


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

  override def getCustomerByCustomerIdFuture(customerId: String): Future[Box[Customer]]= {
    Future {
      getCustomerByCustomerId(customerId)
    }
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

  def getCustomersByUserIdBoxed(userId: String): Box[List[Customer]] = {
    Full(getCustomersByUserId(userId))
  }

  override def getCustomersByUserIdFuture(userId: String): Future[Box[List[Customer]]]= {
    Future {
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
  override def getCustomerByCustomerNumberFuture(customerNumber: String, bankId : BankId): Future[Box[Customer]] = {
    Future(getCustomerByCustomerNumber(customerNumber, bankId))
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
                           faceImage: CustomerFaceImageTrait,
                           dateOfBirth: Date,
                           relationshipStatus: String,
                           dependents: Int,
                           dobOfDependents: List[Date],
                           highestEducationAttained: String,
                           employmentStatus: String,
                           kycStatus: Boolean,
                           lastOkDate: Date,
                           creditRating: Option[CreditRatingTrait],
                           creditLimit: Option[AmountOfMoneyTrait],
                           title: String,     
                           branchId: String,  
                           nameSuffix: String 
                          ) : Box[Customer] = {

    val cr = creditRating match {
      case Some(c) => CreditRating(rating = c.rating, source = c.source)
      case _       => CreditRating(rating = "", source = "")
    }

    val cl = creditLimit match {
      case Some(c) => CreditLimit(currency = c.currency, amount = c.amount)
      case _       => CreditLimit(currency = "", amount = "")
    }
       
    tryo { 
      MappedCustomer
        .create
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
        .mTitle(title)
        .mBranchId(branchId)
        .mNameSuffix(nameSuffix)
        .saveMe()
        } 

  }

  override def bulkDeleteCustomers(): Boolean = {
    MappedCustomer.bulkDelete_!!()
  }

  override def populateMissingUUIDs(): Boolean = {
    logger.warn("Executed script: " + NameOf.nameOf(populateMissingUUIDs))
    for {
      customer <- MappedCustomer.findAll(NullRef(MappedCustomer.mCustomerId))
    } yield {
      customer.mTitle(APIUtil.generateUUID()).save()
    }
  }.forall(_ == true)

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
  object mTitle extends MappedString(this, 255)
  object mBranchId extends MappedString(this, 255)
  object mNameSuffix extends MappedString(this, 255)

  override def customerId: String = mCustomerId.get // id.toString
  override def bankId: String = mBank.get
  override def number: String = mNumber.get
  override def mobileNumber: String = mMobileNumber.get
  override def legalName: String = mLegalName.get
  override def email: String = mEmail.get
  override def faceImage: CustomerFaceImageTrait = new CustomerFaceImageTrait {
    override def date: Date = mFaceImageTime.get
    override def url: String = mFaceImageUrl.get
  }
  override def dateOfBirth: Date = mDateOfBirth.get
  override def relationshipStatus: String = mRelationshipStatus.get
  override def dependents: Integer = mDependents.get
  override def dobOfDependents: List[Date] = List(createdAt.get)
  override def highestEducationAttained: String = mHighestEducationAttained.get
  override def employmentStatus: String = mEmploymentStatus.get
  override def creditRating: CreditRatingTrait = new CreditRatingTrait {
    override def rating: String = mCreditRating.get
    override def source: String = mCreditSource.get
  }
  override def creditLimit: AmountOfMoneyTrait = new AmountOfMoneyTrait {
    override def currency: String = mCreditLimitCurrency.get
    override def amount: String = mCreditLimitAmount.get
  }
  override def kycStatus: lang.Boolean = mKycStatus.get
  override def lastOkDate: Date = mLastOkDate.get
  
  override def title: String = mTitle.get
  override def branchId: String = mBranchId.get
  override def nameSuffix: String = mNameSuffix.get
}

object MappedCustomer extends MappedCustomer with LongKeyedMetaMapper[MappedCustomer] {
  //one customer info per bank for each api user
  override def dbIndexes = UniqueIndex(mCustomerId) :: UniqueIndex(mBank, mNumber) :: super.dbIndexes
}