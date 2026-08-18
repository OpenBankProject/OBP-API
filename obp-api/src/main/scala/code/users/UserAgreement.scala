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
   * The agreement of one type for one user that `getLastUserAgreement` resolves to.
   *
   * NOT simply "the newest". The date column is DATE precision — no time of day — so two
   * acceptances on the same day tie. Mapper broke that tie with a STABLE sort over rows in
   * insertion order, which means the OLDEST of the tied rows wins, despite the method's name.
   * `id ASC` reproduces that exactly; without it SQL would be free to return either row.
   *
   * That is a latent defect (re-accepting an agreement on the same day keeps reporting the
   * superseded text) but it is pre-existing, and correcting it here would be a behaviour change
   * smuggled in under a storage swap. Preserved verbatim; see UserAgreementProviderTest.
   */
  def newestByUserIdAndType(userId: String, agreementType: String): Box[UserAgreement] =
    query(fr"WHERE userid = $userId AND agreementtype = $agreementType ORDER BY date_c DESC, id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => net.liftweb.common.Empty
      }

  /** Every agreement for a set of users, for the batched getUsers path. */
  def findAllByUserIds(userIds: List[String]): List[UserAgreement] =
    if (userIds.isEmpty) Nil
    else {
      val inFrag = Fragments.in(fr"userid", cats.data.NonEmptyList.fromListUnsafe(userIds.distinct))
      query(fr"WHERE " ++ inFrag)
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
