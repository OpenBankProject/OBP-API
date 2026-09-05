/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.model.dataAccess

import java.util.Date

import code.api.Constant
import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieQueries, DoobieUtil}
import com.openbankproject.commons.model.{User, UserPrimaryKey}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import scala.concurrent.duration._

/**
 * A user of the API - a person, or the pseudo-person a consent mints for itself.
 *
 * 1 AuthUser is used for authentication only (username, password, the login web flow).
 * 2 ResourceUser is what everything else hangs off: accounts, transactions, roles, views,
 *   account holders, customers, consumers and tokens all reference its `userId`.
 * 3 Signing up creates an AuthUser, whose save creates the matching ResourceUser; they share a
 *   username and email, and AUTHUSER.USER_C points at RESOURCEUSER.ID.
 *
 * The field names follow the `User` trait rather than the column names, because a user crosses the
 * connector boundary as `UserCommons` - a JSON round-trip that matches by field name.
 */
case class ResourceUser(
  id: Long = 0L,
  userId: String = "",
  emailAddress: String = "",
  name: String = "",
  provider: String = Constant.localIdentityProvider,
  idGivenByProvider: String = "",
  company: String = "",
  createdByConsentId: Option[String] = None,
  createdByUserInvitationId: Option[String] = None,
  isDeleted: Option[Boolean] = Some(false),
  lastMarketingAgreementSignedDate: Option[Date] = None,
  override val lastUsedLocale: Option[String] = None,
  override val isNaturalPerson: Boolean = true,
  override val principalUserIdOption: Option[String] = None,
  // Carried over from develop's Mapper entity. The User trait defaults all three to None, so
  // omitting them here would compile and silently disable the feature rather than fail.
  override val mobilePhoneNumber: Option[String] = None,
  override val mobilePhoneNumberIsValidated: Option[Boolean] = None,
  override val mobilePhoneNumberValidatedDate: Option[Date] = None
) extends User {

  def userPrimaryKey: UserPrimaryKey = UserPrimaryKey(id)

  def toCaseClass: ResourceUserCaseClass =
    ResourceUserCaseClass(
      emailAddress = emailAddress,
      idGivenByProvider = idGivenByProvider,
      resourceUserId = userPrimaryKey.value,
      userId = userId,
      name = name,
      provider = provider
    )
}

object ResourceUser {

  /** A new user: a generated user id, and the entity's field defaults for everything else. */
  def defaults: ResourceUser = ResourceUser(userId = APIUtil.generateUUID())

  /**
   * What MappedEmail's setFilter did on every set: null becomes "", the rest is lowercased and
   * trimmed. Applied where the entity used to assign the field.
   */
  def normalizeEmail(value: String): String =
    (if (value == null) "" else value).toLowerCase.trim

  def getDistinctProviders: List[String] = {
    val cacheKey = ("code.model.dataAccess.ResourceUser", "getDistinctProviders", List().mkString("_"))
    val cacheTTL = APIUtil.getPropsAsIntValue("getDistinctProviders.cache.ttl.seconds", 3600)
    Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cacheTTL.seconds) {
      DoobieQueries.getDistinctProviders
    }
  }

  private val selectColumns =
    fr"""SELECT id, userid_, email, name_, provider_, providerid, company, createdbyconsentid,
                createdbyuserinvitationid, isdeleted, lastmarketingagreementsigneddate,
                lastusedlocale, isnaturalperson, principaluserid,
                mobilephonenumber, mobilephonenumberisvalidated, mobilephonenumbervalidateddate
         FROM resourceuser"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[Boolean],
    Option[java.sql.Date], Option[String], Option[Boolean], Option[String],
    Option[String], Option[Boolean], Option[java.sql.Timestamp])

  /** A DATE comes back as java.sql.Date, which json4s serializes as {} unless it is converted. */
  private def readDate(value: Option[java.sql.Date]): Option[Date] =
    value.map(d => new Date(d.getTime))

  /** Mapper turned both null and "" into None on these three. */
  private def blankToNone(value: Option[String]): Option[String] =
    value.filter(_.nonEmpty)

  private def fromRow(row: Row): ResourceUser = row match {
    case (id, userId, email, name, provider, providerId, company, createdByConsentId,
          createdByUserInvitationId, isDeleted, signedDate, lastUsedLocale, isNaturalPerson,
          principalUserId, mobilePhoneNumber, mobilePhoneNumberIsValidated,
          mobilePhoneNumberValidatedDate) =>
      ResourceUser(
        id = id,
        userId = userId.orNull,
        // emailAddress read null as "", which is what the entity's accessor did.
        emailAddress = email.getOrElse(""),
        name = name.orNull,
        provider = provider.orNull,
        idGivenByProvider = providerId.orNull,
        company = company.orNull,
        createdByConsentId = blankToNone(createdByConsentId),
        createdByUserInvitationId = blankToNone(createdByUserInvitationId),
        isDeleted = isDeleted,
        lastMarketingAgreementSignedDate = readDate(signedDate),
        lastUsedLocale = lastUsedLocale,
        // MappedBoolean read a NULL as false whatever the field declared - `defaultValue = true`
        // only seeds a new in-memory instance, it is not what the getter returned.
        isNaturalPerson = isNaturalPerson.getOrElse(false),
        principalUserIdOption = blankToNone(principalUserId),
        mobilePhoneNumber = blankToNone(mobilePhoneNumber),
        mobilePhoneNumberIsValidated = mobilePhoneNumberIsValidated,
        mobilePhoneNumberValidatedDate = mobilePhoneNumberValidatedDate.map(t => new Date(t.getTime)))
  }

  private def query(condition: Fragment): List[ResourceUser] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def one(condition: Fragment): Box[ResourceUser] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findByPrimaryKey(id: Long): Box[ResourceUser] = one(fr"WHERE id = $id")
  def findByUserId(userId: String): Box[ResourceUser] = one(fr"WHERE userid_ = ${opt(userId)}")
  def findByProviderAndProviderId(provider: String, providerId: String): Box[ResourceUser] =
    one(fr"WHERE provider_ = ${opt(provider)} AND providerid = ${opt(providerId)}")
  def findByProviderAndName(provider: String, name: String): Box[ResourceUser] =
    one(fr"WHERE provider_ = ${opt(provider)} AND name_ = ${opt(name)}")

  // Mapper's ByList with an empty list rendered "0 = 1", i.e. no rows - not "no filter".
  def findAllByUserIds(userIds: List[String]): List[ResourceUser] =
    if (userIds.isEmpty) Nil
    else query(fr"WHERE " ++
      Fragments.in(fr"userid_", cats.data.NonEmptyList.fromListUnsafe(userIds.distinct)))
  def findAllByName(name: String): List[ResourceUser] = query(fr"WHERE name_ = ${opt(name)}")
  /** The locked-username lookup: an empty list matches no rows, as Mapper's ByList did. */
  def findAllByNames(names: List[String]): List[ResourceUser] =
    if (names.isEmpty) Nil
    else query(fr"WHERE " ++
      Fragments.in(fr"name_", cats.data.NonEmptyList.fromListUnsafe(names.distinct)))
  def findAllByEmail(email: String): List[ResourceUser] = query(fr"WHERE email = ${opt(email)}")
  def findAllByPrimaryKeys(ids: List[Long]): List[ResourceUser] =
    if (ids.isEmpty) Nil
    else query(fr"WHERE " ++ Fragments.in(fr"id", cats.data.NonEmptyList.fromListUnsafe(ids.distinct)))
  def findAllByProviderAndProviderId(provider: String, providerId: String): List[ResourceUser] =
    query(fr"WHERE provider_ = ${opt(provider)} AND providerid = ${opt(providerId)}")
  def findAllByCreatedByConsentIds(consentIds: List[String]): List[ResourceUser] =
    if (consentIds.isEmpty) Nil
    else query(fr"WHERE " ++
      Fragments.in(fr"createdbyconsentid", cats.data.NonEmptyList.fromListUnsafe(consentIds.distinct)))
  def findAll(): List[ResourceUser] = query(Fragment.empty)
  def count(): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM resourceuser".query[Long].unique)

  /**
   * The listing LiftUsers.getUsersCommon built out of query params.
   *
   * Two things are deliberate and were in the Mapper version too. Absent `isDeleted` means
   * `is_deleted = false` rather than "no filter". And users a consent minted for itself are always
   * excluded: they are not people, there is one per consent ever granted, and they outnumber real
   * users by orders of magnitude - filtered in SQL so it composes with limit/offset, because a
   * filter applied after pagination returns short pages.
   *
   * No ORDER BY, matching Mapper: the rows come back in whatever order the database gives.
   */
  def findAll(params: UserQuery): List[ResourceUser] = {
    val where =
      fr"WHERE isdeleted = ${params.isDeleted.getOrElse(false)}" ++
        fr"AND (createdbyconsentid IS NULL OR createdbyconsentid = '')"
    val paging =
      params.limit.map(value => fr"LIMIT $value").getOrElse(Fragment.empty) ++
        params.offset.map(value => fr"OFFSET $value").getOrElse(Fragment.empty)
    query(where ++ paging)
  }

  def insert(row: ResourceUser): ResourceUser = {
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO resourceuser
            (userid_, email, name_, provider_, providerid, company, createdbyconsentid,
             createdbyuserinvitationid, isdeleted, lastmarketingagreementsigneddate,
             lastusedlocale, isnaturalperson, principaluserid,
             mobilephonenumber, mobilephonenumberisvalidated, mobilephonenumbervalidateddate)
            VALUES (${opt(row.userId)}, ${opt(row.emailAddress)}, ${opt(row.name)},
             ${opt(row.provider)}, ${opt(row.idGivenByProvider)}, ${opt(row.company)},
             ${row.createdByConsentId.flatMap(Option(_))},
             ${row.createdByUserInvitationId.flatMap(Option(_))}, ${row.isDeleted},
             ${row.lastMarketingAgreementSignedDate.map(d => new java.sql.Date(d.getTime))},
             ${row.lastUsedLocale.flatMap(Option(_))}, ${row.isNaturalPerson},
             ${row.principalUserIdOption.flatMap(Option(_))},
             ${row.mobilePhoneNumber.flatMap(Option(_))}, ${row.mobilePhoneNumberIsValidated},
             ${row.mobilePhoneNumberValidatedDate.map(d => new java.sql.Timestamp(d.getTime))})"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    row.copy(id = id)
  }

  def update(row: ResourceUser): ResourceUser = {
    DoobieUtil.runUpdate(
      sql"""UPDATE resourceuser
            SET userid_ = ${opt(row.userId)}, email = ${opt(row.emailAddress)},
                name_ = ${opt(row.name)}, provider_ = ${opt(row.provider)},
                providerid = ${opt(row.idGivenByProvider)}, company = ${opt(row.company)},
                createdbyconsentid = ${row.createdByConsentId.flatMap(Option(_))},
                createdbyuserinvitationid = ${row.createdByUserInvitationId.flatMap(Option(_))},
                isdeleted = ${row.isDeleted},
                lastmarketingagreementsigneddate =
                  ${row.lastMarketingAgreementSignedDate.map(d => new java.sql.Date(d.getTime))},
                lastusedlocale = ${row.lastUsedLocale.flatMap(Option(_))},
                isnaturalperson = ${row.isNaturalPerson},
                principaluserid = ${row.principalUserIdOption.flatMap(Option(_))},
                mobilephonenumber = ${row.mobilePhoneNumber.flatMap(Option(_))},
                mobilephonenumberisvalidated = ${row.mobilePhoneNumberIsValidated},
                mobilephonenumbervalidateddate =
                  ${row.mobilePhoneNumberValidatedDate.map(d => new java.sql.Timestamp(d.getTime))}
            WHERE id = ${row.id}"""
        .update.run)
    row
  }

  def countByProviderAndProviderId(provider: String, providerId: String): Long =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM resourceuser
            WHERE provider_ = ${Option(provider)} AND providerid = ${Option(providerId)}"""
        .query[Long].unique)

  /** Mapper's bulkDelete_!!(By(providerid, ...)). */
  def deleteAllByProviderId(providerId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM resourceuser WHERE providerid = ${Option(providerId)}".update.run) > 0

  def deleteAllByProviderAndProviderId(provider: String, providerId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"""DELETE FROM resourceuser
            WHERE provider_ = ${Option(provider)} AND providerid = ${Option(providerId)}"""
        .update.run) > 0

  /** Mapper's bulkDelete_!!(By(name_, ...)): every row with that username, in one statement. */
  def deleteAllByName(name: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM resourceuser WHERE name_ = ${Option(name)}".update.run) > 0

  def delete(id: Long): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM resourceuser WHERE id = $id".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM resourceuser".update.run)
    ()
  }
}

/** The paging and filters a user listing carries. */
case class UserQuery(limit: Option[Int], offset: Option[Int], isDeleted: Option[Boolean])

case class ResourceUserCaseClass(
                                  emailAddress: String,
                                  idGivenByProvider: String,
                                  resourceUserId: Long,
                                  userId: String,
                                  name: String,
                                  provider: String
                                )
