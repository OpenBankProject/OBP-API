package code.customer.internalMapping

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.CustomerId
import doobie.implicits._
import net.liftweb.common._
import net.liftweb.util.Helpers.tryo

/**
 * Doobie implementation of the customer-id-mapping store, replacing the Lift
 * MappedCustomerIdMapping entity. Third of the id-mapping triplet
 * (DoobieAccountIdMappingProvider is the pattern this follows) - same schema gap, documented
 * once there and in the migration script for this table.
 *
 * Kept under its original name rather than a Doobie* one, for the same reason as
 * MappedAccountIdMappingProvider: DynamicUtil's compiled-code template hands this exact import
 * to every dynamic connector method, and connector method bodies are stored as raw Scala source
 * and compiled at request time - a bank's already-deployed dynamic connector code can reference
 * this name by hand.
 *
 * mBankId/mCustomerNumber are deprecated columns nothing reads through this provider - neither
 * method on CustomerIdMappingProvider returns anything that carries them - so they are left
 * alone rather than threaded through here.
 */
object MappedCustomerIdMappingProvider extends CustomerIdMappingProvider with MdcLoggable {

  override def getOrCreateCustomerId(customerPlainTextReference: String): Box[CustomerId] = {
    findByReference(customerPlainTextReference) match {
      case Full(customerId) =>
        logger.debug(s"getOrCreateCustomerId --> the mappedCustomerIdMapping has been existing in server !")
        Full(customerId)
      case Empty =>
        val newCustomerId = APIUtil.generateUUID()
        val inserted: Box[Int] = tryo {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedcustomeridmapping (mcustomerid, mcustomerplaintextreference, createdat, updatedat)
                  VALUES ($newCustomerId, $customerPlainTextReference, NOW(), NOW())"""
              .update.run)
        }
        inserted match {
          case Full(_) =>
            logger.debug(s"getOrCreateCustomerId--> create mappedCustomerIdMapping : $newCustomerId")
            Full(CustomerId(newCustomerId))
          case Failure(_, _, _) =>
            // Unique-index violation from a concurrent insert — re-fetch the committed row.
            findByReference(customerPlainTextReference)
          case Empty =>
            findByReference(customerPlainTextReference)
        }
      case failure => failure
    }
  }

  private def findByReference(customerPlainTextReference: String): Box[CustomerId] =
    DoobieUtil.runQuery(
      sql"SELECT mcustomerid FROM mappedcustomeridmapping WHERE mcustomerplaintextreference = $customerPlainTextReference LIMIT 1"
        .query[String].option
    ) match {
      case Some(id) => Full(CustomerId(id))
      case None     => Empty
    }

  override def getCustomerPlainTextReference(customerId: CustomerId): Box[String] =
    DoobieUtil.runQuery(
      sql"SELECT mcustomerplaintextreference FROM mappedcustomeridmapping WHERE mcustomerid = ${customerId.value} LIMIT 1"
        .query[String].option
    ) match {
      case Some(ref) => Full(ref)
      case None      => Empty
    }
}
