package code.api

import code.api.test.ServerSetup
import code.model.dataAccess.{ViewPrivileges, ViewImpl}

//a trait that grants obpuser1 from DefaultUsers access to all views before each test
trait User1AllPrivileges extends ServerSetup {
  self : DefaultUsers =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    for{
      v <- ViewImpl.findAll()
    }{
      ViewPrivileges.create.
        view(v).
        user(obpuser1).
        save
    }
  }
}
