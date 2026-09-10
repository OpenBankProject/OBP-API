package code.apiproductsubscriptionattribute

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

/**
 * Attributes on an API Product Subscription. Billing adapters store e.g. STRIPE_SUBSCRIPTION_ID here.
 *
 * Doobie store rather than the Lift Mapper entity upstream declares: this branch has no Lift
 * Mapper and no Schemifier, so the table lives in db.changelog-develop-merge-2.yaml.
 */
case class ApiProductSubscriptionAttribute(
  apiProductSubscriptionId: String,
  apiProductSubscriptionAttributeId: String,
  name: String,
  attributeType: String,
  value: String,
  isActive: Option[Boolean]
) extends ApiProductSubscriptionAttributeTrait

object ApiProductSubscriptionAttribute {

  // type is a SQL reserved word on several vendors, so the column is type_c.
  private val selectColumns =
    fr"""SELECT apiproductsubscriptionid, apiproductsubscriptionattributeid, name, type_c, value,
                isactive
         FROM apiproductsubscriptionattribute"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Boolean])

  private def fromRow(row: Row): ApiProductSubscriptionAttribute = row match {
    case (apiProductSubscriptionId, apiProductSubscriptionAttributeId, name, attributeType,
          value, isActive) =>
      // The Mapper getter was Some(IsActive.get), and MappedBoolean read a NULL as false.
      ApiProductSubscriptionAttribute(apiProductSubscriptionId.orNull,
        apiProductSubscriptionAttributeId.orNull, name.orNull, attributeType.orNull,
        value.orNull, Some(isActive.getOrElse(false)))
  }

  private def query(condition: Fragment): List[ApiProductSubscriptionAttribute] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findBySubscriptionId(apiProductSubscriptionId: String): List[ApiProductSubscriptionAttribute] =
    query(fr"WHERE apiproductsubscriptionid = $apiProductSubscriptionId ORDER BY id ASC")

  def findById(apiProductSubscriptionAttributeId: String): Box[ApiProductSubscriptionAttribute] =
    query(fr"""WHERE apiproductsubscriptionattributeid = $apiProductSubscriptionAttributeId
               ORDER BY id ASC LIMIT 1""").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(apiProductSubscriptionId: String, name: String, attributeType: String, value: String,
             isActive: Boolean): ApiProductSubscriptionAttribute = {
    val apiProductSubscriptionAttributeId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO apiproductsubscriptionattribute
            (apiproductsubscriptionid, apiproductsubscriptionattributeid, name, type_c, value,
             isactive, createdat, updatedat)
            VALUES (${Option(apiProductSubscriptionId)}, $apiProductSubscriptionAttributeId,
             ${Option(name)}, ${Option(attributeType)}, ${Option(value)}, $isActive, $now, $now)"""
        .update.run)
    findById(apiProductSubscriptionAttributeId)
      .openOrThrowException("the subscription attribute just inserted must be readable")
  }

  def update(apiProductSubscriptionAttributeId: String, apiProductSubscriptionId: String,
             name: String, attributeType: String, value: String,
             isActive: Boolean): Box[ApiProductSubscriptionAttribute] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE apiproductsubscriptionattribute
              SET apiproductsubscriptionid = ${Option(apiProductSubscriptionId)},
                  name = ${Option(name)}, type_c = ${Option(attributeType)},
                  value = ${Option(value)}, isactive = $isActive, updatedat = $now
            WHERE apiproductsubscriptionattributeid = $apiProductSubscriptionAttributeId"""
        .update.run)
    findById(apiProductSubscriptionAttributeId)
  }

  def delete(apiProductSubscriptionAttributeId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"""DELETE FROM apiproductsubscriptionattribute
            WHERE apiproductsubscriptionattributeid = $apiProductSubscriptionAttributeId"""
        .update.run)
    true
  }

  def deleteBySubscriptionId(apiProductSubscriptionId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"""DELETE FROM apiproductsubscriptionattribute
            WHERE apiproductsubscriptionid = $apiProductSubscriptionId""".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM apiproductsubscriptionattribute".update.run)
    ()
  }
}

trait ApiProductSubscriptionAttributeTrait {
  def apiProductSubscriptionId: String
  def apiProductSubscriptionAttributeId: String
  def name: String
  def attributeType: String
  def value: String
  def isActive: Option[Boolean]
}
