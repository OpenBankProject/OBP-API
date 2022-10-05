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
  override def save(bankId: Option[String], entityName: String, requestBody: JObject): Box[DynamicDataT] = {
    val idName = getIdName(entityName)
    val JString(idValue) = (requestBody \ idName).asInstanceOf[JString]
    val dynamicData: DynamicData = DynamicData.create.DynamicDataId(idValue)
    val result = saveOrUpdate(bankId, entityName, requestBody, dynamicData)
    result
  }
  override def update(bankId: Option[String], entityName: String, requestBody: JObject, id: String): Box[DynamicDataT] = {
    val dynamicData = get(bankId, entityName, id).openOrThrowException(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynameicDataId=$id").asInstanceOf[DynamicData]
    saveOrUpdate(bankId, entityName, requestBody, dynamicData)
  }

  override def get(bankId: Option[String],entityName: String, id: String): Box[DynamicDataT] = {
    if(bankId.isEmpty){
      //forced the empty also to a error here. this is get Dynamic by Id, if it return Empty, better show the error in this level.
      DynamicData.find(
        By(DynamicData.DynamicDataId, id), 
        By(DynamicData.DynamicEntityName, entityName),
        NullRef(DynamicData.BankId),
      ) match {
        case Full(dynamicData) => Full(dynamicData)
        case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynameicDataId=$id")
      }
    } else{
      //forced the empty also to a error here. this is get Dynamic by Id, if it return Empty, better show the error in this level.
      DynamicData.find(
        By(DynamicData.DynamicDataId, id), 
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, bankId.get)
      ) match {
        case Full(dynamicData) => Full(dynamicData)
        case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynameicDataId=$id")
      }
    }
    
  }

  override def getAllDataJson(bankId: Option[String], entityName: String): List[JObject] = { 
    if(bankId.isEmpty){
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        NullRef(DynamicData.BankId)
      ).map(it => json.parse(it.dataJson)).map(_.asInstanceOf[JObject])
    } else {
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, bankId.get)
      ).map(it => json.parse(it.dataJson)).map(_.asInstanceOf[JObject])
    }
  }

  override def getAll(bankId: Option[String], entityName: String): List[DynamicDataT] = { 
    if(bankId.isEmpty) {
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        NullRef(DynamicData.BankId)
      )
    }else{
      DynamicData.findAll(
        By(DynamicData.DynamicEntityName, entityName),
        By(DynamicData.BankId, bankId.get)
      )
    }
  }

  override def delete(bankId: Option[String], entityName: String, id: String) = {
    //forced the empty also to a error here. this is get Dynamic by Id, if it return Empty, better show the error in this level.
    //Note: DynamicDataId is UniqueIndex
    DynamicData.find(By(DynamicData.DynamicDataId, id), By(DynamicData.DynamicEntityName, entityName)) match {
      case Full(dynamicData) => Full(dynamicData.delete_!)
      case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id")
    }
  }

  override def existsData(bankId: Option[String], dynamicEntityName: String): Boolean = {
    if(bankId.isEmpty){
      DynamicData.find(
        By(DynamicData.DynamicEntityName, dynamicEntityName),
        NullRef(DynamicData.BankId)
      ).isDefined
    } else {
      DynamicData.find(
        By(DynamicData.DynamicEntityName, dynamicEntityName),
        By(DynamicData.BankId, bankId.get)
      ).nonEmpty
    }
  }

  private def saveOrUpdate(bankId: Option[String], entityName: String, requestBody: JObject, dynamicData: => DynamicData): Box[DynamicData] = {
    val data: DynamicData = dynamicData

    val dataStr = json.compactRender(requestBody)
    tryo {
      if(bankId.isDefined){
        data.DataJson(dataStr).DynamicEntityName(entityName).BankId(bankId.get).saveMe()
      } else{
        data.DataJson(dataStr).DynamicEntityName(entityName).BankId(null).saveMe()
      }
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

  override def dynamicDataId: Option[String] = Option(DynamicDataId.get)
  override def dynamicEntityName: String = DynamicEntityName.get
  override def dataJson: String = DataJson.get
  override def bankId: Option[String] = Some(BankId.get)
}

object DynamicData extends DynamicData with LongKeyedMetaMapper[DynamicData] {
  override def dbIndexes = UniqueIndex(DynamicDataId) :: super.dbIndexes
}

