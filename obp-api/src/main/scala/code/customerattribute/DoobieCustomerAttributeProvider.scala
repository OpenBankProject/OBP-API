package code.customerattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.dto.CustomerAndAttribute
import com.openbankproject.commons.model.enums.CustomerAttributeType
import com.openbankproject.commons.model.{BankId, Customer, CustomerAttribute, CustomerId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One customer-attribute row, standing in for the Lift entity in return types. */
case class CustomerAttributeRow(
  bankId: BankId,
  customerId: CustomerId,
  customerAttributeId: String,
  attributeType: CustomerAttributeType.Value,
  name: String,
  value: String
) extends CustomerAttribute

/**
 * Doobie implementation of the customer-attribute store, replacing the Lift
 * MappedCustomerAttribute entity.
 *
 * There is no unique index on this table: only plain indexes on mCustomerId and
 * mCustomerAttributeId. createOrUpdateCustomerAttribute finds by customerAttributeId to decide
 * update vs create, matching the Mapper version, but nothing in the schema stops two rows sharing
 * an id.
 *
 * mbankidid is a historical typo in the column name (the Mapper field's own dbColumnName
 * override), preserved as-is - see the migration script.
 *
 * getCustomerIdsByAttributeNameValues reproduces the Mapper version's BySql(sqlParametersFilter,
 * ...) row-level filter: OR-across-attributes semantics (a customer matches if ANY requested
 * name/value pair is present on one of their attribute rows), not an AND-across-all-requested-
 * names filter.
 */
object DoobieCustomerAttributeProvider extends CustomerAttributeProvider {

  private def rowOf(r: (String, String, String, String, String, String)): CustomerAttributeRow =
    CustomerAttributeRow(
      bankId = BankId(r._1),
      customerId = CustomerId(r._2),
      customerAttributeId = r._3,
      attributeType = CustomerAttributeType.withName(r._4),
      name = r._5,
      value = r._6
    )

  private val selectCols: Fragment =
    fr"SELECT mbankidid, mcustomerid, mcustomerattributeid, mtype, mname, mvalue FROM mappedcustomerattribute"

  override def getCustomerAttributesFromProvider(customerId: CustomerId): Future[Box[List[CustomerAttribute]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcustomerid = ${customerId.value}")
          .query[(String, String, String, String, String, String)].to[List]
      ).map(rowOf)
    }

  override def getCustomerAttributes(bankId: BankId, customerId: CustomerId): Future[Box[List[CustomerAttribute]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankidid = ${bankId.value} AND mcustomerid = ${customerId.value}")
          .query[(String, String, String, String, String, String)].to[List]
      ).map(rowOf)
    }

  override def getCustomerIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] =
    Future {
      Full {
        if (params.isEmpty) {
          DoobieUtil.runQuery(
            sql"SELECT mcustomerid FROM mappedcustomerattribute WHERE mbankidid = ${bankId.value}".query[String].to[List])
        } else {
          val paramList = params.toList
          val filterFrag: Fragment = paramList.map { case (name, values) =>
            if (values.size == 1) {
              fr"(mname = $name AND mvalue = ${values.head})"
            } else {
              val valueFragments = values.map(v => fr"$v")
              val inClause = valueFragments.reduceLeft((a, b) => a ++ fr"," ++ b)
              fr"(mname = $name AND mvalue IN (" ++ inClause ++ fr"))"
            }
          }.reduceOption((a, b) => a ++ fr" OR " ++ b).getOrElse(fr"1=1")

          DoobieUtil.runQuery(
            (fr"SELECT mcustomerid FROM mappedcustomerattribute WHERE mbankidid = ${bankId.value} AND (" ++ filterFrag ++ fr")")
              .query[String].to[List])
        }
      }
    }

  override def getCustomerAttributesForCustomers(customers: List[Customer]): Future[Box[List[CustomerAndAttribute]]] =
    Future {
      Box !! customers.map { customer =>
        val attrs = DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE mbankidid = ${customer.bankId} AND mcustomerid = ${customer.customerId}")
            .query[(String, String, String, String, String, String)].to[List]
        ).map(rowOf)
        CustomerAndAttribute(customer, attrs)
      }
    }

  override def getCustomerAttributeById(customerAttributeId: String): Future[Box[CustomerAttribute]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcustomerattributeid = $customerAttributeId LIMIT 1")
        .query[(String, String, String, String, String, String)].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def createOrUpdateCustomerAttribute(
    bankId: BankId,
    customerId: CustomerId,
    customerAttributeId: Option[String],
    name: String,
    attributeType: CustomerAttributeType.Value,
    value: String
  ): Future[Box[CustomerAttribute]] = {
    customerAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE mcustomerattributeid = $id LIMIT 1")
            .query[(String, String, String, String, String, String)].option
        ) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runUpdate(
                sql"""UPDATE mappedcustomerattribute
                      SET mbankidid = ${bankId.value}, mcustomerid = ${customerId.value}, mname = $name, mtype = ${attributeType.toString}, mvalue = $value
                      WHERE mcustomerattributeid = $id"""
                  .update.run)
              CustomerAttributeRow(bankId, customerId, id, attributeType, name, value)
            }
          case None => Empty
        }
      }
      case None => Future {
        val id = APIUtil.generateUUID()
        Full {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedcustomerattribute (mbankidid, mcustomerid, mcustomerattributeid, mname, mtype, mvalue)
                  VALUES (${bankId.value}, ${customerId.value}, $id, $name, ${attributeType.toString}, $value)"""
              .update.run)
          CustomerAttributeRow(bankId, customerId, id, attributeType, name, value)
        }
      }
    }
  }

  override def createCustomerAttributes(
    bankId: BankId,
    customerId: CustomerId,
    customerAttributes: List[CustomerAttribute]
  ): Future[Box[List[CustomerAttribute]]] =
    Future {
      tryo {
        customerAttributes.map { customerAttribute =>
          val id = APIUtil.generateUUID()
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedcustomerattribute (mbankidid, mcustomerid, mcustomerattributeid, mname, mtype, mvalue)
                  VALUES (${bankId.value}, ${customerId.value}, $id, ${customerAttribute.name}, ${customerAttribute.attributeType.toString}, ${customerAttribute.value})"""
              .update.run)
          CustomerAttributeRow(bankId, customerId, id, customerAttribute.attributeType, customerAttribute.name, customerAttribute.value)
        }
      }
    }

  override def deleteCustomerAttribute(customerAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedcustomerattribute WHERE mcustomerattributeid = $customerAttributeId".update.run) >= 0
    )
  }
}
