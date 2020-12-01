package code.DynamicData

import code.api.util.CustomJsonFormats
import code.util.MappedUUID
import net.liftweb.common.Box
import net.liftweb.json
import net.liftweb.json.JObject
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.JsonDSL._
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

object MappedDynamicDataProvider extends DynamicDataProvider with CustomJsonFormats{
  override def save(entityName: String, requestBody: JObject): Box[DynamicData] = {
    val idName = getIdName(entityName)
    val JString(idValue) = (requestBody \ idName).asInstanceOf[JString]
    val dynamicData: DynamicData = DynamicData.create.DynamicDataId(idValue)
    val result = saveOrUpdate(entityName, requestBody, dynamicData)
    result
  }
  override def update(entityName: String, requestBody: JObject, id: String): Box[DynamicData] = {
    val dynamicData: DynamicData = get(entityName, id).openOrThrowException(s"not exists DynamicData's data of dynamicEntityName=$entityName, dynameicDataId=$id")
    saveOrUpdate(entityName, requestBody, dynamicData)
  }

  override def get(entityName: String, id: String): Box[DynamicData] = DynamicData.find(By(DynamicData.DynamicDataId, id), By(DynamicData.DynamicEntityName, entityName))

  override def getAll(entityName: String): List[JObject] = DynamicData.findAll(By(DynamicData.DynamicEntityName, entityName))
    .map(it => json.parse(it.dataJson)).map(_.asInstanceOf[JObject])

  override def delete(entityName: String, id: String): Boolean = DynamicData.bulkDelete_!!(By(DynamicData.DynamicDataId, id), By(DynamicData.DynamicEntityName, entityName))

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

