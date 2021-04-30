package code.endpointRouting

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

object MappedEndpointRoutingProvider extends EndpointRoutingProvider with CustomJsonFormats{

  override def getById(endpointRoutingId: String): Box[EndpointRoutingT] =  getByEndpointRoutingId(endpointRoutingId)

  override def createOrUpdate(endpointRouting: EndpointRoutingT): Box[EndpointRoutingT] = {
    //to find exists endpointRouting, if endpointRoutingId supplied, query by endpointRoutingId, or use endpointName and endpointRoutingId to do query
    val existsEndpointRouting: Box[EndpointRouting] = endpointRouting.endpointRoutingId match {
      case Some(id) if (StringUtils.isNotBlank(id)) => getByEndpointRoutingId(id)
      case _ => Empty
    }
    val entityToPersist = existsEndpointRouting match {
      case _: EmptyBox => EndpointRouting.create
      case Full(endpointRouting) => endpointRouting
    }
    
    tryo{
      entityToPersist
        .OperationId(endpointRouting.operationId)
        .RequestMapping(endpointRouting.requestMapping)
        .ResponseMapping(endpointRouting.responseMapping)
        .saveMe()
    }
  }


  override def delete(endpointRoutingId: String): Box[Boolean] = getByEndpointRoutingId(endpointRoutingId).map(_.delete_!)

  private[this] def getByEndpointRoutingId(endpointRoutingId: String): Box[EndpointRouting] = EndpointRouting.find(By(EndpointRouting.EndpointRoutingId, endpointRoutingId))

  override def getByEndpointRoutings: List[EndpointRoutingT] = EndpointRouting.findAll()

}

class EndpointRouting extends EndpointRoutingT with LongKeyedMapper[EndpointRouting] with IdPK with CustomJsonFormats{

  override def getSingleton = EndpointRouting

  object EndpointRoutingId extends MappedUUID(this)
  object OperationId extends MappedString(this, 255)
  object RequestMapping extends MappedString(this, 255)
  object ResponseMapping extends MappedString(this, 255)

  override def endpointRoutingId: Option[String] = Option(EndpointRoutingId.get)
  override def operationId: String = OperationId.get
  override def requestMapping: String = RequestMapping.get
  override def responseMapping: String = ResponseMapping.get
}

object EndpointRouting extends EndpointRouting with LongKeyedMetaMapper[EndpointRouting] {
  override def dbIndexes = UniqueIndex(EndpointRoutingId) ::UniqueIndex(OperationId) :: super.dbIndexes
}

