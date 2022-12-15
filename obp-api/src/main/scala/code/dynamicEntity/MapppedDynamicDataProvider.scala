package code.DynamicData

import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.DynamicDataNotFound
import code.util.MappedUUID
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.json
import net.liftweb.json.JObject
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.JsonDSL._
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

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

  override def get(bankId: Option[String],entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    if(bankId.isEmpty && !isPersonalEntity ){ //isPersonalEntity == false, get all the data, no need for specific userId.
      //forced the empty also to a error here. this is get Dynamic by Id, if it return Empty, better show the error in this level.
      DynamicData.find(
        By(DynamicData.DynamicDataId, id), 
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.UserId, userId.getOrElse(null)),
        By(DynamicData.IsPersonalEntity, false),
        NullRef(DynamicData.BankId)
      ) match {
        case Full(dynamicData) => Full(dynamicData)
        case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id")
      }
    } else if(bankId.isEmpty && isPersonalEntity){ //isPersonalEntity == true, get all the data for specific userId.
      DynamicData.find(
        By(DynamicData.DynamicDataId, id), 
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.UserId, userId.getOrElse(null)),
        By(DynamicData.IsPersonalEntity, true),
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
    }else{  //isPersonalEntity == true, get all the data for specific userId.
      DynamicData.find(
        By(DynamicData.DynamicDataId, id), 
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, bankId.get),
        By(DynamicData.UserId, userId.get),
        By(DynamicData.IsPersonalEntity, true)
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
    } else if(bankId.isEmpty && isPersonalEntity){  //isPersonalEntity == true, get all the data for specific userId.
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.UserId, userId.getOrElse(null)),
        By(DynamicData.IsPersonalEntity, true),
        NullRef(DynamicData.BankId)
      ) 
    } else if(bankId.isDefined && !isPersonalEntity){ //isPersonalEntity == false, get all the data, no need for specific userId.
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.IsPersonalEntity, false),
        By(DynamicData.BankId, bankId.get),
      )
    }else{
      DynamicData.findAll(//isPersonalEntity == true, get all the data for specific userId.
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, bankId.get),
        By(DynamicData.UserId, userId.getOrElse(null)),
        By(DynamicData.IsPersonalEntity, true)
      )
    }
  }

  override def delete(bankId: Option[String], entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean) = {
    get(bankId, entityName, id, userId, isPersonalEntity).map(_.asInstanceOf[DynamicData].delete_!)
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
    } else if(bankId.isEmpty && isPersonalEntity){ //isPersonalEntity == true, get all the data for specific userId.
      DynamicData.find(
        By(DynamicData.DynamicEntityName, dynamicEntityName),
        NullRef(DynamicData.BankId),
        By(DynamicData.IsPersonalEntity, true),
        By(DynamicData.UserId, userId.getOrElse(null))
      ).nonEmpty
    } else {
      DynamicData.find(
        By(DynamicData.DynamicEntityName, dynamicEntityName),
        By(DynamicData.BankId, bankId.get),
        By(DynamicData.IsPersonalEntity, true),
        By(DynamicData.UserId, userId.getOrElse(null))
      ).nonEmpty
    }
  }

  private def saveOrUpdate(bankId: Option[String], entityName: String, requestBody: JObject, userId: Option[String], isPersonalEntity: Boolean, dynamicData: => DynamicData): Box[DynamicData] = {
    val data: DynamicData = dynamicData
    tryo {
      val dataStr = json.compactRender(requestBody)
     data.DataJson(dataStr)
       .DynamicEntityName(entityName)
       .BankId(bankId.getOrElse(null))
       .UserId(userId.getOrElse(null))
       .IsPersonalEntity(isPersonalEntity)
       .saveMe()
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

