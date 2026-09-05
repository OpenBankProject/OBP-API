package code.taxresidence

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import code.customer.MappedCustomer
import com.openbankproject.commons.model.TaxResidence
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

/** One tax-residence row, standing in for the Lift entity in return types. */
case class TaxResidenceRow(
  customerId: String,
  taxResidenceId: String,
  domain: String,
  taxNumber: String
) extends TaxResidence

/**
 * Doobie implementation of the tax-residence store, replacing the Lift MappedTaxResidence entity.
 *
 * mcustomerid stores the customer's internal BIGINT primary key (mappedcustomer.id, still a
 * Mapper entity), not the customer's UUID customerId - matching the Mapper field's own
 * MappedLongForeignKey(this, MappedCustomer). customerId is resolved back to the UUID via
 * MappedCustomer.find(By(MappedCustomer.id, ...)), falling back to the raw long id as a string
 * if the customer row is somehow missing - the same fallback the Mapper entity's own
 * customerId getter used (mCustomerId.foreign.map(_.customerId).getOrElse(mCustomerId.get.toString)).
 *
 * The UNIQUE INDEX on (mcustomerid, mdomain, mtaxnumber) means createTaxResidence can violate a
 * DB constraint for a duplicate domain+number pair - this was already true under Mapper (saveMe()
 * would throw), so the behavior is unchanged, just surfaced as a different exception type.
 */
object DoobieTaxResidenceProvider extends TaxResidenceProvider {

  private def resolveCustomerId(longId: Long): String =
    MappedCustomer.findByPrimaryKey(longId).map(_.customerId).getOrElse(longId.toString)

  private def rowOf(r: (Long, String, String, String)): TaxResidenceRow =
    TaxResidenceRow(
      customerId = resolveCustomerId(r._1),
      taxResidenceId = r._2,
      domain = r._3,
      taxNumber = r._4
    )

  private val selectCols: Fragment =
    fr"SELECT mcustomerid, mtaxresidenceid, mdomain, mtaxnumber FROM mappedtaxresidence"

  override def getTaxResidence(customerId: String): Future[Box[List[TaxResidence]]] = Future {
    MappedCustomer.findByCustomerId(customerId) match {
      case Full(customer) =>
        Full(
          DoobieUtil.runQuery(
            (selectCols ++ fr"WHERE mcustomerid = ${customer.customerPrimaryKey}")
              .query[(Long, String, String, String)].to[List]
          ).map(rowOf)
        )
      case Empty => Empty
      case f: Failure => f
    }
  }

  override def createTaxResidence(customerId: String, domain: String, taxNumber: String): Future[Box[TaxResidence]] = Future {
    MappedCustomer.findByCustomerId(customerId) match {
      case Full(customer) =>
        tryo {
          val id = APIUtil.generateUUID()
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedtaxresidence (mcustomerid, mtaxresidenceid, mdomain, mtaxnumber, createdat, updatedat)
                  VALUES (${customer.customerPrimaryKey}, $id, $domain, $taxNumber, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"""
              .update.run)
          TaxResidenceRow(customerId, id, domain, taxNumber)
        }
      case Empty =>
        Empty ?~! ErrorMessages.CustomerNotFoundByCustomerId
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }

  override def deleteTaxResidence(taxResidenceId: String): Future[Box[Boolean]] = Future {
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM mappedtaxresidence WHERE mtaxresidenceid = $taxResidenceId".query[Int].unique) match {
      case 0 => Empty ?~! ErrorMessages.TaxResidenceNotFound
      case _ =>
        DoobieUtil.runUpdate(sql"DELETE FROM mappedtaxresidence WHERE mtaxresidenceid = $taxResidenceId".update.run)
        Full(true)
    }
  }
}
