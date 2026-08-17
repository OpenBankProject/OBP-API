package code.apiproductattribute

import java.sql.Timestamp

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

/** One api-product-attribute row, standing in for the Lift entity in return types. */
case class ApiProductAttributeRow(
  bankId: String,
  apiProductCode: String,
  apiProductAttributeId: String,
  name: String,
  attributeType: String,
  value: String,
  isActive: Option[Boolean]
) extends ApiProductAttributeTrait

/**
 * Doobie implementation of the api-product-attribute store, replacing the Lift
 * ApiProductAttribute entity.
 *
 * createOrUpdateApiProductAttribute looks up by apiProductAttributeId, not by
 * (bankId, apiProductCode) - a bank/product pair can carry more than one attribute with the same
 * name at once, so the unique index (and this lookup) is on the id alone. Supplying an id with no
 * matching row falls back to create, matching the Mapper version.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object DoobieApiProductAttributesProvider extends MdcLoggable with ApiProductAttributesProvider {

  private def rowOf(r: (String, String, String, String, String, String, Boolean)): ApiProductAttributeRow =
    ApiProductAttributeRow(r._1, r._2, r._3, r._4, r._5, r._6, Some(r._7))

  private val selectCols: Fragment =
    fr"""SELECT bankid, apiproductcode, apiproductattributeid, name, type_c, value, isactive
         FROM apiproductattribute"""

  override def getApiProductAttributesByBankIdAndCode(
    bankId: String,
    apiProductCode: String
  ): Box[List[ApiProductAttributeTrait]] = tryo {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bankid = $bankId AND apiproductcode = $apiProductCode")
        .query[(String, String, String, String, String, String, Boolean)].to[List]
    ).map(rowOf)
  }

  override def getApiProductAttributeById(apiProductAttributeId: String): Box[ApiProductAttributeTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE apiproductattributeid = $apiProductAttributeId LIMIT 1")
        .query[(String, String, String, String, String, String, Boolean)].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def createOrUpdateApiProductAttribute(
    bankId: String,
    apiProductCode: String,
    apiProductAttributeId: Option[String],
    name: String,
    attributeType: String,
    value: String,
    isActive: Option[Boolean]
  ): Box[ApiProductAttributeTrait] = {
    val active = isActive.getOrElse(true)
    apiProductAttributeId.flatMap(id => getApiProductAttributeById(id).toOption) match {
      case Some(_) =>
        val id = apiProductAttributeId.get
        tryo {
          DoobieUtil.runUpdate(
            sql"""UPDATE apiproductattribute
                  SET bankid = $bankId, apiproductcode = $apiProductCode, name = $name,
                      type_c = $attributeType, value = $value, isactive = $active
                  WHERE apiproductattributeid = $id"""
              .update.run)
          ApiProductAttributeRow(bankId, apiProductCode, id, name, attributeType, value, Some(active))
        }
      case None =>
        createNew(bankId, apiProductCode, name, attributeType, value, active)
    }
  }

  private def createNew(
    bankId: String,
    apiProductCode: String,
    name: String,
    attributeType: String,
    value: String,
    isActive: Boolean
  ): Box[ApiProductAttributeTrait] = {
    val id = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis)
    tryo {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO apiproductattribute
                (apiproductattributeid, bankid, apiproductcode, name, type_c, value, isactive, createdat, updatedat)
              VALUES ($id, $bankId, $apiProductCode, $name, $attributeType, $value, $isActive, $now, $now)"""
          .update.run)
      ApiProductAttributeRow(bankId, apiProductCode, id, name, attributeType, value, Some(isActive))
    }
  }

  override def deleteApiProductAttribute(apiProductAttributeId: String): Box[Boolean] =
    getApiProductAttributeById(apiProductAttributeId) match {
      case Full(_) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"DELETE FROM apiproductattribute WHERE apiproductattributeid = $apiProductAttributeId".update.run)
          true
        }
      case _ => Empty
    }

  override def deleteApiProductAttributesByBankIdAndCode(bankId: String, apiProductCode: String): Box[Boolean] =
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM apiproductattribute WHERE bankid = $bankId AND apiproductcode = $apiProductCode".update.run)
      true
    }
}
