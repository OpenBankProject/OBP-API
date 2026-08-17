package code.atmattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.enums.AtmAttributeType
import com.openbankproject.commons.model.{AtmAttributeTrait, AtmId, BankId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One ATM-attribute row, standing in for the Lift entity in return types. */
case class AtmAttributeRow(
  bankId: BankId,
  atmId: AtmId,
  atmAttributeId: String,
  attributeType: AtmAttributeType.Value,
  name: String,
  value: String,
  isActive: Option[Boolean]
) extends AtmAttributeTrait

/**
 * Doobie implementation of the ATM-attribute store, replacing the Lift AtmAttribute entity.
 *
 * There is no unique index on this table: only a plain composite index on (BankId, AtmId),
 * matching the entity's own dbIndexes. createOrUpdateAtmAttribute finds by atmAttributeId to
 * decide update vs create, matching the Mapper version, but nothing in the schema stops two rows
 * sharing an id.
 *
 * The Type column is stored as type_c - Lift Mapper suffixes reserved SQL words, and TYPE
 * collides with H2's reserved TYPE keyword (see the migration script).
 */
object DoobieAtmAttributeProvider extends AtmAttributeProviderTrait {

  private def rowOf(r: (String, String, String, String, String, String, Option[Boolean])): AtmAttributeRow =
    AtmAttributeRow(
      bankId = BankId(r._1),
      atmId = AtmId(r._2),
      atmAttributeId = r._3,
      attributeType = AtmAttributeType.withName(r._4),
      name = r._5,
      value = r._6,
      isActive = r._7
    )

  private val selectCols: Fragment =
    fr"SELECT bankid, atmid, atmattributeid, type_c, name, value, isactive FROM atmattribute"

  override def getAtmAttributesFromProvider(bankId: BankId, atmId: AtmId): Future[Box[List[AtmAttributeTrait]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = ${bankId.value} AND atmid = ${atmId.value}")
          .query[(String, String, String, String, String, String, Option[Boolean])].to[List]
      ).map(rowOf)
    }

  override def getAtmAttributeById(atmAttributeId: String): Future[Box[AtmAttributeTrait]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE atmattributeid = $atmAttributeId LIMIT 1")
        .query[(String, String, String, String, String, String, Option[Boolean])].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def createOrUpdateAtmAttribute(
    bankId: BankId,
    atmId: AtmId,
    atmAttributeId: Option[String],
    name: String,
    attributeType: AtmAttributeType.Value,
    value: String,
    isActive: Option[Boolean]
  ): Future[Box[AtmAttributeTrait]] = {
    val activeValue = isActive.getOrElse(true)
    atmAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE atmattributeid = $id LIMIT 1")
            .query[(String, String, String, String, String, String, Option[Boolean])].option
        ) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runUpdate(
                sql"""UPDATE atmattribute
                      SET bankid = ${bankId.value}, atmid = ${atmId.value}, name = $name, type_c = ${attributeType.toString}, value = $value, isactive = $activeValue
                      WHERE atmattributeid = $id"""
                  .update.run)
              AtmAttributeRow(bankId, atmId, id, attributeType, name, value, Some(activeValue))
            }
          case None => Empty
        }
      }
      case None => Future {
        val id = APIUtil.generateUUID()
        Full {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO atmattribute (bankid, atmid, atmattributeid, name, type_c, value, isactive)
                  VALUES (${bankId.value}, ${atmId.value}, $id, $name, ${attributeType.toString}, $value, $activeValue)"""
              .update.run)
          AtmAttributeRow(bankId, atmId, id, attributeType, name, value, Some(activeValue))
        }
      }
    }
  }

  override def deleteAtmAttribute(atmAttributeId: String): Future[Box[Boolean]] = Future {
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM atmattribute WHERE atmattributeid = $atmAttributeId".update.run) >= 0
    }
  }

  override def deleteAtmAttributesByAtmId(atmId: AtmId): Future[Box[Boolean]] = Future {
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM atmattribute WHERE atmid = ${atmId.value}".update.run) >= 0
    }
  }
}
