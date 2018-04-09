package code.setup

//a trait that grants resourceUser1 from DefaultUsers access to all views before each test
trait User1AllPrivilegesAsync extends ServerSetupWithTestDataAsync {
  self : DefaultUsers =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    super.grantAccessToAllExistingViews(resourceUser1)
  }
}
