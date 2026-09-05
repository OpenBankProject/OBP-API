package code.counterpartyattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.enums.CounterpartyAttributeType
import com.openbankproject.commons.model.{CounterpartyAttributeTrait, CounterpartyId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One counterparty-attribute row, standing in for the Lift entity in return types. */
case class CounterpartyAttributeRow(
  counterpartyId: CounterpartyId,
  counterpartyAttributeId: String,
  attributeType: CounterpartyAttributeType.Value,
  name: String,
  value: String,
  isActive: Option[Boolean]
) extends CounterpartyAttributeTrait

/**
 * Doobie implementation of the counterparty-attribute store, replacing the Lift
 * CounterpartyAttribute entity.
 *
 * There is no unique index on this table: only a plain index on counterpartyid.
 * createOrUpdateCounterpartyAttribute finds by counterpartyAttributeId to decide update vs
 * create, matching the Mapper version, but nothing in the schema stops two rows sharing an id.
 *
 * The Type column is stored as type_c - Lift Mapper suffixes reserved SQL words, and TYPE
 * collides with H2's reserved TYPE keyword.
 */
object DoobieCounterpartyAttributeProvider extends CounterpartyAttributeProviderTrait {

  private def rowOf(r: (String, String, String, String, String, Option[Boolean])): CounterpartyAttributeRow =
    CounterpartyAttributeRow(
      counterpartyId = CounterpartyId(r._1),
      counterpartyAttributeId = r._2,
      attributeType = CounterpartyAttributeType.withName(r._3),
      name = r._4,
      value = r._5,
      isActive = r._6
    )

  private val selectCols: Fragment =
    fr"SELECT counterpartyid, counterpartyattributeid, type_c, name, value, isactive FROM counterpartyattribute"

  override def getCounterpartyAttributes(counterpartyId: CounterpartyId): Future[Box[List[CounterpartyAttributeTrait]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE counterpartyid = ${counterpartyId.value}")
          .query[(String, String, String, String, String, Option[Boolean])].to[List]
      ).map(rowOf)
    }

  override def getCounterpartyAttributeById(counterpartyAttributeId: String): Future[Box[CounterpartyAttributeTrait]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE counterpartyattributeid = $counterpartyAttributeId LIMIT 1")
        .query[(String, String, String, String, String, Option[Boolean])].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def createOrUpdateCounterpartyAttribute(
    counterpartyId: CounterpartyId,
    counterpartyAttributeId: Option[String],
    name: String,
    attributeType: CounterpartyAttributeType.Value,
    value: String,
    isActive: Option[Boolean]
  ): Future[Box[CounterpartyAttributeTrait]] = {
    val activeValue = isActive.getOrElse(true)
    counterpartyAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE counterpartyattributeid = $id LIMIT 1")
            .query[(String, String, String, String, String, Option[Boolean])].option
        ) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runUpdate(
                sql"""UPDATE counterpartyattribute
                      SET counterpartyid = ${counterpartyId.value}, name = $name, type_c = ${attributeType.toString}, value = $value, isactive = $activeValue
                      WHERE counterpartyattributeid = $id"""
                  .update.run)
              CounterpartyAttributeRow(counterpartyId, id, attributeType, name, value, Some(activeValue))
            }
          case None => Empty
        }
      }
      case None => Future {
        val id = APIUtil.generateUUID()
        Full {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO counterpartyattribute (counterpartyid, counterpartyattributeid, name, type_c, value, isactive)
                  VALUES (${counterpartyId.value}, $id, $name, ${attributeType.toString}, $value, $activeValue)"""
              .update.run)
          CounterpartyAttributeRow(counterpartyId, id, attributeType, name, value, Some(activeValue))
        }
      }
    }
  }

  override def deleteCounterpartyAttribute(counterpartyAttributeId: String): Future[Box[Boolean]] = Future {
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM counterpartyattribute WHERE counterpartyattributeid = $counterpartyAttributeId".update.run) >= 0
    }
  }

  override def deleteCounterpartyAttributesByCounterpartyId(counterpartyId: CounterpartyId): Future[Box[Boolean]] = Future {
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM counterpartyattribute WHERE counterpartyid = ${counterpartyId.value}".update.run) >= 0
    }
  }
}
