package code.metadata.narrative

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId}
import doobie._
import doobie.implicits._
import net.liftweb.util.Helpers.tryo

object DoobieNarratives extends Narrative {

  private case class NarrativeRow(
    id: Long,
    account: Option[String],
    narrative: Option[String],
    bank: Option[String],
    transaction: Option[String]
  )

  private val selectCols: Fragment =
    fr"SELECT id, account, narrative, bank, transaction_c FROM mappednarrative"

  private def findRow(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Option[NarrativeRow] = {
    val q = (selectCols ++ fr"""WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value} LIMIT 1""")
      .query[NarrativeRow].option
    DoobieUtil.runQuery(q)
  }

  override def getNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(): String =
    findRow(bankId, accountId, transactionId).flatMap(_.narrative).getOrElse("")

  override def setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(narrative: String): Boolean = {
    if (narrative.isEmpty) {
      tryo {
        DoobieUtil.runUpdate(
          sql"""DELETE FROM mappednarrative WHERE bank = ${bankId.value} AND account = ${accountId.value}
                AND transaction_c = ${transactionId.value}""".update.run
        )
      }.isDefined
    } else {
      val existing = findRow(bankId, accountId, transactionId)
      val result = tryo {
        existing match {
          case Some(row) =>
            DoobieUtil.runUpdate(
              sql"""UPDATE mappednarrative SET narrative = $narrative
                    WHERE id = ${row.id}""".update.run
            )
          case None =>
            DoobieUtil.runUpdate(
              sql"""INSERT INTO mappednarrative (account, narrative, bank, transaction_c)
                    VALUES (${accountId.value}, $narrative, ${bankId.value}, ${transactionId.value})"""
                .update.run
            )
        }
      }
      result.isDefined
    }
  }

  override def bulkDeleteNarrativeOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"""DELETE FROM mappednarrative WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value}""".update.run
      )
    }.isDefined

  override def bulkDeleteNarratives(bankId: BankId, accountId: AccountId): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappednarrative WHERE bank = ${bankId.value} AND account = ${accountId.value}".update.run
      )
    }.isDefined

  def countByBankAccountTransaction(bankId: String, accountId: String, transactionId: String): Int =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM mappednarrative
            WHERE bank = $bankId AND account = $accountId AND transaction_c = $transactionId"""
        .query[Int].unique
    )
}
