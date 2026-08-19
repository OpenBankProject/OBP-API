package code.accountholders

import code.api.util.DoobieUtil
import code.model.dataAccess.ResourceUser
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, User}
import doobie._
import doobie.implicits._
import net.liftweb.common._
import net.liftweb.util.Helpers.tryo

/**
  * the link userId <--> bankId + accountId
  *
  * `userKey` is RESOURCEUSER's numeric primary key, not the public user_id.
  *
  * `source` genuinely holds NULL, and getAccountsHeldByUser distinguishes three cases on it: no
  * filter, `IS NULL`, and an equality match. A row storing "" instead of NULL would be invisible
  * to the IS NULL branch, so it is bound as Option throughout.
  */
case class MapperAccountHolders(
  userKey: Long,
  accountBankPermalink: String,
  accountPermalink: String,
  source: Option[String]
)

object MapperAccountHolders extends AccountHolders with MdcLoggable {

  // NOTE: !!! Uses a DIFFERENT TABLE NAME PREFIX TO ALL OTHERS i.e. MAPPER not MAPPED !!!!!

  private val selectColumns =
    fr"SELECT user_c, accountbankpermalink, accountpermalink, source FROM mapperaccountholders"

  private type Row = (Option[Long], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): MapperAccountHolders = row match {
    case (userKey, accountBankPermalink, accountPermalink, source) =>
      MapperAccountHolders(userKey.getOrElse(0L), accountBankPermalink.orNull,
        accountPermalink.orNull, source)
  }

  private def query(condition: Fragment): List[MapperAccountHolders] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /**
   * The bank and account ids are bound as Option, not as bare Strings, because callers legitimately
   * pass null: canRevokeOwnerAccess looks holders up by a ViewDefinition's bankId/accountId, and a
   * SYSTEM view carries none. Lift rendered `By(field, null)` as `field = NULL`, which matches
   * nothing and quietly returns an empty set; a non-nullable Put throws "oops, null" instead, and
   * the resulting 500 carries no OBP frame to point at the cause.
   */
  def find(userKey: Long, bankId: String, accountId: String): Box[MapperAccountHolders] =
    query(fr"""WHERE user_c = $userKey AND accountbankpermalink = ${Option(bankId)}
               AND accountpermalink = ${Option(accountId)} ORDER BY id ASC LIMIT 1""")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findAll(): List[MapperAccountHolders] = query(fr"ORDER BY id ASC")

  def insert(userKey: Long, bankId: String, accountId: String,
             source: Option[String]): MapperAccountHolders = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mapperaccountholders
            (user_c, accountbankpermalink, accountpermalink, source)
            VALUES ($userKey, ${Option(bankId)}, ${Option(accountId)}, $source)"""
        .update.run)
    MapperAccountHolders(userKey, bankId, accountId, source)
  }

  def count(bankId: String, accountId: String): Long =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM mapperaccountholders
            WHERE accountbankpermalink = ${Option(bankId)}
              AND accountpermalink = ${Option(accountId)}"""
        .query[Long].unique)

  def delete(userKey: Long, bankId: String, accountId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"""DELETE FROM mapperaccountholders
            WHERE user_c = $userKey AND accountbankpermalink = ${Option(bankId)}
              AND accountpermalink = ${Option(accountId)}"""
        .update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mapperaccountholders".update.run)
    ()
  }

  //Note, this method, will not check the existing of bankAccount, any value of BankIdAccountId
  //Can create the MapperAccountHolders.
  def getOrCreateAccountHolder(user: User, bankIdAccountId: BankIdAccountId,
                               source: Option[String] = None): Box[MapperAccountHolders] = {
    val userKey = user.userPrimaryKey.value
    find(userKey, bankIdAccountId.bankId.value, bankIdAccountId.accountId.value) match {
      case Full(_) =>
        logger.debug(
          s"getOrCreateAccountHolder --> the accountHolder has been existing in server !"
        )
        find(userKey, bankIdAccountId.bankId.value, bankIdAccountId.accountId.value)
      case Empty =>
        // The unique index is what makes this safe: a concurrent duplicate insert is rejected and
        // the loser re-reads the committed row rather than creating a second holder.
        tryo {
          insert(userKey, bankIdAccountId.bankId.value, bankIdAccountId.accountId.value, source)
        } match {
          case Full(holder) =>
            logger.debug(s"getOrCreateAccountHolder--> create account holder: $holder")
            Full(holder)
          case Failure(_, _, _) =>
            find(userKey, bankIdAccountId.bankId.value, bankIdAccountId.accountId.value)
          case other => other
        }
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x, y, z, q) => ParamFailure(x, y, z, q)
    }
  }

  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] = {
    val accountHolders =
      query(fr"""WHERE accountbankpermalink = ${Option(bankId.value)}
                 AND accountpermalink = ${Option(accountId.value)} ORDER BY id ASC""")

    //accountHolders --> user
    accountHolders.flatMap { accHolder =>
      ResourceUser.findByPrimaryKey(accHolder.userKey)
    }.toSet
  }

  def getAccountsHeld(bankId: BankId, user: User): Set[BankIdAccountId] =
    transformHolderToAccount(
      query(fr"""WHERE accountbankpermalink = ${Option(bankId.value)}
                 AND user_c = ${user.userPrimaryKey.value} ORDER BY id ASC"""))

  def getAccountsHeldByUser(user: User, source: Option[String] = None): Set[BankIdAccountId] = {
    val userKey = user.userPrimaryKey.value
    // Three distinct cases, preserved: no source filter at all; the source column must be NULL;
    // or an exact match. The middle case is why the column stays nullable.
    val accountHolders =
      if (source.isEmpty) {
        query(fr"WHERE user_c = $userKey ORDER BY id ASC")
      } else if (source.equals(Some("")) || source.equals(Some(null))) {
        query(fr"WHERE user_c = $userKey AND source IS NULL ORDER BY id ASC")
      } else {
        query(fr"WHERE user_c = $userKey AND source = ${Option(source.get)} ORDER BY id ASC")
      }
    transformHolderToAccount(accountHolders)
  }

  private def transformHolderToAccount(accountHolders: List[MapperAccountHolders]) = {
    //accountHolders --> BankIdAccountIds
    accountHolders.map { accHolder =>
      BankIdAccountId(BankId(accHolder.accountBankPermalink), AccountId(accHolder.accountPermalink))
    }.toSet
  }

  def bulkDeleteAllAccountHolders(): Box[Boolean] = {
    deleteAll()
    Full(true)
  }

  def deleteAccountHolder(user: User, bankIdAccountId: BankIdAccountId): Box[Boolean] = {
    val userKey = user.userPrimaryKey.value
    find(userKey, bankIdAccountId.bankId.value, bankIdAccountId.accountId.value)
      .map(_ => delete(userKey, bankIdAccountId.bankId.value, bankIdAccountId.accountId.value))
  }
}
