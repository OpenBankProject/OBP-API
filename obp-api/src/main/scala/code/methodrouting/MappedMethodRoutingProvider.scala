package code.methodrouting

import code.api.util.CustomJsonFormats
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, EmptyBox, Full}
import net.liftweb.json
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils
import net.liftweb.json.Serialization.write

object MappedMethodRoutingProvider extends MethodRoutingProvider with CustomJsonFormats{

  override def getById(methodRoutingId: String): Box[MethodRoutingT] =  MethodRouting.find(
    By(MethodRouting.MethodRoutingId, methodRoutingId)
  )

  override def getMethodRoutings(methodName: Option[String], isBankIdExactMatch: Option[Boolean] = None, bankIdPattern: Option[String] = None): List[MethodRouting] = {

    val byMethodName = methodName.map(By(MethodRouting.MethodName, _))
    val byIsBankIdExactMatch = isBankIdExactMatch.map(By(MethodRouting.IsBankIdExactMatch, _))
    val byBankIdPattern = bankIdPattern.map(By(MethodRouting.BankIdPattern, _))

    val queryParam: Seq[QueryParam[MethodRouting]] = List(byMethodName, byIsBankIdExactMatch, byBankIdPattern).collect {
      case Some(by) => by
    }

    MethodRouting.findAll(queryParam :_*)
  }

  override def createOrUpdate(methodRouting: MethodRoutingT): Box[MethodRoutingT] = {

    val bankIdPattern = methodRouting.bankIdPattern
                          .filter(StringUtils.isNotBlank) // treat blank string as not supplied

    //to find exists methodRouting, if methodRoutingId supplied, query by methodRoutingId, or use methodName and methodRoutingId to do query
    val existsMethodRouting: Box[MethodRouting] = methodRouting.methodRoutingId match {
      case Some(id) if (StringUtils.isNotBlank(id)) => getByMethodRoutingId(id)
      case _ => Empty
    }
    val entityToPersist = existsMethodRouting match {
      case _: EmptyBox => MethodRouting.create
      case Full(methodRouting) => methodRouting
    }
    // if not supply bankIdPattern, isExactMatch must be false
    val isExactMatch = if(bankIdPattern.isDefined) methodRouting.isBankIdExactMatch else false

    tryo{
      entityToPersist
        .MethodName(methodRouting.methodName)
        .BankIdPattern(bankIdPattern.orNull)
        .IsBankIdExactMatch(isExactMatch)
        .ConnectorName(methodRouting.connectorName)
        .Parameters(write(methodRouting.parameters))
        .saveMe()
    }
  }


  override def delete(methodRoutingId: String): Box[Boolean] = getByMethodRoutingId(methodRoutingId).map(_.delete_!)

  private[this] def getByMethodRoutingId(methodRoutingId: String): Box[MethodRouting] = MethodRouting.find(By(MethodRouting.MethodRoutingId, methodRoutingId))

}

class MethodRouting extends MethodRoutingT with LongKeyedMapper[MethodRouting] with IdPK with CustomJsonFormats{

  override def getSingleton = MethodRouting

  object MethodRoutingId extends MappedUUID(this)
  object MethodName extends MappedString(this, 255)
  object BankIdPattern extends MappedString(this, 255){
    override def defaultValue: String = MethodRouting.bankIdPatternMatchAny
  }
  object IsBankIdExactMatch extends MappedBoolean(this)
  object ConnectorName extends MappedString(this, 255)
  object Parameters extends MappedString(this, 5000)

  override def methodRoutingId: Option[String] = Option(MethodRoutingId.get)
  override def methodName: String = MethodName.get
  override def bankIdPattern: Option[String] = Option(BankIdPattern.get)
  override def isBankIdExactMatch: Boolean = IsBankIdExactMatch.get
  override def connectorName: String = ConnectorName.get

  //Here we store all the key-value paris in one big String filed in database. 
  override def parameters: Option[List[MethodRoutingParam]] = Option(json.parse(Parameters.get).extract[List[MethodRoutingParam]])
}

object MethodRouting extends MethodRouting with LongKeyedMetaMapper[MethodRouting] {
  override def dbIndexes = UniqueIndex(MethodRoutingId) :: super.dbIndexes

  /**
    * default bankIdPattern is match any
    */
  val bankIdPatternMatchAny: String = ".*"
}

