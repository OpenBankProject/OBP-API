package code.transactionattribute

import code.api.attributedefinition.AttributeDefinition
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.enums.{AttributeCategory, TransactionAttributeType}
import com.openbankproject.commons.model.{BankId, TransactionAttribute, TransactionId, ViewId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.Fragments
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One transaction-attribute row, standing in for the Lift entity in return types. */
case class TransactionAttributeRow(
  bankId: BankId,
  transactionId: TransactionId,
  transactionAttributeId: String,
  attributeType: TransactionAttributeType.Value,
  name: String,
  value: String
) extends TransactionAttribute

/**
 * Doobie implementation of the transaction-attribute store, replacing the Lift
 * MappedTransactionAttribute entity.
 *
 * There is no unique index on this table: only plain indexes on mTransactionId and
 * mTransactionAttributeId. createOrUpdateTransactionAttribute finds by transactionAttributeId to
 * decide update vs create, matching the Mapper version, but nothing in the schema stops two rows
 * sharing an id.
 *
 * getTransactionAttributesCanBeSeenOnView / getTransactionsAttributesCanBeSeenOnView still read
 * AttributeDefinition (a separate, not-yet-migrated Mapper entity) directly and join in plain
 * Scala, exactly as the Mapper version did - only the TransactionAttribute-table read moved to
 * Doobie.
 *
 * getTransactionIdsByAttributeNameValues reproduces the Mapper version's
 * BySql(sqlParametersFilter, ...) row-level filter: OR-across-attributes semantics.
 */
object DoobieTransactionAttributeProvider extends TransactionAttributeProvider {

  private def rowOf(r: (String, String, String, String, String, String)): TransactionAttributeRow =
    TransactionAttributeRow(
      bankId = BankId(r._1),
      transactionId = TransactionId(r._2),
      transactionAttributeId = r._3,
      attributeType = TransactionAttributeType.withName(r._4),
      name = r._5,
      value = r._6
    )

  private val selectCols: Fragment =
    fr"SELECT mbankid, mtransactionid, mtransactionattributeid, mtype, mname, mvalue FROM mappedtransactionattribute"

  override def getTransactionAttributesFromProvider(transactionId: TransactionId): Future[Box[List[TransactionAttribute]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mtransactionid = ${transactionId.value}")
          .query[(String, String, String, String, String, String)].to[List]
      ).map(rowOf)
    }

  override def getTransactionAttributes(bankId: BankId, transactionId: TransactionId): Future[Box[List[TransactionAttribute]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankid = ${bankId.value} AND mtransactionid = ${transactionId.value}")
          .query[(String, String, String, String, String, String)].to[List]
      ).map(rowOf)
    }

  override def getTransactionAttributesCanBeSeenOnView(
    bankId: BankId,
    transactionId: TransactionId,
    viewId: ViewId
  ): Future[Box[List[TransactionAttribute]]] = Future {
    val attributeDefinitions = AttributeDefinition
      .findAllByBankIdAndCategory(bankId.value, AttributeCategory.Transaction.toString)
      .filter(_.canBeSeenOnViews.exists(_ == viewId.value))
    val transactionAttributes = DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${bankId.value} AND mtransactionid = ${transactionId.value}")
        .query[(String, String, String, String, String, String)].to[List]
    ).map(rowOf)
    val filteredTransactionAttributes = for {
      definition <- attributeDefinitions
      attribute <- transactionAttributes
      if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
    } yield attribute
    Full(filteredTransactionAttributes)
  }

  override def getTransactionsAttributesCanBeSeenOnView(
    bankId: BankId,
    transactionIds: List[TransactionId],
    viewId: ViewId
  ): Future[Box[List[TransactionAttribute]]] = Future {
    if (transactionIds.isEmpty) {
      Full(Nil)
    } else {
      val attributeDefinitions = AttributeDefinition
        .findAllByBankIdAndCategory(bankId.value, AttributeCategory.Transaction.toString)
        .filter(_.canBeSeenOnViews.exists(_ == viewId.value))
      val inFrag = Fragments.in(fr"mtransactionid", cats.data.NonEmptyList.fromListUnsafe(transactionIds.map(_.value)))
      val transactionsAttributes = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE " ++ inFrag)
          .query[(String, String, String, String, String, String)].to[List]
      ).map(rowOf).filter { item =>
        transactionIds.exists(acc => (bankId.value, acc.value) == (item.bankId.value, item.transactionId.value))
      }
      val filteredTransactionAttributes = for {
        definition <- attributeDefinitions
        attribute <- transactionsAttributes
        if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
      } yield attribute
      Full(filteredTransactionAttributes)
    }
  }

  override def getTransactionAttributeById(transactionAttributeId: String): Future[Box[TransactionAttribute]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mtransactionattributeid = $transactionAttributeId LIMIT 1")
        .query[(String, String, String, String, String, String)].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def getTransactionIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] = Future {
    Full {
      if (params.isEmpty) {
        DoobieUtil.runQuery(
          sql"SELECT mtransactionid FROM mappedtransactionattribute WHERE mbankid = ${bankId.value}".query[String].to[List])
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
          (fr"SELECT mtransactionid FROM mappedtransactionattribute WHERE mbankid = ${bankId.value} AND (" ++ filterFrag ++ fr")")
            .query[String].to[List])
      }
    }
  }

  override def createOrUpdateTransactionAttribute(
    bankId: BankId,
    transactionId: TransactionId,
    transactionAttributeId: Option[String],
    name: String,
    attributeType: TransactionAttributeType.Value,
    value: String
  ): Future[Box[TransactionAttribute]] = {
    transactionAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE mtransactionattributeid = $id LIMIT 1")
            .query[(String, String, String, String, String, String)].option
        ) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runUpdate(
                sql"""UPDATE mappedtransactionattribute
                      SET mbankid = ${bankId.value}, mtransactionid = ${transactionId.value}, mname = $name, mtype = ${attributeType.toString}, mvalue = $value
                      WHERE mtransactionattributeid = $id"""
                  .update.run)
              TransactionAttributeRow(bankId, transactionId, id, attributeType, name, value)
            }
          case None => Empty
        }
      }
      case None => Future {
        val id = APIUtil.generateUUID()
        Full {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedtransactionattribute (mbankid, mtransactionid, mtransactionattributeid, mname, mtype, mvalue)
                  VALUES (${bankId.value}, ${transactionId.value}, $id, $name, ${attributeType.toString}, $value)"""
              .update.run)
          TransactionAttributeRow(bankId, transactionId, id, attributeType, name, value)
        }
      }
    }
  }

  override def createTransactionAttributes(
    bankId: BankId,
    transactionId: TransactionId,
    transactionAttributes: List[TransactionAttribute]
  ): Future[Box[List[TransactionAttribute]]] =
    Future {
      tryo {
        transactionAttributes.map { transactionAttribute =>
          val id = APIUtil.generateUUID()
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedtransactionattribute (mbankid, mtransactionid, mtransactionattributeid, mname, mtype, mvalue)
                  VALUES (${bankId.value}, ${transactionId.value}, $id, ${transactionAttribute.name}, ${transactionAttribute.attributeType.toString}, ${transactionAttribute.value})"""
              .update.run)
          TransactionAttributeRow(bankId, transactionId, id, transactionAttribute.attributeType, transactionAttribute.name, transactionAttribute.value)
        }
      }
    }

  override def deleteTransactionAttribute(transactionAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedtransactionattribute WHERE mtransactionattributeid = $transactionAttributeId".update.run) >= 0
    )
  }

  /** Direct query used by deletion.DeleteTransactionCascade.delete. */
  def deleteTransactionAttributesByBankAndTransaction(bankId: String, transactionId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedtransactionattribute WHERE mbankid = $bankId AND mtransactionid = $transactionId".update.run)
    true
  }

  /** Direct query used by test helper V400ServerSetup.checkAllTransactionRelatedData. */
  def countAttributesSync(bankId: String, transactionId: String): Long =
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM mappedtransactionattribute WHERE mbankid = $bankId AND mtransactionid = $transactionId"
        .query[Long].unique)
}
