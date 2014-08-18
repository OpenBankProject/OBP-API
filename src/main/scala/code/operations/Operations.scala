package code.operations

import code.model.User
import code.model.operations.Operation
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object Operations extends SimpleInjector {

  val operations = new Inject(buildOne _) {}

  def buildOne: Operations = MappedOperations

}

trait Operations {
  def getOperation(operationId : String, user : Box[User]) : Box[Operation]
}