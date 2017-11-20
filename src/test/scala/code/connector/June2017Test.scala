package code.connector

import code.accountholder.MapperAccountHolders
import code.bankconnectors.{Connector}
import code.model.dataAccess.{ViewImpl}
import code.setup.{DefaultUsers, ServerSetup}

class June2017Test extends ServerSetup with DefaultUsers {

  
  override def beforeAll() = {
    super.beforeAll()
    Connector.connector.default.set(MockedJune2017Connector)
    ViewImpl.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
  }
  
  override def afterEach() = {
    super.afterEach()
    Connector.connector.default.set(Connector.buildOne)
    ViewImpl.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
  }
  
}
