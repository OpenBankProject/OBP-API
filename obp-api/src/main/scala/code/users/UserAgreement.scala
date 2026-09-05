package code.users

import java.util.Date
import java.util.UUID.randomUUID

import code.api.util.{DoobieUtil, HashUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Full}

/**
 * One agreement a user accepted.
 *
 * Rows are a history rather than current state: createUserAgreement always inserts, and reads
 * take the most recent per (userId, agreementType). Preserved as-is.
 *
 * `agreementHash` is derived - a SHA-256 of agreementText that the Mapper entity recomputed in a
 * beforeSave hook on every write, so a caller could not supply a hash that disagreed with the
 * text. insert() computes it the same way for the same reason.
 *
 * `userInvitationId` returning the AGREEMENT id is not a typo introduced here: that is the trait
 * method name in UserAgreementTrait, and callers rely on it.
 */
case class UserAgreement(
  userAgreementId: String,
  userId: String,
  agreementType: String,
  agreementText: String,
  agreementHash: String,
  date: Date
) extends UserAgreementTrait {
  override def userInvitationId: String = userAgreementId
}

object UserAgreement {

  // Schemifier renames `date` to date_c because DATE is a reserved word.
  private val selectColumns =
    fr"SELECT useragreementid, userid, agreementtype, agreementtext, agreementhash, date_c FROM useragreement"

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[java.sql.Date])

  private def fromRow(row: Row): UserAgreement = row match {
    case (userAgreementId, userId, agreementType, agreementText, agreementHash, date) =>
      UserAgreement(userAgreementId.orNull, userId.orNull, agreementType.orNull,
        agreementText.orNull, agreementHash.orNull, date.orNull)
  }

  private def query(condition: Fragment): List[UserAgreement] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(userId: String, agreementType: String, agreementText: String): UserAgreement = {
    val newId = randomUUID().toString
    // Derived, never taken from the caller — mirrors the entity's beforeSave hook.
    val hash = HashUtil.Sha256Hash(agreementText)
    val date = new java.sql.Date(System.currentTimeMillis())
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO useragreement
            (useragreementid, userid, agreementtype, agreementtext, agreementhash, date_c, createdat, updatedat)
            VALUES ($newId, $userId, $agreementType, $agreementText, $hash, $date, $now, $now)"""
        .update.run)
    UserAgreement(newId, userId, agreementType, agreementText, hash, date)
  }

  /**
   * The newest agreement of one type for one user, which is what `getLastUserAgreement` returns.
   *
   * The date column is DATE precision — no time of day — so two acceptances on the same day tie
   * on date alone and the tie has to be broken by something else. Mapper broke it with a STABLE
   * sort over rows in insertion order, which handed back the OLDEST of the tied rows despite the
   * method's name: an agreement re-accepted the same day kept reporting the superseded text. The
   * identity column breaks it here instead, and it descends, so the row written last wins.
   *
   * `findAllByUserIds` orders the same way for the same reason — see the note there.
   */
  def newestByUserIdAndType(userId: String, agreementType: String): Box[UserAgreement] =
    query(fr"WHERE userid = $userId AND agreementtype = $agreementType ORDER BY date_c DESC, id DESC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => net.liftweb.common.Empty
      }

  /**
   * Every agreement for a set of users, for the batched getUsers path.
   *
   * Ordered newest-first, and that ordering is load-bearing rather than cosmetic: the caller
   * picks each type's latest with a stable `sortBy(date)`, and DATE precision means same-day
   * rows tie there. A stable sort keeps the order it was given, so whichever row this query
   * returns first is the one that path reports. Without `id DESC` it would report the oldest of
   * a same-day pair while newestByUserIdAndType reported the newest, and a user's agreement text
   * would depend on which endpoint asked.
   */
  def findAllByUserIds(userIds: List[String]): List[UserAgreement] =
    if (userIds.isEmpty) Nil
    else {
      val inFrag = Fragments.in(fr"userid", cats.data.NonEmptyList.fromListUnsafe(userIds.distinct))
      query(fr"WHERE " ++ inFrag ++ fr"ORDER BY date_c DESC, id DESC")
    }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM useragreement".update.run)
    ()
  }
}

object MappedUserAgreementProvider extends UserAgreementProvider {
  override def createUserAgreement(userId: String, agreementType: String, agreementText: String): Box[UserAgreement] =
    Full(UserAgreement.insert(userId, agreementType, agreementText))

  override def getLastUserAgreement(userId: String, agreementType: String): Box[UserAgreement] =
    UserAgreement.newestByUserIdAndType(userId, agreementType)
}
