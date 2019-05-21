package code.methodrouting

/* For ConnectorMethod */

import com.openbankproject.commons.model.BankId
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object MethodRouting extends SimpleInjector {

  val connectorMethodProvider = new Inject(buildOne _) {}

  def buildOne: MappedMethodRoutingProvider.type = MappedMethodRoutingProvider
}

trait MethodRoutingT {
  def methodRoutingId: String
  def methodName: String
  def bankIdPattern: String
  def connectorName: String
}

trait MethodRoutingProvider {

  def getByMethodName(methodName: String) : Seq[MethodRoutingT]

  def getByMethodNameAndBankId(methodName: String, bankId: String) : Box[MethodRoutingT]

  def createOrUpdate(methodName: String, bankIdPattern: String, connectorName: String, methodRoutingId: Option[String]= None):Box[MethodRoutingT]

  def delete(methodRoutingId: String):Box[Boolean]
}






