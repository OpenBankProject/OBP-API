package code.methodrouting

import code.util.MappedUUID
import com.openbankproject.commons.model.BankId
import net.liftweb.common.{Box, EmptyBox, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable

object MappedMethodRoutingProvider extends MethodRoutingProvider {

  override def getByMethodName(methodName: String): immutable.Seq[MethodRoutingT] = MappedMethodRouting.findAll {
    By(MappedMethodRouting.mMethodRoutingId, methodName)
  }

  private[this] def getByMethodRoutingId(methodRoutingId: String): Box[MappedMethodRouting] = MappedMethodRouting.find(By(MappedMethodRouting.mMethodRoutingId, methodRoutingId))

  override def createOrUpdate(methodName: String, bankIdPattern: String, connectorName: String, methodRoutingId: Option[String]): Box[MethodRoutingT] = {
    //to find exists methodRouting, if methodRoutingId supplied, query by methodRoutingId, or use methodName and methodRoutingId to do query
    val existsMethodRouting: Box[MappedMethodRouting] = methodRoutingId match {
      case Some(id) => getByMethodRoutingId(id)
      case None => MappedMethodRouting.find(
        By(MappedMethodRouting.mMethodName, methodName),
        By(MappedMethodRouting.mBankIdPattern, bankIdPattern)
      )
    }
    existsMethodRouting match {
      case _: EmptyBox => tryo {
        MappedMethodRouting.mMethodName(methodName).mBankIdPattern(bankIdPattern).mConnectorName(connectorName).saveMe()
      }
      case Full(methodRouting) => tryo{methodRouting.saveMe()}
    }
  }


  override def delete(methodRoutingId: String): Box[Boolean] = getByMethodRoutingId(methodRoutingId).map(_.delete_!)

  override def getByMethodNameAndBankId(methodName: String, bankId: String): Box[MethodRoutingT] = MappedMethodRouting.find(
      By(MappedMethodRouting.mMethodName, methodName),
      By(MappedMethodRouting.mBankIdPattern, bankId)
    )
  }

class MappedMethodRouting extends MethodRoutingT with LongKeyedMapper[MappedMethodRouting] with IdPK {

  override def getSingleton = MappedMethodRouting
  object mMethodRoutingId extends MappedUUID(this)
  object mMethodName extends MappedString(this, 255)
  object mBankIdPattern extends MappedString(this, 255)
  object mConnectorName extends MappedString(this, 255)

  override def methodRoutingId = mMethodRoutingId.get
  override def methodName: String = mMethodName.get
  override def bankIdPattern: String = mBankIdPattern.get

  override def connectorName: String = mConnectorName.get
}

object MappedMethodRouting extends MappedMethodRouting with LongKeyedMetaMapper[MappedMethodRouting] {
  override def dbIndexes = UniqueIndex(mMethodRoutingId) :: super.dbIndexes
}

