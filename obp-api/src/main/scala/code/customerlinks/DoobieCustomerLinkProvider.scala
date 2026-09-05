package code.customerlinks

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.util.Date
import scala.concurrent.Future

/** One customer-link row, standing in for the Lift entity in return types. */
case class CustomerLinkRow(
  customerLinkId: String,
  bankId: String,
  customerId: String,
  otherBankId: String,
  otherCustomerId: String,
  relationshipTo: String,
  dateInserted: Date,
  dateUpdated: Date
) extends CustomerLinkTrait

/**
 * Doobie implementation of the customer-link store, replacing the Lift CustomerLink entity.
 *
 * Unique index on customerlinkid; plain indexes on customerid and othercustomerid. No test
 * coverage existed for this table before the migration, so CustomerLinkProviderTest was added
 * and confirmed green against the pristine Mapper entity first.
 */
object DoobieCustomerLinkProvider extends CustomerLinkProvider {

  private def rowOf(r: (String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)): CustomerLinkRow =
    CustomerLinkRow(
      customerLinkId = r._1,
      bankId = r._2,
      customerId = r._3,
      otherBankId = r._4,
      otherCustomerId = r._5,
      relationshipTo = r._6,
      dateInserted = new Date(r._7.getTime),
      dateUpdated = new Date(r._8.getTime)
    )

  private val selectCols: Fragment =
    fr"""SELECT customerlinkid, bankid, customerid, otherbankid, othercustomerid, relationshipto, createdat, updatedat
         FROM customerlink"""

  override def createCustomerLink(bankId: String, customerId: String, otherBankId: String, otherCustomerId: String, relationshipTo: String): Box[CustomerLinkTrait] =
    tryo {
      val id = APIUtil.generateUUID()
      DoobieUtil.runUpdate(
        sql"""INSERT INTO customerlink (customerlinkid, bankid, customerid, otherbankid, othercustomerid, relationshipto, createdat, updatedat)
              VALUES ($id, $bankId, $customerId, $otherBankId, $otherCustomerId, $relationshipTo, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"""
          .update.run)
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE customerlinkid = $id LIMIT 1")
          .query[(String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)].unique
      )
    }.map(rowOf)

  override def getCustomerLinkById(customerLinkId: String): Box[CustomerLinkTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE customerlinkid = $customerLinkId LIMIT 1")
        .query[(String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def getCustomerLinksByBankId(bankId: String): Box[List[CustomerLinkTrait]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = $bankId")
          .query[(String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)].to[List]
      ).map(rowOf)
    }

  override def getCustomerLinksByCustomerId(customerId: String): Box[List[CustomerLinkTrait]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE customerid = $customerId")
          .query[(String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)].to[List]
      ).map(rowOf)
    }

  override def updateCustomerLinkById(customerLinkId: String, relationshipTo: String): Box[CustomerLinkTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE customerlinkid = $customerLinkId LIMIT 1")
        .query[(String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)].option
    ) match {
      case Some(_) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"UPDATE customerlink SET relationshipto = $relationshipTo, updatedat = CURRENT_TIMESTAMP WHERE customerlinkid = $customerLinkId"
              .update.run)
          DoobieUtil.runQuery(
            (selectCols ++ fr"WHERE customerlinkid = $customerLinkId LIMIT 1")
              .query[(String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)].unique
          )
        }.map(rowOf)
      case None => Empty ?~! ErrorMessages.CustomerLinkNotFound
    }

  override def deleteCustomerLinkById(customerLinkId: String): Future[Box[Boolean]] = Future {
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM customerlink WHERE customerlinkid = $customerLinkId".query[Int].unique) match {
      case 0 => Empty ?~! ErrorMessages.CustomerLinkNotFound
      case _ =>
        DoobieUtil.runUpdate(sql"DELETE FROM customerlink WHERE customerlinkid = $customerLinkId".update.run)
        Full(true)
    }
  }

  override def bulkDeleteCustomerLinks(): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM customerlink".update.run)
    true
  }
}
