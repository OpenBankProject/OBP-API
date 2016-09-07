package code.api

//a trait that grants obpuser1 from DefaultUsers access to all views before each test
trait User1AllPrivileges extends ServerSetupWithTestData {
  self : DefaultUsers =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    super.grantAccessToAllExistingViews(obpuser1)
  }
}
