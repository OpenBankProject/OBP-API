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
      By(DynamicData.DynamicDataId, id),
      By(DynamicData.DynamicEntityName, entityName)
    ) > 0
    println(s"========== Reference validation result: exists=$exists ==========")
    exists
  }

  override def get(bankId: Option[String],entityName: String, id: String, callerUserId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    val userId = ownerOf(callerUserId)
    if(bankId.isEmpty && !isPersonalEntity ){ //isPersonalEntity == false, get all the data, no need for specific userId.
      //forced the empty also to a error here. this is get Dynamic by Id, if it return Empty, better show the error in this level.
      DynamicData.find(
        By(DynamicData.DynamicDataId, id),
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.IsPersonalEntity, false),
        By(DynamicData.BankId, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
      ) match {
        case Full(dynamicData) => Full(dynamicData)
        case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id")
      }
    } else if(bankId.isEmpty && isPersonalEntity){ //isPersonalEntity == true, get the data for specific userId (regardless of how it was created).
      DynamicData.find(
        By(DynamicData.DynamicDataId, id),
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.UserId, userId.getOrElse(null)),
        By(DynamicData.BankId, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
      ) match {
        case Full(dynamicData) => Full(dynamicData)
        case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id, userId = $userId")
      }
    } else if(bankId.isDefined && !isPersonalEntity ){ //isPersonalEntity == false, get all the data, no need for specific userId.
      //forced the empty also to a error here. this is get Dynamic by Id, if it return Empty, better show the error in this level.
      DynamicData.find(
        By(DynamicData.DynamicDataId, id),
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.IsPersonalEntity, false),
        By(DynamicData.BankId, bankId.get),
      ) match {
        case Full(dynamicData) => Full(dynamicData)
        case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id, bankId= ${bankId.get}")
      }
    }else{  //isPersonalEntity == true, get the data for specific userId (regardless of how it was created).
      DynamicData.find(
        By(DynamicData.DynamicDataId, id),
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, bankId.get),
        By(DynamicData.UserId, userId.get)
      ) match {
        case Full(dynamicData) => Full(dynamicData)
        case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id, bankId= ${bankId.get}, userId = ${userId.get}")
      }
    }

  }

  override def getAllDataJson(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity: Boolean): List[JObject] = {
    getAll(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity)
      .map(it => json.parse(it.dataJson))
      .map(_.asInstanceOf[JObject])
  }

  override def getAll(bankId: Option[String], entityName: String, callerUserId: Option[String], isPersonalEntity: Boolean): List[DynamicDataT] = {
    val userId = ownerOf(callerUserId)
    if(bankId.isEmpty && !isPersonalEntity){ //isPersonalEntity == false, get all the data, no need for specific userId.
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.IsPersonalEntity, false),
        By(DynamicData.BankId, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID),
      )
    } else if(bankId.isEmpty && isPersonalEntity){  //isPersonalEntity == true, get all the data for specific userId (regardless of how it was created).
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.UserId, userId.getOrElse(null)),
        By(DynamicData.BankId, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
      )
    } else if(bankId.isDefined && !isPersonalEntity){ //isPersonalEntity == false, get all the data, no need for specific userId.
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.IsPersonalEntity, false),
        By(DynamicData.BankId, bankId.get),
      )
    }else{
      DynamicData.findAll(//isPersonalEntity == true, get all the data for specific userId (regardless of how it was created).
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, bankId.get),
        By(DynamicData.UserId, userId.getOrElse(null))
      )
    }
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
  override def getAllCommunity(bankId: Option[String], entityName: String): List[DynamicDataT] = {
    if (bankId.isEmpty) {
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID),
      )
    } else {
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, bankId.get),
      )
    }
  }

  override def getAllDataJsonCommunity(bankId: Option[String], entityName: String): List[JObject] = {
    getAllCommunity(bankId, entityName)
      .map(it => json.parse(it.dataJson))
      .map(_.asInstanceOf[JObject])
  }

  override def getCommunity(bankId: Option[String], entityName: String, id: String): Box[DynamicDataT] = {
    if (bankId.isEmpty) {
      DynamicData.find(
        By(DynamicData.DynamicDataId, id),
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
      ) match {
        case Full(dynamicData) => Full(dynamicData)
        case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id")
      }
    } else {
      DynamicData.find(
        By(DynamicData.DynamicDataId, id),
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, bankId.get),
      ) match {
        case Full(dynamicData) => Full(dynamicData)
        case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id, bankId=${bankId.get}")
      }
    }
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
    if(bankId.isEmpty && !isPersonalEntity){//isPersonalEntity == false, get all the data, no need for specific userId.
      DynamicData.find(
        By(DynamicData.DynamicEntityName, dynamicEntityName),
        By(DynamicData.BankId, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID),
        By(DynamicData.IsPersonalEntity, false)
      ).isDefined
    } else if(bankId.isDefined && !isPersonalEntity){//isPersonalEntity == false, get all the data, no need for specific userId.
      DynamicData.find(
        By(DynamicData.DynamicEntityName, dynamicEntityName),
        By(DynamicData.BankId, bankId.get),
        By(DynamicData.IsPersonalEntity, false)
      ).nonEmpty
    } else if(bankId.isEmpty && isPersonalEntity){ //isPersonalEntity == true, check if data exists for specific userId (regardless of how it was created).
      DynamicData.find(
        By(DynamicData.DynamicEntityName, dynamicEntityName),
        By(DynamicData.BankId, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID),
        By(DynamicData.UserId, userId.getOrElse(null))
      ).nonEmpty
    } else { //isPersonalEntity == true, check if data exists for specific userId (regardless of how it was created).
      DynamicData.find(
        By(DynamicData.DynamicEntityName, dynamicEntityName),
        By(DynamicData.BankId, bankId.get),
        By(DynamicData.UserId, userId.getOrElse(null))
      ).nonEmpty
    }
  }

  private def saveOrUpdate(bankId: Option[String], entityName: String, requestBody: JObject, userId: Option[String], isPersonalEntity: Boolean, dynamicData: => DynamicData): Box[DynamicData] = {
    val data: DynamicData = dynamicData
    tryo {
      val dataStr = json.compactRender(requestBody)
     val saved = data.DataJson(dataStr)
       .DynamicEntityName(entityName)
       .BankId(bankId.getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID))
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

