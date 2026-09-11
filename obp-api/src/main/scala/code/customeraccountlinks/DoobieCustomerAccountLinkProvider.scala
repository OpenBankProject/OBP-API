package code.customeraccountlinks

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.model.CustomerAccountLinkTrait
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

/** One customer-account-link row, standing in for the Lift entity in return types. */
case class CustomerAccountLinkRow(
  customerAccountLinkId: String,
  customerId: String,
  bankId: String,
  accountId: String,
  relationshipType: String
) extends CustomerAccountLinkTrait

/**
 * Doobie implementation of the customer-account-link store, replacing the Lift
 * CustomerAccountLink entity.
 *
 * Two unique indexes: one on customeraccountlinkid, one on the composite
 * (accountid, customerid) - a customer has at most one link per account, matching the entity's
 * own dbIndexes.
 */
object DoobieCustomerAccountLinkProvider extends CustomerAccountLinkProvider {

  // Only `id` is NOT NULL on this table. `bankid` in particular was added to the model two months
  // after the table existed, and Schemifier added it with no backfill, so links created in that
  // window hold SQL NULL there. Binding bare made doobie raise NonNullableColumnRead and fail the
  // whole listing; each column is collapsed the way its MappedString read a NULL.
  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[String])

  private def rowOf(r: Row): CustomerAccountLinkRow =
    CustomerAccountLinkRow(
      customerAccountLinkId = r._1.orNull,
      customerId = r._2.orNull,
      bankId = r._3.orNull,
      accountId = r._4.orNull,
      relationshipType = r._5.orNull
    )

  private val selectCols: Fragment =
    fr"SELECT customeraccountlinkid, customerid, bankid, accountid, relationshiptype FROM customeraccountlink"

  private def insert(customerId: String, bankId: String, accountId: String, relationshipType: String): CustomerAccountLinkRow = {
    val id = APIUtil.generateUUID()
    DoobieUtil.runUpdate(
      sql"""INSERT INTO customeraccountlink (customeraccountlinkid, customerid, bankid, accountid, relationshiptype, createdat, updatedat)
            VALUES ($id, $customerId, $bankId, $accountId, $relationshipType, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"""
        .update.run)
    CustomerAccountLinkRow(id, customerId, bankId, accountId, relationshipType)
  }

  override def createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait] =
    tryo(insert(customerId, bankId, accountId, relationshipType))

  override def getOrCreateCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE customerid = $customerId AND bankid = $bankId AND accountid = $accountId LIMIT 1")
        .query[Row].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Full(insert(customerId, bankId, accountId, relationshipType))
    }

  override def getCustomerAccountLinkByCustomerId(customerId: String): Box[CustomerAccountLinkTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE customerid = $customerId LIMIT 1")
        .query[Row].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = $bankId AND accountid = $accountId")
          .query[Row].to[List]
      ).map(rowOf)
    }

  override def getCustomerAccountLinksByCustomerId(customerId: String): Box[List[CustomerAccountLinkTrait]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE customerid = $customerId")
          .query[Row].to[List]
      ).map(rowOf)
    }

  override def getCustomerAccountLinksByAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = $bankId AND accountid = $accountId")
          .query[Row].to[List]
      ).map(rowOf)
    }

  override def getCustomerAccountLinkById(customerAccountLinkId: String): Box[CustomerAccountLinkTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE customeraccountlinkid = $customerAccountLinkId LIMIT 1")
        .query[Row].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String): Box[CustomerAccountLinkTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE customeraccountlinkid = $customerAccountLinkId LIMIT 1")
        .query[Row].option
    ) match {
      case Some(r) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"UPDATE customeraccountlink SET relationshiptype = $relationshipType, updatedat = CURRENT_TIMESTAMP WHERE customeraccountlinkid = $customerAccountLinkId"
              .update.run)
          rowOf(r).copy(relationshipType = relationshipType)
        }
      case None => Empty ?~! ErrorMessages.CustomerAccountLinkNotFound
    }

  override def getCustomerAccountLinks: Box[List[CustomerAccountLinkTrait]] =
    tryo {
      DoobieUtil.runQuery(selectCols.query[Row].to[List]).map(rowOf)
    }

  override def bulkDeleteCustomerAccountLinks(): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM customeraccountlink".update.run)
    true
  }

  /** Direct query used by deletion.DeleteBankCascade.delete (filters by accountId only). */
  def findByAccountIdSync(accountId: String): List[CustomerAccountLinkRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE accountid = $accountId")
        .query[Row].to[List]
    ).map(rowOf)

  /** Direct query used by deletion.DeleteCustomerCascade.delete. */
  def deleteByCustomerIdSync(customerId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM customeraccountlink WHERE customerid = $customerId".update.run)
    true
  }

  override def deleteCustomerAccountLinkById(customerAccountLinkId: String): Future[Box[Boolean]] = Future {
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM customeraccountlink WHERE customeraccountlinkid = $customerAccountLinkId".query[Int].unique) match {
      case 0 => Empty ?~! ErrorMessages.CustomerAccountLinkNotFound
      case _ =>
        DoobieUtil.runUpdate(sql"DELETE FROM customeraccountlink WHERE customeraccountlinkid = $customerAccountLinkId".update.run)
        Full(true)
    }
  }
}
