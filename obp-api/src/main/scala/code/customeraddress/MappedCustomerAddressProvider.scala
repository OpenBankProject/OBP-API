package code.customeraddress

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.CustomerAddress
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/**
 * A postal address held for a customer.
 *
 * `status` returns the STATE column, not the status column. That is a pre-existing defect in the
 * entity's accessor, not a transcription slip here: the endpoints above it have always reported
 * state in the status field, and the mstatus column has always been written but never read.
 * Correcting it would change what every existing caller sees, so it is preserved and stated.
 */
case class MappedCustomerAddress(
  customerId: String,
  customerAddressId: String,
  line1: String,
  line2: String,
  line3: String,
  city: String,
  county: String,
  state: String,
  postcode: String,
  countryCode: String,
  tags: String,
  insertDate: Date
) extends CustomerAddress {
  override def status: String = state
}

object MappedCustomerAddress {

  // mcustomerid holds MAPPEDCUSTOMER's numeric key, so the public customer id comes from the join.
  private val selectColumns =
    fr"""SELECT COALESCE(c.mcustomerid, ''), a.mcustomeraddressid, a.mline1, a.mline2, a.mline3,
                a.mcity, a.mcounty, a.mstate, a.mpostcode, a.mcountrycode, a.mtags, a.createdat
         FROM mappedcustomeraddress a
         LEFT JOIN mappedcustomer c ON c.id = a.mcustomerid"""

  private type Row = (String, String, String, String, String, String, String, String, String,
    String, String, java.sql.Timestamp)

  private def fromRow(row: Row): MappedCustomerAddress = row match {
    case (customerId, customerAddressId, line1, line2, line3, city, county, state, postcode,
          countryCode, tags, createdAt) =>
      MappedCustomerAddress(customerId, customerAddressId, line1, line2, line3, city, county,
        state, postcode, countryCode, tags, createdAt)
  }

  private def query(condition: Fragment): List[MappedCustomerAddress] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /** The numeric MAPPEDCUSTOMER key for a public customer id, or None when the customer is absent. */
  private def customerKey(customerId: String): Option[Long] =
    DoobieUtil.runQuery(
      sql"SELECT id FROM mappedcustomer WHERE mcustomerid = $customerId ORDER BY id ASC LIMIT 1"
        .query[Long].option)

  def findAllByCustomerId(customerId: String): Option[List[MappedCustomerAddress]] =
    customerKey(customerId).map(key => query(fr"WHERE a.mcustomerid = $key ORDER BY a.id ASC"))

  def findById(customerAddressId: String): Box[MappedCustomerAddress] =
    query(fr"WHERE a.mcustomeraddressid = $customerAddressId ORDER BY a.id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def insert(customerKey: Long, line1: String, line2: String, line3: String, city: String,
             county: String, state: String, postcode: String, countryCode: String, tags: String,
             status: String): MappedCustomerAddress = {
    val customerAddressId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedcustomeraddress
            (mcustomeraddressid, mcustomerid, mline1, mline2, mline3, mcity, mcounty, mstate,
             mpostcode, mcountrycode, mtags, mstatus, createdat, updatedat)
            VALUES ($customerAddressId, $customerKey, $line1, $line2, $line3, $city, $county,
             $state, $postcode, $countryCode, $tags, $status, $now, $now)"""
        .update.run)
    findById(customerAddressId)
      .openOrThrowException("the customer address just inserted must be readable")
  }

  def update(customerAddressId: String, line1: String, line2: String, line3: String, city: String,
             county: String, state: String, postcode: String, countryCode: String, tags: String,
             status: String): Box[MappedCustomerAddress] = {
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedcustomeraddress SET mline1 = $line1, mline2 = $line2, mline3 = $line3,
              mcity = $city, mcounty = $county, mstate = $state, mpostcode = $postcode,
              mcountrycode = $countryCode, mtags = $tags, mstatus = $status,
              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
            WHERE mcustomeraddressid = $customerAddressId""".update.run)
    findById(customerAddressId)
  }

  def delete(customerAddressId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedcustomeraddress WHERE mcustomeraddressid = $customerAddressId"
        .update.run) > 0

  def deleteByCustomerKey(customerKey: Long): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedcustomeraddress WHERE mcustomerid = $customerKey".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomeraddress".update.run)
    ()
  }

  private[customeraddress] def keyForCustomerId(customerId: String): Option[Long] =
    customerKey(customerId)
}

object MappedCustomerAddressProvider extends CustomerAddressProvider {

  override def getAddress(customerId: String): Future[Box[List[MappedCustomerAddress]]] = Future {
    // Mapper resolved the customer first and mapped over the Box, so an unknown customer yielded
    // Empty rather than an empty list. Preserved.
    MappedCustomerAddress.findAllByCustomerId(customerId) match {
      case Some(addresses) => Full(addresses)
      case None => Empty
    }
  }

  override def createAddress(customerId: String, line1: String, line2: String, line3: String,
                             city: String, county: String, state: String, postcode: String,
                             countryCode: String, tags: String,
                             status: String): Future[Box[CustomerAddress]] = Future {
    MappedCustomerAddress.keyForCustomerId(customerId) match {
      case Some(key) =>
        tryo(MappedCustomerAddress.insert(key, line1, line2, line3, city, county, state, postcode,
          countryCode, tags, status))
      case None =>
        Empty ?~! ErrorMessages.CustomerNotFoundByCustomerId
    }
  }

  override def updateAddress(customerAddressId: String, line1: String, line2: String,
                             line3: String, city: String, county: String, state: String,
                             postcode: String, countryCode: String, tags: String,
                             status: String): Future[Box[CustomerAddress]] = Future {
    MappedCustomerAddress.findById(customerAddressId) match {
      case Full(_) =>
        tryo(MappedCustomerAddress.update(customerAddressId, line1, line2, line3, city, county,
          state, postcode, countryCode, tags, status)).flatMap(identity)
      case Empty =>
        Empty ?~! ErrorMessages.CustomerAddressNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }

  override def deleteAddress(customerAddressId: String): Future[Box[Boolean]] = Future {
    MappedCustomerAddress.findById(customerAddressId) match {
      case Full(_) => Full(MappedCustomerAddress.delete(customerAddressId))
      case Empty   => Empty ?~! ErrorMessages.CustomerAddressNotFound
      case _       => Full(false)
    }
  }
}
