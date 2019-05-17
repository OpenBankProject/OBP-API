package code.connector

import code.accountholders.MapperAccountHolders
import code.bankconnectors.Connector
import code.setup.{DefaultUsers, ServerSetup}
import code.views.system.ViewDefinition

class June2017Test extends ServerSetup with DefaultUsers {

  
  override def beforeAll() = {
    super.beforeAll()
    Connector.connector.default.set(MockedJune2017Connector)
    ViewDefinition.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
  }
  
  override def afterEach() = {
    super.afterEach()
    Connector.connector.default.set(Connector.buildOne)
    ViewDefinition.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
  }
  
}
