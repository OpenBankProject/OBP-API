package code.customer

import java.lang
import java.util.Date

import code.CustomerDependants.CustomerDependants
import code.api.util._
import code.api.util.migration.Migration.DbFunction
import code.usercustomerlinks.{DoobieUserCustomerLinkProvider, UserCustomerLink}
import code.users.Users
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf
import com.openbankproject.commons.model.{User, _}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future


object MappedCustomerProvider extends CustomerProvider with MdcLoggable {

  override def getCustomersAtAllBanks(queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] = Future {
    Full(MappedCustomer.findAll(bankId = None, customerTypes = None, getOptionalParams(queryParams)))
  }
  override def getCustomersFuture(bankId : BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] = Future {
    Full(MappedCustomer.findAll(Some(bankId.value), customerTypes = None, getOptionalParams(queryParams)))
  }

  /**
   * The paging, date range and ordering a customer listing carries.
   *
   * The date filters work on updatedAt but the ordering works on mLastOkDate, which is not a typo:
   * that is what the Mapper translation did, and the two are not the same column.
   */
  def getOptionalParams(queryParams: List[OBPQueryParam]): CustomerQuery =
    CustomerQuery(
      limit = queryParams.collect { case OBPLimit(value) => value }.headOption,
      offset = queryParams.collect { case OBPOffset(value) => value }.headOption,
      fromDate = queryParams.collect { case OBPFromDate(date) => date }.headOption,
      toDate = queryParams.collect { case OBPToDate(date) => date }.headOption,
      ascending = queryParams.collect {
        case OBPOrdering(_, OBPAscending) => true
        case OBPOrdering(_, OBPDescending) => false
      }.headOption)

  override def getCustomersByCustomerPhoneNumber(bankId: BankId, phoneNumber: String): Future[Box[List[Customer]]] = Future {
    Full(MappedCustomer.findAllByBankAndMobileNumberLike(bankId.value, phoneNumber))
  }
  override def getCustomersByCustomerLegalName(bankId: BankId, legalName: String): Future[Box[List[Customer]]] = Future {
    Full(MappedCustomer.findAllByBankAndLegalNameLike(bankId.value, legalName))
  }


  override def checkCustomerNumberAvailable(bankId : BankId, customerNumber : String) : Boolean = {
    val customers  = MappedCustomer.findAllByBankAndNumber(bankId.value, customerNumber)

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

  override def getCustomerByCustomerId(customerId: String): Box[Customer] =
    MappedCustomer.findByCustomerId(customerId)

  override def getCustomersByUserId(userId: String): List[Customer] = {
    val customerIds = DoobieUserCustomerLinkProvider.getUserCustomerLinksByUserId(userId).map(_.customerId)
    MappedCustomer.findAllByCustomerIds(customerIds)
  }

  def getCustomersByUserIdBoxed(userId: String): Box[List[Customer]] = {
    Full(getCustomersByUserId(userId))
  }

  override def getCustomersByUserIdFuture(userId: String): Future[Box[List[Customer]]]= {
    Future {
      Full(getCustomersByUserId(userId))
    }
  }

  override def getBankIdByCustomerId(customerId: String): Box[String] =
    for (c <- MappedCustomer.findByCustomerId(customerId)) yield {c.bankId}

  override def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId): Box[Customer] =
    MappedCustomer.findByBankAndNumber(bankId.value, customerNumber)

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
                           nameSuffix: String,
                           customerType: String = "",
                           parentCustomerId: String = ""
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
      val mappedCustomer = MappedCustomer.insert(
        bankIdValue = bankId.value,
        email = email,
        faceImageTime = faceImage.date,
        faceImageUrl = faceImage.url,
        legalName = legalName,
        mobileNumber = mobileNumber,
        number = number,
        dateOfBirth = dateOfBirth,
        relationshipStatus = relationshipStatus,
        dependents = dependents,
        highestEducationAttained = highestEducationAttained,
        employmentStatus = employmentStatus,
        kycStatus = kycStatus,
        lastOkDate = lastOkDate,
        creditRating = cr.rating,
        creditSource = cr.source,
        creditLimitCurrency = cl.currency,
        creditLimitAmount = cl.amount,
        title = title,
        branchId = branchId,
        nameSuffix = nameSuffix,
        customerType = customerType,
        parentCustomerId = parentCustomerId,
        isPendingAgent = true,
        isConfirmedAgent = false)

        // This is especially for OneToMany table, to save a List to database.
        CustomerDependants.CustomerDependants.vend
          .createCustomerDependants(mappedCustomer.customerPrimaryKey, dobOfDependents.map(CustomerDependant(_)))

        mappedCustomer
    }

  }

  override def updateCustomerScaData(customerId: String, mobileNumber: Option[String], email: Option[String], customerNumber: Option[String]): Future[Box[Customer]] = Future {
    MappedCustomer.findByCustomerId(customerId) map { c =>
      MappedCustomer.update(c.customerId, List(
        mobileNumber.map(value => fr"mmobilenumber = ${Option(value)}"),
        email.map(value => fr"memail = ${Option(value)}"),
        customerNumber.map(value => fr"mnumber = ${Option(value)}")
      ).flatten)
    }
  }
  override def updateCustomerCreditData(customerId: String,
                                        creditRating: Option[String],
                                        creditSource: Option[String],
                                        creditLimit: Option[AmountOfMoney]): Future[Box[Customer]] = Future {
    MappedCustomer.findByCustomerId(customerId) map { c =>
      MappedCustomer.update(c.customerId, List(
        creditRating.map(value => fr"mcreditrating = ${Option(value)}"),
        creditSource.map(value => fr"mcreditsource = ${Option(value)}"),
        creditLimit.map(limit => fr"mcreditlimitamount = ${Option(limit.amount)}"),
        creditLimit.map(limit => fr"mcreditlimitcurrency = ${Option(limit.currency)}")
      ).flatten)
    }
  }

  override def updateCustomerGeneralData(customerId: String,
                                         legalName: Option[String],
                                         faceImage: Option[CustomerFaceImageTrait],
                                         dateOfBirth: Option[Date],
                                         relationshipStatus: Option[String],
                                         dependents: Option[Int],
                                         highestEducationAttained: Option[String],
                                         employmentStatus: Option[String],
                                         title: Option[String],
                                         branchId: Option[String],
                                         nameSuffix: Option[String],
                                         customerType: Option[String] = None,
                                         parentCustomerId: Option[String] = None,
                                        ): Future[Box[Customer]] = Future {
    MappedCustomer.findByCustomerId(customerId) map { c =>
      MappedCustomer.update(c.customerId, List(
        legalName.map(value => fr"mlegalname = ${Option(value)}"),
        faceImage.map(value => fr"mfaceimageurl = ${Option(value.url)}"),
        faceImage.map(value => fr"mfaceimagetime = ${MappedCustomer.timestamp(value.date)}"),
        dateOfBirth.map(value => fr"mdateofbirth = ${MappedCustomer.timestamp(value)}"),
        relationshipStatus.map(value => fr"mrelationshipstatus = ${Option(value)}"),
        dependents.map(value => fr"mdependents = $value"),
        highestEducationAttained.map(value => fr"mhighesteducationattained = ${Option(value)}"),
        employmentStatus.map(value => fr"memploymentstatus = ${Option(value)}"),
        title.map(value => fr"mtitle = ${Option(value)}"),
        branchId.map(value => fr"mbranchid = ${Option(value)}"),
        nameSuffix.map(value => fr"mnamesuffix = ${Option(value)}"),
        customerType.map(value => fr"mcustomertype = ${Option(value)}"),
        parentCustomerId.map(value => fr"mparentcustomerid = ${Option(value)}")
      ).flatten)
    }
  }

  override def getCustomersByParentCustomerId(bankId: BankId, parentCustomerId: String): Future[Box[List[Customer]]] = Future {
    Full(MappedCustomer.findAllByBankAndParentCustomerId(bankId.value, parentCustomerId))
  }

  override def getCustomersByCustomerTypes(bankId: BankId, customerTypes: List[String], queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] = Future {
    Full(MappedCustomer.findAll(Some(bankId.value), Some(customerTypes), getOptionalParams(queryParams)))
  }

  override def bulkDeleteCustomers(): Boolean = {
    MappedCustomer.deleteAll()
    true
  }

  override def populateMissingUUIDs(): Boolean = {
    logger.warn("Executed script: " + NameOf.nameOf(populateMissingUUIDs()))
    //Back up MappedCustomer table.
    DbFunction.makeBackUpOfTableByName("mappedcustomer")

    for {
      customer <- MappedCustomer.findAllWithoutCustomerId()
    } yield {
      MappedCustomer.setCustomerId(customer.customerPrimaryKey, APIUtil.generateUUID())
    }
  }.forall(_ == true)

}

/** The paging, date range and ordering a customer listing carries. */
case class CustomerQuery(
  limit: Option[Int],
  offset: Option[Int],
  fromDate: Option[Date],
  toDate: Option[Date],
  ascending: Option[Boolean]
)

//in OBP, customer and agent share the same customer model. the CustomerAccountLink and AgentAccountLink also share the same model
/**
 * A customer, which is also an agent: the same row backs both, told apart by isPendingAgent and
 * isConfirmedAgent.
 *
 * `customerPrimaryKey` is the surrogate key and would normally stay inside the store, but the tax
 * residence, address and dependant rows are keyed by it rather than by the customer id, so it has
 * to be carried on the row for those to resolve.
 */
case class MappedCustomer(
  customerPrimaryKey: Long,
  customerId: String,
  bankId: String,
  number: String,
  mobileNumber: String,
  legalName: String,
  email: String,
  faceImageUrl: String,
  faceImageTime: Date,
  dateOfBirthValue: Date,
  relationshipStatus: String,
  dependentsValue: Int,
  highestEducationAttained: String,
  employmentStatus: String,
  creditRatingValue: String,
  creditSource: String,
  creditLimitCurrency: String,
  creditLimitAmount: String,
  kycStatusValue: Boolean,
  lastOkDate: Date,
  title: String,
  branchId: String,
  nameSuffix: String,
  customerTypeValue: String,
  parentCustomerIdValue: String,
  isPendingAgent: Boolean,
  isConfirmedAgent: Boolean
) extends Customer with Agent {

  override def faceImage: CustomerFaceImageTrait = new CustomerFaceImageTrait {
    override def date: Date = faceImageTime
    override def url: String = faceImageUrl
  }
  override def dateOfBirth: Date = dateOfBirthValue
  override def dependents: Integer = dependentsValue
  override def dobOfDependents: List[Date] =
    CustomerDependants.CustomerDependants.vend
    .getCustomerDependantsByCustomerPrimaryKey(customerPrimaryKey)
    .map(_.dateOfBirth)
  override def creditRating: CreditRatingTrait = new CreditRatingTrait {
    override def rating: String = creditRatingValue
    override def source: String = creditSource
  }
  override def creditLimit: AmountOfMoneyTrait = new AmountOfMoneyTrait {
    override def currency: String = creditLimitCurrency
    override def amount: String = creditLimitAmount
  }
  override def kycStatus: lang.Boolean = kycStatusValue
  override def customerType: Option[String] = Option(customerTypeValue)
  override def parentCustomerId: Option[String] = Option(parentCustomerIdValue)

  override def agentId: String = customerId //this is for Agent
}

object MappedCustomer {

  private val selectColumns =
    fr"""SELECT id, mcustomerid, mbank, mnumber, mmobilenumber, mlegalname, memail, mfaceimageurl,
                mfaceimagetime, mdateofbirth, mrelationshipstatus, mdependents,
                mhighesteducationattained, memploymentstatus, mcreditrating, mcreditsource,
                mcreditlimitcurrency, mcreditlimitamount, mkycstatus, mlastokdate, mtitle,
                mbranchid, mnamesuffix, mcustomertype, mparentcustomerid, mispendingagent,
                misconfirmedagent
         FROM mappedcustomer"""

  // 27 columns, past the 22-element tuple limit, so the row is read as three nested tuples.
  private type RowA = (Long, Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[java.sql.Timestamp])
  private type RowB = (Option[java.sql.Timestamp], Option[String], Option[Int], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String])
  private type RowC = (Option[Boolean], Option[java.sql.Timestamp], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[Boolean], Option[Boolean])
  private type Row = (RowA, RowB, RowC)

  /** A date read back as a plain java.util.Date, which is what MappedDateTime handed out. */
  private def readDate(value: Option[java.sql.Timestamp]): Date =
    value.map(t => new Date(t.getTime)).orNull

  private def fromRow(row: Row): MappedCustomer = row match {
    case ((id, customerId, bankId, number, mobileNumber, legalName, email, faceImageUrl,
           faceImageTime),
          (dateOfBirth, relationshipStatus, dependents, highestEducationAttained, employmentStatus,
           creditRating, creditSource, creditLimitCurrency, creditLimitAmount),
          (kycStatus, lastOkDate, title, branchId, nameSuffix, customerType, parentCustomerId,
           isPendingAgent, isConfirmedAgent)) =>
      MappedCustomer(id, customerId.orNull, bankId.orNull, number.orNull, mobileNumber.orNull,
        legalName.orNull, email.orNull, faceImageUrl.orNull, readDate(faceImageTime),
        readDate(dateOfBirth), relationshipStatus.orNull,
        // A NULL count, flag or date reads back as the field default, which is what Mapper did.
        dependents.getOrElse(0), highestEducationAttained.orNull, employmentStatus.orNull,
        creditRating.orNull, creditSource.orNull, creditLimitCurrency.orNull,
        creditLimitAmount.orNull, kycStatus.getOrElse(false), readDate(lastOkDate), title.orNull,
        branchId.orNull, nameSuffix.orNull, customerType.orNull, parentCustomerId.orNull,
        // MappedBoolean read a NULL as false for both, `defaultValue = true` on mIsPendingAgent
        // notwithstanding: that default only seeds a new instance.
        isPendingAgent.getOrElse(false), isConfirmedAgent.getOrElse(false))
  }

  private def query(condition: Fragment): List[MappedCustomer] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private[customer] def timestamp(value: Date): Option[java.sql.Timestamp] =
    Option(value).map(d => new java.sql.Timestamp(d.getTime))

  private def one(condition: Fragment): Box[MappedCustomer] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findByCustomerId(customerId: String): Box[MappedCustomer] =
    one(fr"WHERE mcustomerid = ${opt(customerId)}")

  /** By surrogate key, for the child tables that reference a customer that way. */
  def findByPrimaryKey(customerPrimaryKey: Long): Box[MappedCustomer] =
    one(fr"WHERE id = $customerPrimaryKey")

  def findByBankAndNumber(bankId: String, number: String): Box[MappedCustomer] =
    one(fr"WHERE mnumber = ${opt(number)} AND mbank = ${opt(bankId)}")

  def findAllByBankAndNumber(bankId: String, number: String): List[MappedCustomer] =
    query(fr"WHERE mbank = ${opt(bankId)} AND mnumber = ${opt(number)}")

  def findAllByBankAndMobileNumberLike(bankId: String, phoneNumber: String): List[MappedCustomer] =
    query(fr"WHERE mbank = ${opt(bankId)} AND mmobilenumber LIKE ${opt(phoneNumber)}")

  def findAllByBankAndLegalNameLike(bankId: String, legalName: String): List[MappedCustomer] =
    query(fr"WHERE mbank = ${opt(bankId)} AND mlegalname LIKE ${opt(legalName)}")

  def findAllByBankAndParentCustomerId(bankId: String, parentCustomerId: String): List[MappedCustomer] =
    query(fr"WHERE mbank = ${opt(bankId)} AND mparentcustomerid = ${opt(parentCustomerId)}")

  def findAllByCustomerIds(customerIds: List[String]): List[MappedCustomer] =
    // Mapper's ByList with an empty list rendered "0 = 1", i.e. no rows - not "no filter".
    if (customerIds.isEmpty) Nil
    else {
      val in = Fragments.in(fr"mcustomerid",
        cats.data.NonEmptyList.fromListUnsafe(customerIds.distinct))
      query(fr"WHERE " ++ in)
    }

  /** Rows whose customer id was never filled in - the ones populateMissingUUIDs exists to repair. */
  def findAllWithoutCustomerId(): List[MappedCustomer] =
    query(fr"WHERE mcustomerid IS NULL OR mcustomerid = ''")

  def findAll(bankId: Option[String], customerTypes: Option[List[String]],
              params: CustomerQuery): List[MappedCustomer] = {
    val filters = List(
      bankId.map(value => fr"mbank = ${opt(value)}"),
      customerTypes.map(types =>
        if (types.isEmpty) fr"0 = 1" // an empty ByList matched nothing rather than everything
        else Fragments.in(fr"mcustomertype", cats.data.NonEmptyList.fromListUnsafe(types.distinct))),
      params.fromDate.map(d => fr"updatedat >= ${new java.sql.Timestamp(d.getTime)}"),
      params.toDate.map(d => fr"updatedat <= ${new java.sql.Timestamp(d.getTime)}")
    ).flatten
    val where =
      if (filters.isEmpty) Fragment.empty
      else fr"WHERE " ++ filters.reduce((a, b) => a ++ fr"AND" ++ b)
    // The date filters work on updatedAt but the ordering works on mLastOkDate. Not a typo: that
    // is the translation Mapper did, and the two are different columns.
    val ordering = params.ascending match {
      case Some(true) => fr"ORDER BY mlastokdate ASC"
      case Some(false) => fr"ORDER BY mlastokdate DESC"
      case None => Fragment.empty
    }
    val paging =
      params.limit.map(value => fr"LIMIT $value").getOrElse(Fragment.empty) ++
        params.offset.map(value => fr"OFFSET $value").getOrElse(Fragment.empty)
    query(where ++ ordering ++ paging)
  }

  def insert(bankIdValue: String, email: String, faceImageTime: Date, faceImageUrl: String,
             legalName: String, mobileNumber: String, number: String, dateOfBirth: Date,
             relationshipStatus: String, dependents: Int, highestEducationAttained: String,
             employmentStatus: String, kycStatus: Boolean, lastOkDate: Date, creditRating: String,
             creditSource: String, creditLimitCurrency: String, creditLimitAmount: String,
             title: String, branchId: String, nameSuffix: String, customerType: String,
             parentCustomerId: String, isPendingAgent: Boolean,
             isConfirmedAgent: Boolean): MappedCustomer = {
    val customerId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedcustomer
            (mcustomerid, mbank, mnumber, mmobilenumber, mlegalname, memail, mfaceimageurl,
             mfaceimagetime, mdateofbirth, mrelationshipstatus, mdependents,
             mhighesteducationattained, memploymentstatus, mcreditrating, mcreditsource,
             mcreditlimitcurrency, mcreditlimitamount, mkycstatus, mlastokdate, mtitle, mbranchid,
             mnamesuffix, mcustomertype, mparentcustomerid, mispendingagent, misconfirmedagent,
             createdat, updatedat)
            VALUES ($customerId, ${opt(bankIdValue)}, ${opt(number)}, ${opt(mobileNumber)},
             ${opt(legalName)}, ${opt(email)}, ${opt(faceImageUrl)}, ${timestamp(faceImageTime)},
             ${timestamp(dateOfBirth)}, ${opt(relationshipStatus)}, $dependents,
             ${opt(highestEducationAttained)}, ${opt(employmentStatus)}, ${opt(creditRating)},
             ${opt(creditSource)}, ${opt(creditLimitCurrency)}, ${opt(creditLimitAmount)},
             $kycStatus, ${timestamp(lastOkDate)}, ${opt(title)}, ${opt(branchId)},
             ${opt(nameSuffix)}, ${opt(customerType)}, ${opt(parentCustomerId)}, $isPendingAgent,
             $isConfirmedAgent, $now, $now)"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    MappedCustomer(id, customerId, bankIdValue, number, mobileNumber, legalName, email,
      faceImageUrl, faceImageTime, dateOfBirth, relationshipStatus, dependents,
      highestEducationAttained, employmentStatus, creditRating, creditSource, creditLimitCurrency,
      creditLimitAmount, kycStatus, lastOkDate, title, branchId, nameSuffix, customerType,
      parentCustomerId, isPendingAgent, isConfirmedAgent)
  }

  /**
   * Applies the supplied column assignments and returns the row as it now stands.
   *
   * An empty list means the caller asked for no change: Mapper still called saveMe in that case,
   * which restamped updatedAt, so the row is re-read rather than skipped.
   */
  def update(customerId: String, sets: List[Fragment]): MappedCustomer = {
    val stamp = fr"updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}"
    val assignments = (sets :+ stamp).reduce((a, b) => a ++ fr"," ++ b)
    DoobieUtil.runUpdate(
      (fr"UPDATE mappedcustomer SET" ++ assignments ++
        fr"WHERE mcustomerid = ${opt(customerId)}").update.run)
    findByCustomerId(customerId)
      .openOrThrowException("the customer just updated must be readable")
  }

  def setCustomerId(customerPrimaryKey: Long, customerId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedcustomer SET mcustomerid = ${opt(customerId)},
              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
            WHERE id = $customerPrimaryKey"""
        .update.run) > 0

  def setAgentStatus(customerId: String, isPendingAgent: Boolean,
                     isConfirmedAgent: Boolean): MappedCustomer =
    update(customerId, List(fr"mispendingagent = $isPendingAgent",
      fr"misconfirmedagent = $isConfirmedAgent"))

  def deleteByCustomerId(customerId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedcustomer WHERE mcustomerid = ${opt(customerId)}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomer".update.run)
    ()
  }
}
