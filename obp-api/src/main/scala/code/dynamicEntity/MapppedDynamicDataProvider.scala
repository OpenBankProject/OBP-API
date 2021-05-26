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
  override def save(entityName: String, requestBody: JObject): Box[DynamicDataT] = {
    val idName = getIdName(entityName)
    val JString(idValue) = (requestBody \ idName).asInstanceOf[JString]
    val dynamicData: DynamicData = DynamicData.create.DynamicDataId(idValue)
    val result = saveOrUpdate(entityName, requestBody, dynamicData)
    result
  }
  override def update(entityName: String, requestBody: JObject, id: String): Box[DynamicDataT] = {
    val dynamicData = get(entityName, id).openOrThrowException(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynameicDataId=$id").asInstanceOf[DynamicData]
    saveOrUpdate(entityName, requestBody, dynamicData)
  }

  override def get(entityName: String, id: String): Box[DynamicDataT] = {
    //forced the empty also to a error here. this is get Dynamic by Id, if it return Empty, better show the error in this level.
    DynamicData.find(By(DynamicData.DynamicDataId, id), By(DynamicData.DynamicEntityName, entityName)) match {
      case Full(dynamicData) => Full(dynamicData)
      case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynameicDataId=$id")
    }
  }

  override def getAllDataJson(entityName: String): List[JObject] = DynamicData.findAll(By(DynamicData.DynamicEntityName, entityName))
    .map(it => json.parse(it.dataJson)).map(_.asInstanceOf[JObject])

  override def getAll(entityName: String): List[DynamicDataT] = DynamicData.findAll(By(DynamicData.DynamicEntityName, entityName))
  
  override def delete(entityName: String, id: String) = {
    //forced the empty also to a error here. this is get Dynamic by Id, if it return Empty, better show the error in this level.
    DynamicData.find(By(DynamicData.DynamicDataId, id), By(DynamicData.DynamicEntityName, entityName)) match {
      case Full(dynamicData) => Full(dynamicData.delete_!)
      case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynameicDataId=$id")
    }
  }

  override def existsData(dynamicEntityName: String): Boolean = {
    DynamicData.findAll(By(DynamicData.DynamicEntityName, dynamicEntityName), MaxRows(1))
      .nonEmpty
  }

  private def saveOrUpdate(entityName: String, requestBody: JObject, dynamicData: => DynamicData): Box[DynamicData] = {
    val data: DynamicData = dynamicData

    val dataStr = json.compactRender(requestBody)
    tryo {
      data.DataJson(dataStr).DynamicEntityName(entityName).saveMe()
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

  override def dynamicDataId: Option[String] = Option(DynamicDataId.get)
  override def dynamicEntityName: String = DynamicEntityName.get
  override def dataJson: String = DataJson.get
}

object DynamicData extends DynamicData with LongKeyedMetaMapper[DynamicData] {
  override def dbIndexes = UniqueIndex(DynamicDataId) :: super.dbIndexes
}

