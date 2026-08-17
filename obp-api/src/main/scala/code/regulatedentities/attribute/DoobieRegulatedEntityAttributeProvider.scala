package code.regulatedentities.attribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.enums.RegulatedEntityAttributeType
import com.openbankproject.commons.model.{RegulatedEntityAttributeTrait, RegulatedEntityId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One regulated-entity-attribute row, standing in for the Lift entity in return types. */
case class RegulatedEntityAttributeRow(
  regulatedEntityId: RegulatedEntityId,
  regulatedEntityAttributeId: String,
  attributeType: RegulatedEntityAttributeType.Value,
  name: String,
  value: String,
  isActive: Option[Boolean]
) extends RegulatedEntityAttributeTrait

/**
 * Doobie implementation of the regulated-entity-attribute store, replacing the Lift
 * RegulatedEntityAttribute entity.
 *
 * There is no unique index on this table: only a plain index on regulatedentityid.
 * createOrUpdateRegulatedEntityAttribute finds by regulatedEntityAttributeId to decide update vs
 * create, matching the Mapper version, but nothing in the schema stops two rows sharing an id.
 *
 * The Type column is stored as type_c - Lift Mapper suffixes reserved SQL words, and TYPE
 * collides with H2's reserved TYPE keyword.
 */
object DoobieRegulatedEntityAttributeProvider extends RegulatedEntityAttributeProviderTrait {

  private def rowOf(r: (String, String, String, String, String, Option[Boolean])): RegulatedEntityAttributeRow =
    RegulatedEntityAttributeRow(
      regulatedEntityId = RegulatedEntityId(r._1),
      regulatedEntityAttributeId = r._2,
      attributeType = RegulatedEntityAttributeType.withName(r._3),
      name = r._4,
      value = r._5,
      isActive = r._6
    )

  private val selectCols: Fragment =
    fr"SELECT regulatedentityid, regulatedentityattributeid, type_c, name, value, isactive FROM regulatedentityattribute"

  override def getRegulatedEntityAttributes(regulatedEntityId: RegulatedEntityId): Future[Box[List[RegulatedEntityAttributeTrait]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE regulatedentityid = ${regulatedEntityId.value}")
          .query[(String, String, String, String, String, Option[Boolean])].to[List]
      ).map(rowOf)
    }

  override def getRegulatedEntityAttributeById(regulatedEntityAttributeId: String): Future[Box[RegulatedEntityAttributeTrait]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE regulatedentityattributeid = $regulatedEntityAttributeId LIMIT 1")
        .query[(String, String, String, String, String, Option[Boolean])].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def createOrUpdateRegulatedEntityAttribute(
    regulatedEntityId: RegulatedEntityId,
    regulatedEntityAttributeId: Option[String],
    name: String,
    attributeType: RegulatedEntityAttributeType.Value,
    value: String,
    isActive: Option[Boolean]
  ): Future[Box[RegulatedEntityAttributeTrait]] = {
    val activeValue = isActive.getOrElse(true)
    regulatedEntityAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE regulatedentityattributeid = $id LIMIT 1")
            .query[(String, String, String, String, String, Option[Boolean])].option
        ) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runUpdate(
                sql"""UPDATE regulatedentityattribute
                      SET regulatedentityid = ${regulatedEntityId.value}, name = $name, type_c = ${attributeType.toString}, value = $value, isactive = $activeValue
                      WHERE regulatedentityattributeid = $id"""
                  .update.run)
              RegulatedEntityAttributeRow(regulatedEntityId, id, attributeType, name, value, Some(activeValue))
            }
          case None => Empty
        }
      }
      case None => Future {
        val id = APIUtil.generateUUID()
        Full {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO regulatedentityattribute (regulatedentityid, regulatedentityattributeid, name, type_c, value, isactive)
                  VALUES (${regulatedEntityId.value}, $id, $name, ${attributeType.toString}, $value, $activeValue)"""
              .update.run)
          RegulatedEntityAttributeRow(regulatedEntityId, id, attributeType, name, value, Some(activeValue))
        }
      }
    }
  }

  override def deleteRegulatedEntityAttribute(regulatedEntityAttributeId: String): Future[Box[Boolean]] = Future {
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM regulatedentityattribute WHERE regulatedentityattributeid = $regulatedEntityAttributeId".update.run) >= 0
    }
  }

  override def deleteRegulatedEntityAttributesByRegulatedEntityId(regulatedEntityId: RegulatedEntityId): Future[Box[Boolean]] = Future {
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM regulatedentityattribute WHERE regulatedentityid = ${regulatedEntityId.value}".update.run) >= 0
    }
  }

  /** Direct query used by MappedRegulatedEntity.attributes - see that file for context. */
  def getRegulatedEntityAttributesSync(regulatedEntityId: String): List[RegulatedEntityAttributeRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE regulatedentityid = $regulatedEntityId")
        .query[(String, String, String, String, String, Option[Boolean])].to[List]
    ).map(rowOf)
}
