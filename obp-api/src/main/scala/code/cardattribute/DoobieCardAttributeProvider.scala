package code.cardattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.enums.CardAttributeType
import com.openbankproject.commons.model.{BankId, CardAttribute}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One card-attribute row, standing in for the Lift entity in return types. */
case class CardAttributeRow(
  bankId: Option[BankId],
  cardId: Option[String],
  cardAttributeId: Option[String],
  name: String,
  attributeType: CardAttributeType.Value,
  value: String
) extends CardAttribute

/**
 * Doobie implementation of the card-attribute store, replacing the Lift MappedCardAttribute
 * entity.
 *
 * There is no unique index on this table (see the migration script): only plain indexes on
 * mCardId and mCardAttributeId. createOrUpdateCardAttribute finds by cardAttributeId to decide
 * update vs create, matching the Mapper version, but nothing stops two rows sharing an id if
 * something outside this provider ever inserted one directly.
 *
 * bankId/cardId are stored as nullable columns and always read back wrapped in Some(...), even
 * when the underlying column is null - matching the Mapper version's own getters
 * (`bankId: Some[BankId]`, `cardId: Some[String]`), which never produced None for an existing
 * row regardless of whether the column had been set.
 */
object DoobieCardAttributeProvider extends CardAttributeProvider {

  private def rowOf(r: (Option[String], Option[String], String, String, String, String)): CardAttributeRow =
    CardAttributeRow(
      bankId = Some(BankId(r._1.orNull)),
      cardId = Some(r._2.orNull),
      cardAttributeId = Some(r._3),
      name = r._4,
      attributeType = CardAttributeType.withName(r._5),
      value = r._6
    )

  private val selectCols: Fragment =
    fr"SELECT mbankid, mcardid, mcardattributeid, mname, mtype, mvalue FROM mappedcardattribute"

  override def getCardAttributesFromProvider(cardId: String): Future[Box[List[CardAttribute]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcardid = $cardId")
          .query[(Option[String], Option[String], String, String, String, String)].to[List]
      ).map(rowOf)
    }

  override def getCardAttributeById(cardAttributeId: String): Future[Box[CardAttribute]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcardattributeid = $cardAttributeId LIMIT 1")
        .query[(Option[String], Option[String], String, String, String, String)].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def createOrUpdateCardAttribute(
    bankId: Option[BankId],
    cardId: Option[String],
    cardAttributeId: Option[String],
    name: String,
    attributeType: CardAttributeType.Value,
    value: String
  ): Future[Box[CardAttribute]] = {
    val bankIdValue = bankId.map(_.value)
    cardAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE mcardattributeid = $id LIMIT 1")
            .query[(Option[String], Option[String], String, String, String, String)].option
        ) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runUpdate(
                sql"""UPDATE mappedcardattribute
                      SET mcardid = $cardId, mbankid = $bankIdValue, mname = $name, mtype = ${attributeType.toString}, mvalue = $value
                      WHERE mcardattributeid = $id"""
                  .update.run)
              CardAttributeRow(Some(bankId.orNull), cardId, Some(id), name, attributeType, value)
            }
          case None => Empty
        }
      }
      case None => Future {
        val id = APIUtil.generateUUID()
        Full {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedcardattribute (mcardid, mbankid, mcardattributeid, mname, mtype, mvalue)
                  VALUES ($cardId, $bankIdValue, $id, $name, ${attributeType.toString}, $value)"""
              .update.run)
          CardAttributeRow(Some(bankId.orNull), cardId, Some(id), name, attributeType, value)
        }
      }
    }
  }

  override def deleteCardAttribute(cardAttributeId: String): Future[Box[Boolean]] = Future {
    Some {
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedcardattribute WHERE mcardattributeid = $cardAttributeId".update.run) > 0
    }
  }
}
