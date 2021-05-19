package code.endpointMapping

import code.api.util.CustomJsonFormats
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, EmptyBox, Full}
import net.liftweb.json
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils
import net.liftweb.json.Serialization.write
import com.openbankproject.commons.util.Functions.Implicits._
import net.liftweb.json.JsonAST.JArray

object MappedEndpointMappingProvider extends EndpointMappingProvider with CustomJsonFormats{

  override def getById(endpointMappingId: String): Box[EndpointMappingT] = getByEndpointMappingId(endpointMappingId)
  
  override def getByOperationId(operationId: String): Box[EndpointMappingT] = EndpointMapping.find(By(EndpointMapping.OperationId, operationId))

  override def createOrUpdate(endpointMapping: EndpointMappingT): Box[EndpointMappingT] = {
    //to find exists endpointMapping, if endpointMappingId supplied, query by endpointMappingId, or use endpointName and endpointMappingId to do query
    val existsEndpointMapping: Box[EndpointMapping] = endpointMapping.endpointMappingId match {
      case Some(id) if (StringUtils.isNotBlank(id)) => getByEndpointMappingId(id)
      case _ => Empty
    }
    val entityToPersist = existsEndpointMapping match {
      case _: EmptyBox => EndpointMapping.create
      case Full(endpointMapping) => endpointMapping
    }
    
    tryo{
      entityToPersist
        .OperationId(endpointMapping.operationId)
        .RequestMapping(endpointMapping.requestMapping)
        .ResponseMapping(endpointMapping.responseMapping)
        .saveMe()
    }
  }

  override def delete(endpointMappingId: String): Box[Boolean] = getByEndpointMappingId(endpointMappingId).map(_.delete_!)

  private[this] def getByEndpointMappingId(endpointMappingId: String): Box[EndpointMapping] = EndpointMapping.find(By(EndpointMapping.EndpointMappingId, endpointMappingId))

  override def getAllEndpointMappings(): List[EndpointMappingT] = EndpointMapping.findAll()

}

class EndpointMapping extends EndpointMappingT with LongKeyedMapper[EndpointMapping] with IdPK with CustomJsonFormats{

  override def getSingleton = EndpointMapping

  object EndpointMappingId extends MappedUUID(this)
  object OperationId extends MappedString(this, 255)
  object RequestMapping extends MappedText(this)
  object ResponseMapping extends MappedText(this)

  override def endpointMappingId: Option[String] = Option(EndpointMappingId.get)
  override def operationId: String = OperationId.get
  override def requestMapping: String = RequestMapping.get
  override def responseMapping: String = ResponseMapping.get
}

object EndpointMapping extends EndpointMapping with LongKeyedMetaMapper[EndpointMapping] {
  override def dbIndexes = UniqueIndex(EndpointMappingId) ::UniqueIndex(OperationId) :: super.dbIndexes
}

