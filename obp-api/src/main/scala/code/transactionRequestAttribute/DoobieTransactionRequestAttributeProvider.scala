package code.transactionRequestAttribute

import code.api.attributedefinition.AttributeDefinition
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.enums.{AttributeCategory, TransactionRequestAttributeType}
import com.openbankproject.commons.model.{BankId, TransactionRequestAttributeJsonV400, TransactionRequestAttributeTrait, TransactionRequestId, ViewId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One transaction-request-attribute row, standing in for the Lift entity in return types. */
case class TransactionRequestAttributeRow(
  bankId: BankId,
  transactionRequestId: TransactionRequestId,
  transactionRequestAttributeId: String,
  attributeType: TransactionRequestAttributeType.Value,
  name: String,
  value: String,
  isPersonal: Boolean
) extends TransactionRequestAttributeTrait

/**
 * Doobie implementation of the transaction-request-attribute store, replacing the Lift
 * TransactionRequestAttribute entity.
 *
 * There is no unique index on this table: only plain indexes on transactionrequestid and
 * transactionrequestattributeid. createOrUpdateTransactionRequestAttribute finds by
 * transactionRequestAttributeId to decide update vs create, matching the Mapper version, but
 * nothing in the schema stops two rows sharing an id.
 *
 * The Type column is stored as type_c - Lift Mapper suffixes reserved SQL words, and TYPE
 * collides with H2's reserved TYPE keyword.
 *
 * getTransactionRequestAttributesCanBeSeenOnView still reads AttributeDefinition (a separate,
 * not-yet-migrated Mapper entity) directly and joins in plain Scala, exactly as the Mapper
 * version did - including its pre-existing bug of filtering AttributeDefinition by
 * AttributeCategory.Account instead of .TransactionRequest, preserved verbatim.
 *
 * getByAttributeNameValues always filters WHERE ispersonal = true regardless of the isPersonal
 * argument - another pre-existing quirk of the Mapper version (the argument was never wired into
 * the query), preserved verbatim.
 */
object DoobieTransactionRequestAttributeProvider extends TransactionRequestAttributeProvider {

  private def rowOf(r: (String, String, String, String, String, String, Boolean)): TransactionRequestAttributeRow =
    TransactionRequestAttributeRow(
      bankId = BankId(r._1),
      transactionRequestId = TransactionRequestId(r._2),
      transactionRequestAttributeId = r._3,
      attributeType = TransactionRequestAttributeType.withName(r._4),
      name = r._5,
      value = r._6,
      isPersonal = r._7
    )

  private val selectCols: Fragment =
    fr"""SELECT bankid, transactionrequestid, transactionrequestattributeid, type_c, name, value, ispersonal
         FROM transactionrequestattribute"""

  override def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE transactionrequestid = ${transactionRequestId.value}")
          .query[(String, String, String, String, String, String, Boolean)].to[List]
      ).map(rowOf)
    }

  override def getTransactionRequestAttributes(bankId: BankId, transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = ${bankId.value} AND transactionrequestid = ${transactionRequestId.value}")
          .query[(String, String, String, String, String, String, Boolean)].to[List]
      ).map(rowOf)
    }

  override def getTransactionRequestAttributesCanBeSeenOnView(
    bankId: BankId,
    transactionRequestId: TransactionRequestId,
    viewId: ViewId
  ): Future[Box[List[TransactionRequestAttributeTrait]]] = Future {
    val attributeDefinitions = AttributeDefinition
      .findAllByBankIdAndCategory(bankId.value, AttributeCategory.Account.toString)
      .filter(_.canBeSeenOnViews.exists(_ == viewId.value))
    val transactionRequestAttributes = DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bankid = ${bankId.value} AND transactionrequestid = ${transactionRequestId.value}")
        .query[(String, String, String, String, String, String, Boolean)].to[List]
    ).map(rowOf)
    val filteredTransactionRequestAttributes = for {
      definition <- attributeDefinitions
      attribute <- transactionRequestAttributes
      if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
    } yield attribute
    Full(filteredTransactionRequestAttributes)
  }

  override def getTransactionRequestAttributeById(transactionRequestAttributeId: String): Future[Box[TransactionRequestAttributeTrait]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE transactionrequestattributeid = $transactionRequestAttributeId LIMIT 1")
        .query[(String, String, String, String, String, String, Boolean)].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]], isPersonal: Boolean): Future[Box[List[String]]] =
    getByAttributeNameValues(bankId, params, isPersonal)
      .map(attributesBox => attributesBox.map(attributes => attributes.map(_.transactionRequestId.value)))

  override def getByAttributeNameValues(bankId: BankId, params: Map[String, List[String]], isPersonal: Boolean): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      Full {
        if (params.isEmpty) {
          DoobieUtil.runQuery(
            (selectCols ++ fr"WHERE bankid = ${bankId.value} AND ispersonal = true")
              .query[(String, String, String, String, String, String, Boolean)].to[List]
          ).map(rowOf)
        } else {
          val paramList = params.toList
          val filterFrag: Fragment = paramList.map { case (name, values) =>
            if (values.size == 1) {
              fr"(name = $name AND value = ${values.head})"
            } else {
              val valueFragments = values.map(v => fr"$v")
              val inClause = valueFragments.reduceLeft((a, b) => a ++ fr"," ++ b)
              fr"(name = $name AND value IN (" ++ inClause ++ fr"))"
            }
          }.reduceOption((a, b) => a ++ fr" OR " ++ b).getOrElse(fr"1=1")

          DoobieUtil.runQuery(
            (selectCols ++ fr"WHERE bankid = ${bankId.value} AND ispersonal = true AND (" ++ filterFrag ++ fr")")
              .query[(String, String, String, String, String, String, Boolean)].to[List]
          ).map(rowOf)
        }
      }
    }

  override def createOrUpdateTransactionRequestAttribute(
    bankId: BankId,
    transactionRequestId: TransactionRequestId,
    transactionRequestAttributeId: Option[String],
    name: String,
    attributeType: TransactionRequestAttributeType.Value,
    value: String
  ): Future[Box[TransactionRequestAttributeTrait]] = {
    transactionRequestAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE transactionrequestattributeid = $id LIMIT 1")
            .query[(String, String, String, String, String, String, Boolean)].option
        ) match {
          case Some((_, _, _, _, _, _, existingIsPersonal)) =>
            tryo {
              DoobieUtil.runUpdate(
                sql"""UPDATE transactionrequestattribute
                      SET bankid = ${bankId.value}, transactionrequestid = ${transactionRequestId.value}, name = $name, type_c = ${attributeType.toString}, value = $value
                      WHERE transactionrequestattributeid = $id"""
                  .update.run)
              TransactionRequestAttributeRow(bankId, transactionRequestId, id, attributeType, name, value, existingIsPersonal)
            }
          case None => Empty
        }
      }
      case None => Future {
        val id = APIUtil.generateUUID()
        Full {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO transactionrequestattribute (bankid, transactionrequestid, transactionrequestattributeid, name, type_c, value, ispersonal)
                  VALUES (${bankId.value}, ${transactionRequestId.value}, $id, $name, ${attributeType.toString}, $value, ${false})"""
              .update.run)
          TransactionRequestAttributeRow(bankId, transactionRequestId, id, attributeType, name, value, isPersonal = false)
        }
      }
    }
  }

  override def createTransactionRequestAttributes(
    bankId: BankId,
    transactionRequestId: TransactionRequestId,
    transactionRequestAttributes: List[TransactionRequestAttributeJsonV400],
    isPersonal: Boolean
  ): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      tryo {
        transactionRequestAttributes.map { transactionRequestAttribute =>
          val id = APIUtil.generateUUID()
          val attributeType = TransactionRequestAttributeType.withName(transactionRequestAttribute.attribute_type)
          DoobieUtil.runUpdate(
            sql"""INSERT INTO transactionrequestattribute (bankid, transactionrequestid, transactionrequestattributeid, name, type_c, value, ispersonal)
                  VALUES (${bankId.value}, ${transactionRequestId.value}, $id, ${transactionRequestAttribute.name}, ${transactionRequestAttribute.attribute_type}, ${transactionRequestAttribute.value}, $isPersonal)"""
              .update.run)
          TransactionRequestAttributeRow(bankId, transactionRequestId, id, attributeType, transactionRequestAttribute.name, transactionRequestAttribute.value, isPersonal)
        }
      }
    }

  override def deleteTransactionRequestAttribute(transactionRequestAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      DoobieUtil.runUpdate(
        sql"DELETE FROM transactionrequestattribute WHERE transactionrequestattributeid = $transactionRequestAttributeId".update.run) >= 0
    )
  }

  /** Direct query used by OpenCorridorSettlement.hasPromiseEvidence. */
  def existsByNameAndTransactionRequestIdSync(name: String, transactionRequestId: String): Boolean =
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM transactionrequestattribute WHERE name = $name AND transactionrequestid = $transactionRequestId"
        .query[Long].unique) > 0

  /** Direct query used by OpenCorridorSettlement.getSettlementStatus (coveredTrIds). */
  def transactionRequestIdsByNameAndValueSync(name: String, value: String): List[String] =
    DoobieUtil.runQuery(
      sql"SELECT DISTINCT transactionrequestid FROM transactionrequestattribute WHERE name = $name AND value = $value"
        .query[String].to[List])
}
