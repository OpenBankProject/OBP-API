package code.DynamicData

import org.json4s._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.DynamicDataNotFound
import code.util.MappedUUID
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
  override def save(bankId: Option[String], entityName: String, requestBody: JObject, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    val idName = getIdName(entityName)
    val JString(idValue) = (requestBody \ idName).asInstanceOf[JString]
    val dynamicData: DynamicData = DynamicData.create.DynamicDataId(idValue)
    val result = saveOrUpdate(bankId, entityName, requestBody, userId, isPersonalEntity, dynamicData)
    result
  }
  override def update(bankId: Option[String], entityName: String, requestBody: JObject, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    val dynamicData = get(bankId, entityName, id, userId, isPersonalEntity).openOrThrowException(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id").asInstanceOf[DynamicData]
    saveOrUpdate(bankId, entityName, requestBody, userId, isPersonalEntity, dynamicData)
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

  override def get(bankId: Option[String],entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    if(bankId.isEmpty && !isPersonalEntity ){ //isPersonalEntity == false, get all the data, no need for specific userId.
      //forced the empty also to a error here. this is get Dynamic by Id, if it return Empty, better show the error in this level.
      DynamicData.find(
        By(DynamicData.DynamicDataId, id),
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.IsPersonalEntity, false),
        NullRef(DynamicData.BankId)
      ) match {
        case Full(dynamicData) => Full(dynamicData)
        case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id")
      }
    } else if(bankId.isEmpty && isPersonalEntity){ //isPersonalEntity == true, get the data for specific userId (regardless of how it was created).
      DynamicData.find(
        By(DynamicData.DynamicDataId, id),
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.UserId, userId.getOrElse(null)),
        NullRef(DynamicData.BankId)
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

  override def getAll(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity: Boolean): List[DynamicDataT] = {
    if(bankId.isEmpty && !isPersonalEntity){ //isPersonalEntity == false, get all the data, no need for specific userId.
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.IsPersonalEntity, false),
        NullRef(DynamicData.BankId),
      )
    } else if(bankId.isEmpty && isPersonalEntity){  //isPersonalEntity == true, get all the data for specific userId (regardless of how it was created).
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.UserId, userId.getOrElse(null)),
        NullRef(DynamicData.BankId)
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
        NullRef(DynamicData.BankId),
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
        NullRef(DynamicData.BankId)
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

  override def existsData(bankId: Option[String], dynamicEntityName: String, userId: Option[String], isPersonalEntity: Boolean): Boolean = {
    if(bankId.isEmpty && !isPersonalEntity){//isPersonalEntity == false, get all the data, no need for specific userId.
      DynamicData.find(
        By(DynamicData.DynamicEntityName, dynamicEntityName),
        NullRef(DynamicData.BankId),
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
        NullRef(DynamicData.BankId),
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
       .BankId(bankId.getOrElse(null))
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

  object DynamicDataId extends MappedUUID(this)
  object DynamicEntityName extends MappedString(this, 255)

  object DataJson extends MappedText(this)
  
  object BankId extends MappedString(this,255)
  
  object UserId extends MappedString(this,255)
  
  object IsPersonalEntity extends MappedBoolean(this)

  override def dynamicDataId: Option[String] = Option(DynamicDataId.get)
  override def dynamicEntityName: String = DynamicEntityName.get
  override def dataJson: String = DataJson.get
  override def bankId: Option[String] = Option(BankId.get)
  override def userId: Option[String] = Option(UserId.get)
  override def isPersonalEntity: Boolean = IsPersonalEntity.get
}

object DynamicData extends DynamicData with LongKeyedMetaMapper[DynamicData] {
  override def dbIndexes = UniqueIndex(DynamicDataId) :: super.dbIndexes
}

