package code.methodrouting

/* For Connector method routing, star connector use this provider to find proxy connector name */

import com.openbankproject.commons.model.{Converter, ProductCollection, ProductCollectionCommons}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object MethodRoutingProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(buildOne _) {}

  def buildOne: MappedMethodRoutingProvider.type = MappedMethodRoutingProvider
}

trait MethodRoutingT {
  def methodRoutingId: Option[String]
  def methodName: String
  def bankIdPattern: Option[String]

  /**
    * whether bankIdPattern is exact match the bankId value, or regex expression match
    * @return true if exact match, false if regex match
    */
  def isBankIdExactMatch: Boolean
  def connectorName: String
}

case class MethodRoutingCommons(methodName: String,
                                connectorName: String,
                                isBankIdExactMatch: Boolean,
                                bankIdPattern: Option[String],
                                methodRoutingId: Option[String] = None
                               ) extends MethodRoutingT

object MethodRoutingCommons extends Converter[MethodRoutingT, MethodRoutingCommons]

trait MethodRoutingProvider {
  def getByMethodNameAndBankId(methodName: String, bankId: String) : Box[MethodRoutingT]

  def getByMethodNameAndFuzzyMatchBankId(methodName: String) : Seq[MethodRoutingT]

  def getByMethodName(methodName: String) : Seq[MethodRoutingT]

  def createOrUpdate(methodRouting: MethodRoutingT): Box[MethodRoutingT]

  def delete(methodRoutingId: String):Box[Boolean]
}






