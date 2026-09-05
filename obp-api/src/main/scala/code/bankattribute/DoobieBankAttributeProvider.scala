package code.bankattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.enums.BankAttributeType
import com.openbankproject.commons.model.{BankAttributeTrait, BankId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One bank-attribute row, standing in for the Lift entity in return types. */
case class BankAttributeRow(
  bankId: BankId,
  bankAttributeId: String,
  attributeType: BankAttributeType.Value,
  name: String,
  value: String,
  isActive: Option[Boolean]
) extends BankAttributeTrait

/**
 * Doobie implementation of the bank-attribute store, replacing the Lift BankAttribute entity.
 *
 * There is no unique index on this table: only a plain index on bankid_ (bankid_ keeps the
 * trailing underscore from the Mapper field name BankId_, which had no dbColumnName override -
 * see the migration script). createOrUpdateBankAttribute finds by bankAttributeId to decide
 * update vs create, matching the Mapper version, but nothing in the schema stops two rows sharing
 * an id.
 *
 * The Type column is stored as type_c - Lift Mapper suffixes reserved SQL words, and TYPE
 * collides with H2's reserved TYPE keyword.
 */
object DoobieBankAttributeProvider extends BankAttributeProviderTrait {

  private def rowOf(r: (String, String, String, String, String, Option[Boolean])): BankAttributeRow =
    BankAttributeRow(
      bankId = BankId(r._1),
      bankAttributeId = r._2,
      attributeType = BankAttributeType.withName(r._3),
      name = r._4,
      value = r._5,
      isActive = r._6
    )

  private val selectCols: Fragment =
    fr"SELECT bankid_, bankattributeid, type_c, name, value, isactive FROM bankattribute"

  override def getBankAttributesFromProvider(bankId: BankId): Future[Box[List[BankAttributeTrait]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid_ = ${bankId.value}")
          .query[(String, String, String, String, String, Option[Boolean])].to[List]
      ).map(rowOf)
    }

  override def getBankAttributeById(bankAttributeId: String): Future[Box[BankAttributeTrait]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bankattributeid = $bankAttributeId LIMIT 1")
        .query[(String, String, String, String, String, Option[Boolean])].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def createOrUpdateBankAttribute(
    bankId: BankId,
    bankAttributeId: Option[String],
    name: String,
    attributType: BankAttributeType.Value,
    value: String,
    isActive: Option[Boolean]
  ): Future[Box[BankAttributeTrait]] = {
    val activeValue = isActive.getOrElse(true)
    bankAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE bankattributeid = $id LIMIT 1")
            .query[(String, String, String, String, String, Option[Boolean])].option
        ) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runUpdate(
                sql"""UPDATE bankattribute
                      SET bankid_ = ${bankId.value}, name = $name, type_c = ${attributType.toString}, value = $value, isactive = $activeValue
                      WHERE bankattributeid = $id"""
                  .update.run)
              BankAttributeRow(bankId, id, attributType, name, value, Some(activeValue))
            }
          case None => Empty
        }
      }
      case None => Future {
        val id = APIUtil.generateUUID()
        Full {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO bankattribute (bankid_, bankattributeid, name, type_c, value, isactive)
                  VALUES (${bankId.value}, $id, $name, ${attributType.toString}, $value, $activeValue)"""
              .update.run)
          BankAttributeRow(bankId, id, attributType, name, value, Some(activeValue))
        }
      }
    }
  }

  override def deleteBankAttribute(bankAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      DoobieUtil.runUpdate(
        sql"DELETE FROM bankattribute WHERE bankattributeid = $bankAttributeId".update.run) >= 0
    )
  }
}
