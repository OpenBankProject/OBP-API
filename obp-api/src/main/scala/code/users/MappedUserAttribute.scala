package code.users

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.UserAttributeTrait
import com.openbankproject.commons.model.enums.UserAttributeType
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

/**
 * One attribute held against a user.
 *
 * `attributeType` stays a def rather than a field: it calls withName on the stored string, which
 * throws for a value outside the enum. As a def that only happens if a caller asks for it, which
 * is the existing behaviour; evaluating it at construction would make every read of every row
 * throw instead.
 */
case class UserAttribute(
  userAttributeId: String,
  userId: String,
  name: String,
  private val typeRaw: String,
  value: String,
  isPersonal: Boolean,
  insertDate: Date
) extends UserAttributeTrait {
  override def attributeType: UserAttributeType.Value = UserAttributeType.withName(typeRaw)
}

object UserAttribute {

  // type is stored as type_c: TYPE collides with a SQL reserved word.
  private val selectColumns =
    fr"""SELECT userattributeid, userid, name, type_c, value, ispersonal, createdat
         FROM userattribute"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Boolean], Option[java.sql.Timestamp])

  private def fromRow(row: Row): UserAttribute = row match {
    case (userAttributeId, userId, name, attributeType, value, isPersonal, createdAt) =>
        // MappedBoolean read a NULL column as false - `data openOr false`, with a NULL
        // setting `data = Empty` - so it never failed the read and never returned the
        // field's declared defaultValue. Binding the column as Option keeps both halves.
      UserAttribute(userAttributeId.orNull, userId.orNull, name.orNull, attributeType.orNull,
        value.orNull, isPersonal.getOrElse(false), createdAt.orNull)
  }

  private def query(condition: Fragment): List[UserAttribute] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(userId: String, name: String, attributeType: String, value: String,
             isPersonal: Boolean): UserAttribute = {
    val userAttributeId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO userattribute
            (userattributeid, userid, name, type_c, value, ispersonal, createdat, updatedat)
            VALUES ($userAttributeId, $userId, $name, $attributeType, $value, $isPersonal,
             $now, $now)"""
        .update.run)
    findById(userAttributeId)
      .openOrThrowException("the user attribute just inserted must be readable")
  }

  /**
   * isPersonal is deliberately left alone: Mapper's update path never wrote it, so an attribute
   * cannot change between personal and non-personal after creation.
   */
  def update(userAttributeId: String, userId: String, name: String, attributeType: String,
             value: String): Box[UserAttribute] = {
    DoobieUtil.runUpdate(
      sql"""UPDATE userattribute SET userid = $userId, name = $name, type_c = $attributeType,
              value = $value, updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
            WHERE userattributeid = $userAttributeId""".update.run)
    findById(userAttributeId)
  }

  def findById(userAttributeId: String): Box[UserAttribute] =
    query(fr"WHERE userattributeid = $userAttributeId ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findAllByUserId(userId: String): List[UserAttribute] =
    query(fr"WHERE userid = $userId ORDER BY id ASC")

  def findAllByUserIdAndPersonal(userId: String, isPersonal: Boolean): List[UserAttribute] =
    query(fr"WHERE userid = $userId AND ispersonal = $isPersonal ORDER BY createdat DESC, id DESC")

  def findAllByUserIds(userIds: List[String]): List[UserAttribute] =
    // Mapper's ByList with an empty list rendered "0 = 1", i.e. no rows — not "no filter".
    if (userIds.isEmpty) Nil
    else {
      val in = Fragments.in(fr"userid", cats.data.NonEmptyList.fromListUnsafe(userIds.distinct))
      query(fr"WHERE " ++ in ++ fr"ORDER BY id ASC")
    }

  def delete(userAttributeId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM userattribute WHERE userattributeid = $userAttributeId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM userattribute".update.run)
    ()
  }
}

object MappedUserAttributeProvider extends UserAttributeProvider {

  override def getUserAttributesByUser(userId: String): Future[Box[List[UserAttribute]]] = Future {
    tryo(UserAttribute.findAllByUserId(userId))
  }

  override def getPersonalUserAttributes(userId: String): Future[Box[List[UserAttribute]]] = Future {
    tryo(UserAttribute.findAllByUserIdAndPersonal(userId, isPersonal = true))
  }

  override def getNonPersonalUserAttributes(userId: String): Future[Box[List[UserAttribute]]] = Future {
    tryo(UserAttribute.findAllByUserIdAndPersonal(userId, isPersonal = false))
  }

  override def getUserAttributesByUsers(userIds: List[String]): Future[Box[List[UserAttribute]]] = Future {
    tryo(UserAttribute.findAllByUserIds(userIds))
  }

  override def deleteUserAttribute(userAttributeId: String): Future[Box[Boolean]] = Future {
    UserAttribute.findById(userAttributeId) match {
      case Full(_) => Full(UserAttribute.delete(userAttributeId))
      case Empty => Empty ?~! ErrorMessages.UserAttributeNotFound
      case _ => Full(false)
    }
  }

  override def createOrUpdateUserAttribute(userId: String,
                                           userAttributeId: Option[String],
                                           name: String,
                                           attributeType: UserAttributeType.Value,
                                           value: String,
                                           isPersonal: Boolean): Future[Box[UserAttribute]] =
    userAttributeId match {
      case Some(id) => Future {
        UserAttribute.findById(id) match {
          case Full(_) =>
            tryo(UserAttribute.update(id, userId, name, attributeType.toString, value))
              .flatMap(identity)
          case _ => Empty
        }
      }
      case None => Future {
        Full(UserAttribute.insert(userId, name, attributeType.toString, value, isPersonal))
      }
    }
}
