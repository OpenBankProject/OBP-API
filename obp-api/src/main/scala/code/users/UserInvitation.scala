package code.users

import java.util.UUID.randomUUID

import code.api.util.{DoobieUtil, SecureRandomUtil}
import com.openbankproject.commons.model.BankId
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo

/**
 * An invitation a bank issues to a prospective user.
 *
 * `secretKey` is the credential on the invitation link: getUserInvitationBySecretLink resolves an
 * invitation from it alone, with no bank scoping, so it is generated with a CSPRNG on insert and
 * never accepted from a caller.
 */
case class UserInvitation(
  userInvitationId: String,
  bankId: String,
  firstName: String,
  lastName: String,
  email: String,
  company: String,
  country: String,
  status: String,
  purpose: String,
  secretKey: Long,
  /** From the CreatedUpdated mixin. Load-bearing: the claim endpoint expires an
    * invitation 24 hours after this instant, so it is carried on the row rather
    * than dropped as bookkeeping. */
  createdAt: java.util.Date
) extends UserInvitationTrait

object UserInvitation {

  private val selectColumns =
    fr"""SELECT userinvitationid, bankid, firstname, lastname, email, company, country,
                status, purpose, secretkey, createdat
         FROM userinvitation"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String], Option[Long],
    Option[java.sql.Timestamp])

  private def fromRow(row: Row): UserInvitation = row match {
    case (userInvitationId, bankId, firstName, lastName, email, company, country, status, purpose, secretKey, createdAt) =>
      // MappedLong read a NULL as the field's defaultValue, which here was a fresh
      // SecureRandomUtil.csprng.nextLong(). Reproducing that keeps the read from failing and
      // keeps the row unusable as an invitation link, which is what a NULL secret key means:
      // findBySecretKey looks the key up by value, and a fresh random never matches.
      UserInvitation(userInvitationId.orNull, bankId.orNull, firstName.orNull, lastName.orNull,
        email.orNull, company.orNull, country.orNull, status.orNull, purpose.orNull,
        secretKey.getOrElse(SecureRandomUtil.csprng.nextLong()), createdAt.orNull)
  }

  private def query(condition: Fragment): List[UserInvitation] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[UserInvitation] =
    query(condition ++ fr"LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(bankId: String, firstName: String, lastName: String, email: String,
             company: String, country: String, purpose: String): UserInvitation = {
    val newId = randomUUID().toString
    // CSPRNG, matching the entity's SecretKey defaultValue — this is the link credential.
    val secretKey = SecureRandomUtil.csprng.nextLong()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO userinvitation
            (userinvitationid, bankid, firstname, lastname, email, company, country, status,
             purpose, secretkey, createdat, updatedat)
            VALUES ($newId, $bankId, $firstName, $lastName, $email, $company, $country, 'CREATED',
             $purpose, $secretKey, $now, $now)"""
        .update.run)
    UserInvitation(newId, bankId, firstName, lastName, email, company, country, "CREATED", purpose, secretKey, now)
  }

  def findBySecretKey(secretKey: Long): Box[UserInvitation] =
    one(fr"WHERE secretkey = $secretKey")

  def findByBankIdAndSecretKey(bankId: String, secretKey: Long): Box[UserInvitation] =
    one(fr"WHERE bankid = $bankId AND secretkey = $secretKey")

  def findByUserInvitationId(userInvitationId: String): Box[UserInvitation] =
    one(fr"WHERE userinvitationid = $userInvitationId")

  def findAllByBankId(bankId: String): List[UserInvitation] =
    query(fr"WHERE bankid = $bankId")

  def updateStatus(userInvitationId: String, status: String): Boolean = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"UPDATE userinvitation SET status = $status, updatedat = $now WHERE userinvitationid = $userInvitationId"
        .update.run) > 0
  }

  /**
   * Overwrite the personal fields with random noise and mark the row DELETED.
   *
   * Each replacement keeps the ORIGINAL field's length. That is deliberate in the Mapper version
   * and preserved here: a fixed-width scramble would leak nothing, but changing the widths would
   * change what a stored row reveals, and this is the codebase's erasure path for personal data.
   */
  def scramble(userInvitationId: String): Boolean =
    findByUserInvitationId(userInvitationId) match {
      case Full(existing) =>
        val now = new java.sql.Timestamp(System.currentTimeMillis())
        DoobieUtil.runUpdate(
          sql"""UPDATE userinvitation SET
                  email = ${Helpers.randomString(10) + "@example.com"},
                  firstname = ${Helpers.randomString(existing.firstName.length)},
                  lastname = ${Helpers.randomString(existing.lastName.length)},
                  company = ${Helpers.randomString(existing.company.length)},
                  country = ${Helpers.randomString(existing.country.length)},
                  purpose = ${Helpers.randomString(existing.purpose.length)},
                  status = 'DELETED', updatedat = $now
                WHERE userinvitationid = $userInvitationId"""
            .update.run) > 0
      case _ => false
    }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM userinvitation".update.run)
    ()
  }
}

object MappedUserInvitationProvider extends UserInvitationProvider {
  override def createUserInvitation(bankId: BankId, firstName: String, lastName: String, email: String,
                                    company: String, country: String, purpose: String): Box[UserInvitation] = tryo {
    UserInvitation.insert(bankId.value, firstName, lastName, email, company, country, purpose)
  }

  override def getUserInvitationBySecretLink(secretLink: Long): Box[UserInvitation] =
    UserInvitation.findBySecretKey(secretLink)

  override def updateStatusOfUserInvitation(userInvitationId: String, status: String): Box[Boolean] = tryo {
    UserInvitation.updateStatus(userInvitationId, status)
  }

  override def scrambleUserInvitation(userInvitationId: String): Box[Boolean] = tryo {
    UserInvitation.scramble(userInvitationId)
  }

  override def getUserInvitation(bankId: BankId, secretLink: Long): Box[UserInvitation] =
    UserInvitation.findByBankIdAndSecretKey(bankId.value, secretLink)

  override def getUserInvitations(bankId: BankId): Box[List[UserInvitation]] = tryo {
    UserInvitation.findAllByBankId(bankId.value)
  }
}
