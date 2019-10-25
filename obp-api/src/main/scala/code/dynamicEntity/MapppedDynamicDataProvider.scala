package code.DynamicData

import code.api.util.APIUtil.generateUUID
import code.api.util.CustomJsonFormats
import code.util.MappedUUID
import net.liftweb.common.Box
import net.liftweb.json
import net.liftweb.json.JObject
import net.liftweb.json.JsonDSL._
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

object MappedDynamicDataProvider extends DynamicDataProvider with CustomJsonFormats{
  override def saveOrUpdate(entityName: String, requestBody: JObject, id: Option[String]): Box[DynamicData] = {
    val idValue: String = id.getOrElse(generateUUID())
    val dynamicData: DynamicData = id match{
      case Some(i) => get(entityName, i).openOrThrowException(s"not exists DynamicData's data of dynamicEntityName=$entityName, dynameicDataId=$i")
      case _ => DynamicData.create.DynamicDataId(idValue).DynamicEntityName(entityName)
    }
    val idName = StringUtils.uncapitalize(entityName) + "Id"

    val dataToPersist: JObject = requestBody ~ (idName -> idValue)
    val dataStr = json.compactRender(dataToPersist)
    tryo {
      dynamicData.DataJson(dataStr).saveMe()
    }
  }

  override def get(entityName: String, id: String): Box[DynamicData] = DynamicData.find(By(DynamicData.DynamicDataId, id))

  override def getAll(entityName: String): List[JObject] = DynamicData.findAll(By(DynamicData.DynamicEntityName, entityName))
    .map(it => json.parse(it.dataJson)).map(_.asInstanceOf[JObject])

  override def delete(entityName: String, id: String): Boolean = DynamicData.bulkDelete_!!(By(DynamicData.DynamicDataId, id))

  override def existsData(dynamicEntityName: String): Boolean = {
    DynamicData.findAll(By(DynamicData.DynamicEntityName, dynamicEntityName), MaxRows(1))
      .nonEmpty
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

