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

  override def getById(bankId: Option[String], endpointMappingId: String): Box[EndpointMappingT] = { 
    if (bankId.isEmpty) getByEndpointMappingId(endpointMappingId)
    else getByEndpointMappingId(bankId.getOrElse(""), endpointMappingId)
  }

  override def getByOperationId(bankId: Option[String], operationId: String): Box[EndpointMappingT] = {
    if (bankId.isEmpty) EndpointMapping.find(By(EndpointMapping.OperationId, operationId))
    else EndpointMapping.find(
      By(EndpointMapping.OperationId, operationId),
      By(EndpointMapping.BankId, bankId.getOrElse(""))
    )
  }

  override def createOrUpdate(bankId: Option[String], endpointMapping: EndpointMappingT): Box[EndpointMappingT] = {
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
        .BankId(endpointMapping.bankId.getOrElse(null))
        .saveMe()
    }
  }

  override def delete(bankId: Option[String], endpointMappingId: String): Box[Boolean] = 
    if (bankId.isEmpty) getByEndpointMappingId(endpointMappingId).map(_.delete_!)
    else getByEndpointMappingId(bankId.getOrElse(""),endpointMappingId).map(_.delete_!)

  private[this] def getByEndpointMappingId(endpointMappingId: String): Box[EndpointMapping] = EndpointMapping.find(By(EndpointMapping.EndpointMappingId, endpointMappingId))
  private[this] def getByEndpointMappingId(bankId: String, endpointMappingId: String): Box[EndpointMapping] = EndpointMapping.find(
    By(EndpointMapping.EndpointMappingId, endpointMappingId),
    By(EndpointMapping.BankId, bankId),
  )

  override def getAllEndpointMappings(bankId: Option[String]): List[EndpointMappingT] = 
    if (bankId.isEmpty) EndpointMapping.findAll() 
    else EndpointMapping.findAll(By(EndpointMapping.BankId, bankId.getOrElse("")))
}

class EndpointMapping extends EndpointMappingT with LongKeyedMapper[EndpointMapping] with IdPK with CustomJsonFormats{

  override def getSingleton = EndpointMapping

  object EndpointMappingId extends MappedUUID(this)
  object OperationId extends MappedString(this, 255)
  object RequestMapping extends MappedText(this)
  object ResponseMapping extends MappedText(this)
  object BankId extends MappedString(this, 255)

  override def endpointMappingId: Option[String] = Option(EndpointMappingId.get)
  override def operationId: String = OperationId.get
  override def requestMapping: String = RequestMapping.get
  override def responseMapping: String = ResponseMapping.get
  override def bankId: Option[String] = if (BankId.get == null || BankId.get.isEmpty) None else Some(BankId.get)
}

object EndpointMapping extends EndpointMapping with LongKeyedMetaMapper[EndpointMapping] {
  override def dbIndexes = UniqueIndex(EndpointMappingId) ::UniqueIndex(OperationId) :: super.dbIndexes
}

