package code.endpointTag

import code.api.util.CustomJsonFormats
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, EmptyBox, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

object MappedEndpointTagProvider extends EndpointTagProvider with CustomJsonFormats{

  override def getById(endpointTagId: String): Box[EndpointTagT] = { 
   getByEndpointTagId(endpointTagId)
  }

  override def getByOperationId(operationId: String): Box[EndpointTagT] = {
    EndpointTag.find(By(EndpointTag.OperationId, operationId))
  }

  override def createOrUpdate(endpointTag: EndpointTagT): Box[EndpointTagT] = {
    //to find exists endpointTag, if endpointTagId supplied, query by endpointTagId, or use endpointName and endpointTagId to do query
    val existsEndpointTag: Box[EndpointTag] = endpointTag.endpointTagId match {
      case Some(id) if (StringUtils.isNotBlank(id)) => getByEndpointTagId(id)
      case _ => Empty
    }
    val entityToPersist = existsEndpointTag match {
      case _: EmptyBox => EndpointTag.create
      case Full(endpointTag) => endpointTag
    }
    
    tryo{
      entityToPersist
        .OperationId(endpointTag.operationId)
        .TagName(endpointTag.tagName)
        .saveMe()
    }
  }

  override def delete(endpointTagId: String): Box[Boolean] = getByEndpointTagId(endpointTagId).map(_.delete_!)

  private[this] def getByEndpointTagId(endpointTagId: String): Box[EndpointTag] = EndpointTag.find(By(EndpointTag.EndpointTagId, endpointTagId))

  override def getAllEndpointTags: List[EndpointTagT] = EndpointTag.findAll() 
}

class EndpointTag extends EndpointTagT with LongKeyedMapper[EndpointTag] with IdPK with CreatedUpdated with CustomJsonFormats{

  override def getSingleton = EndpointTag

  object EndpointTagId extends MappedUUID(this)
  object OperationId extends MappedString(this, 255)
  object TagName extends MappedString(this, 255)
  object BankId extends MappedString(this, 255)

  override def endpointTagId: Option[String] = Option(EndpointTagId.get)
  override def operationId: String = OperationId.get
  override def tagName: String = TagName.get
  override def bankId: Option[String] = if (BankId.get == null || BankId.get.isEmpty) None else Some(BankId.get) 
}

object EndpointTag extends EndpointTag with LongKeyedMetaMapper[EndpointTag] {
  override def dbIndexes = UniqueIndex(EndpointTagId) ::super.dbIndexes
}