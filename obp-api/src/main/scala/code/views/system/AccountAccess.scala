package code.views.system

import code.api.Constant.ALL_CONSUMERS
import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{AccountId, BankId, UserPrimaryKey, View, ViewId}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

/*
This stores the link between A User and a View
A User can't use a View unless it is listed here.
 */
/**
 * One (user, view) grant, scoped to a consumer.
 *
 * All five columns of the unique index matter: revokeAccess matches on
 * (bank, account, view, user) and cannot tell two applications' grants apart, while the
 * per-consumer revokes match on (bank, account, view, consumer) and cannot tell two users apart.
 * Undoing exactly one consent's grant needs the full tuple.
 *
 * `consumerId` defaults to ALL_CONSUMERS, meaning any consumer may use the grant.
 *
 * `userPrimaryKey` is RESOURCEUSER's numeric key, not the public user_id.
 */
case class AccountAccess(
  userPrimaryKey: Long,
  bankId: String,
  accountId: String,
  viewId: String,
  consumerId: String
)

object AccountAccess {

  // view_fk is deliberately absent: it is deprecated in favour of bank_id/account_id/view_id and no
  // code path writes it.
  private val selectColumns =
    fr"SELECT user_fk, bank_id, account_id, view_id, consumer_id FROM accountaccess"

  private type Row = (Option[Long], Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): AccountAccess = row match {
    case (userPrimaryKey, bankId, accountId, viewId, consumerId) =>
      AccountAccess(userPrimaryKey.getOrElse(0L), bankId.orNull, accountId.orNull, viewId.orNull,
        consumerId.orNull)
  }

  private def query(condition: Fragment): List[AccountAccess] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[AccountAccess] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  // Callers pass ids taken off rows that may legitimately hold null (a system view has no bank or
  // account), so every string is bound as Option — see CLAUDE.md's null-binding note.
  private def opt(v: String): Option[String] = Option(v)

  def findByUniqueIndex(bankId: BankId, accountId: AccountId, viewId: ViewId,
                        userPrimaryKey: UserPrimaryKey, consumerId: String): Box[AccountAccess] =
    one(fr"""WHERE bank_id = ${opt(bankId.value)} AND account_id = ${opt(accountId.value)}
             AND view_id = ${opt(viewId.value)} AND user_fk = ${userPrimaryKey.value}
             AND consumer_id = ${opt(consumerId)}""")

  def findAllBySystemViewId(systemViewId: ViewId): List[AccountAccess] =
    query(fr"WHERE view_id = ${opt(systemViewId.value)} ORDER BY id ASC")

  def findAllByView(view: View): List[AccountAccess] =
    if (view.isSystem) {
      findAllBySystemViewId(view.viewId)
    } else {
      findAllByBankIdAccountIdViewId(view.bankId, view.accountId, view.viewId)
    }

  def findAllByUserPrimaryKey(userPrimaryKey: UserPrimaryKey): List[AccountAccess] =
    query(fr"WHERE user_fk = ${userPrimaryKey.value} ORDER BY id ASC")

  def findAllByUserPrimaryKeyAndConsumer(userPrimaryKey: UserPrimaryKey,
                                         consumerId: String): List[AccountAccess] =
    query(fr"""WHERE user_fk = ${userPrimaryKey.value} AND consumer_id = ${opt(consumerId)}
               ORDER BY id ASC""")

  def findAllByBankId(bankId: BankId): List[AccountAccess] =
    query(fr"WHERE bank_id = ${opt(bankId.value)} ORDER BY id ASC")

  def findAllByBankIdAccountId(bankId: BankId, accountId: AccountId): List[AccountAccess] =
    query(fr"""WHERE bank_id = ${opt(bankId.value)} AND account_id = ${opt(accountId.value)}
               ORDER BY id ASC""")

  def findAllByBankIdAccountIdViewId(bankId: BankId, accountId: AccountId,
                                     viewId: ViewId): List[AccountAccess] =
    query(fr"""WHERE bank_id = ${opt(bankId.value)} AND account_id = ${opt(accountId.value)}
               AND view_id = ${opt(viewId.value)} ORDER BY id ASC""")

  def findByBankIdAccountIdUserPrimaryKey(bankId: BankId, accountId: AccountId,
                                          userPrimaryKey: UserPrimaryKey): List[AccountAccess] =
    query(fr"""WHERE bank_id = ${opt(bankId.value)} AND account_id = ${opt(accountId.value)}
               AND user_fk = ${userPrimaryKey.value} ORDER BY id ASC""")

  def findByBankIdAccountIdViewIdUserPrimaryKey(bankId: BankId, accountId: AccountId, viewId: ViewId,
                                                userPrimaryKey: UserPrimaryKey): Box[AccountAccess] =
    one(fr"""WHERE bank_id = ${opt(bankId.value)} AND account_id = ${opt(accountId.value)}
             AND view_id = ${opt(viewId.value)} AND user_fk = ${userPrimaryKey.value}""")

  def findByBankIdAccountIdViewIdConsumerId(bankId: BankId, accountId: AccountId, viewId: ViewId,
                                            consumerId: String): Box[AccountAccess] =
    one(fr"""WHERE bank_id = ${opt(bankId.value)} AND account_id = ${opt(accountId.value)}
             AND view_id = ${opt(viewId.value)} AND consumer_id = ${opt(consumerId)}""")

  def findByBankIdAccountIdUser(bankId: BankId, accountId: AccountId,
                                userPrimaryKey: UserPrimaryKey): Box[AccountAccess] =
    one(fr"""WHERE bank_id = ${opt(bankId.value)} AND account_id = ${opt(accountId.value)}
             AND user_fk = ${userPrimaryKey.value}""")

  /** Public system views are matched on view id alone; public custom views on all three. */
  def findAllByViewIds(viewIds: List[String]): List[AccountAccess] =
    // Mapper's ByList with an empty list rendered "0 = 1", i.e. no rows — not "no filter".
    if (viewIds.isEmpty) Nil
    else {
      val in = Fragments.in(fr"view_id", cats.data.NonEmptyList.fromListUnsafe(viewIds.distinct))
      query(fr"WHERE " ++ in ++ fr"ORDER BY id ASC")
    }

  def findAllByBankAccountViewIdLists(bankIds: List[String], accountIds: List[String],
                                      viewIds: List[String]): List[AccountAccess] =
    if (bankIds.isEmpty || accountIds.isEmpty || viewIds.isEmpty) Nil
    else {
      val inBank = Fragments.in(fr"bank_id", cats.data.NonEmptyList.fromListUnsafe(bankIds.distinct))
      val inAccount = Fragments.in(fr"account_id", cats.data.NonEmptyList.fromListUnsafe(accountIds.distinct))
      val inView = Fragments.in(fr"view_id", cats.data.NonEmptyList.fromListUnsafe(viewIds.distinct))
      query(fr"WHERE " ++ inBank ++ fr"AND " ++ inAccount ++ fr"AND " ++ inView ++
        fr"ORDER BY id ASC")
    }

  def insert(userPrimaryKey: Long, bankId: String, accountId: String, viewId: String,
             consumerId: String = ALL_CONSUMERS): AccountAccess = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO accountaccess
            (user_fk, bank_id, account_id, view_id, consumer_id, createdat, updatedat)
            VALUES ($userPrimaryKey, ${opt(bankId)}, ${opt(accountId)}, ${opt(viewId)},
             ${opt(consumerId)}, $now, $now)"""
        .update.run)
    AccountAccess(userPrimaryKey, bankId, accountId, viewId, consumerId)
  }

  /** Deletes exactly this row, addressed by the five columns of the unique index. */
  def deleteRow(row: AccountAccess): Boolean =
    DoobieUtil.runUpdate(
      sql"""DELETE FROM accountaccess
            WHERE bank_id = ${opt(row.bankId)} AND account_id = ${opt(row.accountId)}
              AND view_id = ${opt(row.viewId)} AND user_fk = ${row.userPrimaryKey}
              AND consumer_id = ${opt(row.consumerId)}"""
        .update.run) > 0

  def deleteByBankIdAccountId(bankId: BankId, accountId: AccountId): Boolean = {
    DoobieUtil.runUpdate(
      sql"""DELETE FROM accountaccess
            WHERE bank_id = ${opt(bankId.value)} AND account_id = ${opt(accountId.value)}"""
        .update.run)
    true
  }

  def deleteByBankIdAccountIdViewId(bankId: BankId, accountId: AccountId, viewId: ViewId): Boolean = {
    DoobieUtil.runUpdate(
      sql"""DELETE FROM accountaccess
            WHERE bank_id = ${opt(bankId.value)} AND account_id = ${opt(accountId.value)}
              AND view_id = ${opt(viewId.value)}"""
        .update.run)
    true
  }

  /** Every grant on a view id, regardless of bank/account — the system-view shape. */
  def deleteByViewId(viewId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM accountaccess WHERE view_id = ${opt(viewId)}".update.run)
    true
  }

  def findAllByAccountId(accountId: String): List[AccountAccess] =
    query(fr"WHERE account_id = ${opt(accountId)} ORDER BY id ASC")

  def count(bankId: BankId, accountId: AccountId, viewId: ViewId): Long =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM accountaccess
            WHERE bank_id = ${opt(bankId.value)} AND account_id = ${opt(accountId.value)}
              AND view_id = ${opt(viewId.value)}"""
        .query[Long].unique)

  def findAll(): List[AccountAccess] = query(fr"ORDER BY id ASC")

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM accountaccess".update.run)
    ()
  }
}
