/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

package code.DynamicData

import org.json4s._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.DynamicDataNotFound
import code.api.util.APIUtil.generateUUID
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import net.liftweb.common.{Box, Failure, Full}
import com.openbankproject.commons.util.json
import org.json4s.JObject
import org.json4s.JsonAST.JString
import org.json4s.JsonDSL._
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

/**
 * Note on IsPersonalEntity flag:
 * The IsPersonalEntity flag indicates HOW a record was created (via /my/ endpoint or not),
 * but is NOT used as a filter when querying personal data. The /my/ endpoints return all
 * records belonging to the current user (filtered by UserId), regardless of IsPersonalEntity value.
 * This provides a unified view of a user's data whether it was created via /my/ or non-/my/ endpoints.
 */
object MappedDynamicDataProvider extends DynamicDataProvider with CustomJsonFormats{

  /**
   * The user a row belongs to: the caller, or the user its consent names (attribution policy
   * UserReference.DynamicData_UserId). Applied on every entry point that takes a userId, for
   * reads as well as writes, so a consent user reads, updates and deletes the same rows it
   * writes. The resolver logs each redirect; a Failure (invariant broken) keeps the caller.
   * The endpoint decides who may reach this provider (a consent user needs the entity's role,
   * see Http4sDynamicEntity.personalRoleWaived). ON_BEHALF_OF_USER_ID_PLAN.md, Phase 2.
   */
  private def ownerOf(userId: Option[String]): Option[String] =
    userId.map(id => code.users.Users.users.vend.attributedUserId(id, code.users.UserReference.DynamicData_UserId).openOr(id))

  /**
   * The bank and the entity that a lookup is confined to.
   *
   * Every query in this provider starts from these two, so that a record held in one space can
   * never be found, updated or deleted through a request naming another. A record belonging to no
   * bank is stored under Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID rather than as a SQL NULL,
   * and that is what lets the system level case share this one query: it differs from a space only
   * in the value bound here. While the column held NULL the two needed different SQL -- NullRef
   * against a plain equality -- and each method below carried a separate copy of itself for each.
   *
   * The two are named in the order the things they identify come into existence, the bank first and
   * then the entity defined within it, which is also the order of the columns in DynamicData's
   * unique index. A query needing more than these two appends it -- the record id next, then the
   * ownership predicate -- so every query in this file reads in that same order.
   */
  private def whereClauseBankOrSystemAndEntity(bankId: Option[String], entityName: String): List[QueryParam[DynamicData]] =
    List(
      By(DynamicData.BankId, bankId.getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)),
      By(DynamicData.DynamicEntityName, entityName)
    )

  /**
   * Whose records of an entity the caller is asking about.
   *
   * Each record of a personal entity belongs to one user, and is selected by naming that user.
   * Records of any other entity are selected by the flag alone, with no user named at all.
   */
  private def byOwnership(isPersonalEntity: Boolean, userId: Option[String]): QueryParam[DynamicData] =
    if (isPersonalEntity) By(DynamicData.UserId, userId.getOrElse(null))
    else By(DynamicData.IsPersonalEntity, false)

  /**
   * The wording of the not-found failure for an owner-scoped read, which varies with what the
   * caller named.
   *
   * These four spellings are reproduced exactly as they stood when each case had its own copy of
   * the query, because they reach callers as the body of a not-found response. Two of them are
   * irregular and are deliberately left that way: one renders the user id as an Option, so it reads
   * "userId = Some(x)", and the bank id is preceded by a space here that the community wording
   * below does not have. The one deviation is that a missing user id used to be read with .get in
   * the last case, which threw while building the message instead of producing one; it now reads
   * as null, and no message that previously rendered is changed.
   */
  private def notFoundMessage(entityName: String, id: String, bankId: Option[String],
                              userId: Option[String], isPersonalEntity: Boolean): String = {
    val base = s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id"
    (bankId.isDefined, isPersonalEntity) match {
      case (false, false) => base
      case (false, true)  => s"$base, userId = $userId"
      case (true, false)  => s"$base, bankId= ${bankId.get}"
      case (true, true)   => s"$base, bankId= ${bankId.get}, userId = ${userId.getOrElse(null)}"
    }
  }

  /** As notFoundMessage, for the community reads, whose bank id carries no leading space. */
  private def notFoundCommunityMessage(entityName: String, id: String, bankId: Option[String]): String = {
    val base = s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id"
    bankId.map(theBank => s"$base, bankId=$theBank").getOrElse(base)
  }

  override def save(bankId: Option[String], entityName: String, requestBody: JObject, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    val idName = getIdName(entityName)
    val JString(idValue) = (requestBody \ idName).asInstanceOf[JString]
    val dynamicData: DynamicData = DynamicData.create.DynamicDataId(idValue)
    val result = saveOrUpdate(bankId, entityName, requestBody, ownerOf(userId), isPersonalEntity, dynamicData)
    result
  }
  override def update(bankId: Option[String], entityName: String, requestBody: JObject, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    val owner = ownerOf(userId)
    val dynamicData = get(bankId, entityName, id, owner, isPersonalEntity).openOrThrowException(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id").asInstanceOf[DynamicData]
    saveOrUpdate(bankId, entityName, requestBody, owner, isPersonalEntity, dynamicData)
  }

  // Separate method for reference validation - only checks ID and entity name exist
  def existsById(entityName: String, id: String): Boolean = {
    println(s"========== Reference validation: checking if DynamicDataId='$id' exists for DynamicEntityName='$entityName' ==========")
    val exists = DynamicData.count(
      By(DynamicData.DynamicEntityName, entityName),
      By(DynamicData.DynamicDataId, id)
    ) > 0
    println(s"========== Reference validation result: exists=$exists ==========")
    exists
  }

  override def get(bankId: Option[String], entityName: String, id: String, callerUserId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    val userId = ownerOf(callerUserId)
    // An Empty is turned into a Failure rather than passed up: this is get-by-id, and the reason
    // nothing came back is worth stating at this level.
    DynamicData.find(
      (whereClauseBankOrSystemAndEntity(bankId, entityName) :+ By(DynamicData.DynamicDataId, id) :+ byOwnership(isPersonalEntity, userId)): _*
    ) match {
      case Full(dynamicData) => Full(dynamicData)
      case _ => Failure(notFoundMessage(entityName, id, bankId, userId, isPersonalEntity))
    }
  }

  override def getAllDataJson(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity: Boolean): List[JObject] = {
    getAll(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity)
      .map(it => json.parse(it.dataJson))
      .map(_.asInstanceOf[JObject])
  }

  override def getAll(bankId: Option[String], entityName: String, callerUserId: Option[String], isPersonalEntity: Boolean): List[DynamicDataT] = {
    val userId = ownerOf(callerUserId)
    DynamicData.findAll((whereClauseBankOrSystemAndEntity(bankId, entityName) :+ byOwnership(isPersonalEntity, userId)): _*)
  }

  override def delete(bankId: Option[String], entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean) = {
    get(bankId, entityName, id, userId, isPersonalEntity).map { d =>
      val result = d.asInstanceOf[DynamicData].delete_!
      // DE_indexing: remove the projection row in the same transaction (no-op unless projection enabled+ready).
      code.api.dynamic.entity.projection.ProjectionDualWrite.onDelete(bankId, entityName, id)
      result
    }
  }

  // Community access: return ALL records regardless of userId/IsPersonalEntity
  override def getAllCommunity(bankId: Option[String], entityName: String): List[DynamicDataT] =
    DynamicData.findAll(whereClauseBankOrSystemAndEntity(bankId, entityName): _*)

  override def getAllDataJsonCommunity(bankId: Option[String], entityName: String): List[JObject] = {
    getAllCommunity(bankId, entityName)
      .map(it => json.parse(it.dataJson))
      .map(_.asInstanceOf[JObject])
  }

  override def getCommunity(bankId: Option[String], entityName: String, id: String): Box[DynamicDataT] =
    DynamicData.find((whereClauseBankOrSystemAndEntity(bankId, entityName) :+ By(DynamicData.DynamicDataId, id)): _*) match {
      case Full(dynamicData) => Full(dynamicData)
      case _ => Failure(notFoundCommunityMessage(entityName, id, bankId))
    }

  override def updateCommunity(bankId: Option[String], entityName: String, requestBody: JObject, id: String): Box[DynamicDataT] = {
    val dynamicData = getCommunity(bankId, entityName, id)
      .openOrThrowException(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id")
      .asInstanceOf[DynamicData]
    // Preserve the row's existing owner/personal flag — row-level access changes the data, not provenance.
    saveOrUpdate(bankId, entityName, requestBody, Option(dynamicData.UserId.get), dynamicData.IsPersonalEntity.get, dynamicData)
  }

  override def deleteCommunity(bankId: Option[String], entityName: String, id: String): Box[Boolean] = {
    getCommunity(bankId, entityName, id).map { d =>
      val result = d.asInstanceOf[DynamicData].delete_!
      // DE_indexing: remove the projection row in the same transaction (no-op unless projection enabled+ready).
      code.api.dynamic.entity.projection.ProjectionDualWrite.onDelete(bankId, entityName, id)
      result
    }
  }

  override def existsData(bankId: Option[String], dynamicEntityName: String, callerUserId: Option[String], isPersonalEntity: Boolean): Boolean = {
    val userId = ownerOf(callerUserId)
    DynamicData.find((whereClauseBankOrSystemAndEntity(bankId, dynamicEntityName) :+ byOwnership(isPersonalEntity, userId)): _*).isDefined
  }

  private def saveOrUpdate(bankId: Option[String], entityName: String, requestBody: JObject, userId: Option[String], isPersonalEntity: Boolean, dynamicData: => DynamicData): Box[DynamicData] = {
    val data: DynamicData = dynamicData
    tryo {
      val dataStr = json.compactRender(requestBody)
     val saved = data.BankId(bankId.getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID))
       .DynamicEntityName(entityName)
       .DataJson(dataStr)
       .UserId(userId.getOrElse(null))
       .IsPersonalEntity(isPersonalEntity)
       .saveMe()
     // DE_indexing: keep the projection in sync in the same transaction (no-op unless projection enabled+ready).
     code.api.dynamic.entity.projection.ProjectionDualWrite.onSave(bankId, entityName, saved.DynamicDataId.get, requestBody)
     saved
    }
  }

  private def getIdName(entityName: String) = {
    s"${entityName}_Id".replaceAll("(?<=[a-z0-9])(?=[A-Z])|-", "_").toLowerCase
  }
}

class DynamicData extends DynamicDataT with LongKeyedMapper[DynamicData] with IdPK {

  override def getSingleton = DynamicData

  /**
   * The identifier of a single Dynamic Entity record. This column used to be a `MappedUUID`, which is
   * 36 characters wide because that is the length of a UUID. A caller may however supply the id itself
   * in the request body instead of letting one be generated, which is how a Dynamic Entity is given a
   * natural key such as a country code or a scheme name, so the value is not always a UUID and can be
   * considerably longer than 36 characters. Such a value used to reach Postgres unchanged and fail
   * there with "value too long for type character varying(36)". The column is therefore an ordinary
   * string of 255 characters, the same width as the two other columns that hold this same id: the
   * row level access list in DynamicDataAccess, and the `data_id` column of the SQL projection tables.
   * The default value stays a generated UUID, so a request that omits the id behaves exactly as before.
   */
  object DynamicDataId extends MappedString(this, 255) {
    override def defaultValue = generateUUID()
  }
  object DynamicEntityName extends MappedString(this, 255)

  object DataJson extends MappedText(this)
  
  object BankId extends MappedString(this,255)
  
  object UserId extends MappedString(this,255)
  
  object IsPersonalEntity extends MappedBoolean(this)

  override def dynamicDataId: Option[String] = Option(DynamicDataId.get)
  override def dynamicEntityName: String = DynamicEntityName.get
  override def dataJson: String = DataJson.get
  // A system level record stores the sentinel rather than a SQL NULL, so it is filtered back out
  // here: every caller of this method still sees None for such a record, exactly as before.
  override def bankId: Option[String] = Option(BankId.get).filterNot(_ == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
  override def userId: Option[String] = Option(UserId.get)
  override def isPersonalEntity: Boolean = IsPersonalEntity.get
}

object DynamicData extends DynamicData with LongKeyedMetaMapper[DynamicData] {
  /**
   * A record's id is unique within one space and one entity, not across the whole instance.
   *
   * The columns are named in the order the things they identify come into existence: the bank (the
   * space) exists first, the entity is defined within it, and the record is created last. The index
   * used to name DynamicDataId alone, which meant two spaces could not each hold a record with the
   * same natural key -- one country table holding DE stopped any other from holding it. The
   * discriminators were already sitting in the same row, simply unused by the index.
   *
   * The bank id column never holds a SQL NULL; see Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID for
   * why that matters here, since Postgres treats NULLs as distinct and a nullable column in this
   * index would enforce nothing for system level records.
   */
  override def dbIndexes = UniqueIndex(BankId, DynamicEntityName, DynamicDataId) :: super.dbIndexes
}

