package code.views

import code.api.util.ErrorMessages.ViewIdNotSupported
import code.setup.{DefaultUsers, ServerSetup}
import code.views.system.ViewDefinition
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId}
import net.liftweb.common.Failure

class MappedViewsTest extends ServerSetup with DefaultUsers{
  
  override def beforeAll() = {
    super.beforeAll()
    ViewDefinition.bulkDelete_!!()
  }
  
  override def afterEach() = {
    super.afterEach()
    ViewDefinition.bulkDelete_!!()
  }
  
  val bankIdAccountId = BankIdAccountId(BankId("1"),AccountId("2"))
  
  val viewIdOwner = "owner"
  val viewIdPublic = "_public"
  val viewIdAccountant = "accountant"
  val viewIdAuditor = "auditor"
  val viewIdNotSupport = "NotSupport"
  
  
  feature("test some important methods in MappedViews ") {
    
    scenario("test - getOrCreateAccountView") {
      
      Given("set up four normal Views")
      var viewOwner = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdOwner)
      var viewPublic = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdPublic)
      var viewAccountant = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdAccountant)
      var viewAuditor = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdAuditor)
      var allExistingViewsForOneAccount = MapperViews.availableViewsForAccount(bankIdAccountId)
      
      Then("Check the result from database. it should have 4 views and with the right viewId")
      viewOwner.head.viewId.value should equal("owner".toLowerCase())
      viewPublic.head.viewId.value should equal("_public".toLowerCase())
      viewAccountant.head.viewId.value should equal("accountant".toLowerCase())
      viewAuditor.head.viewId.value should equal("auditor".toLowerCase())
      allExistingViewsForOneAccount.length should equal(4)
      
      Then("We set the four normal views again")
      viewOwner = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdOwner)
      viewPublic = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdPublic)
      viewAccountant = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdAccountant)
      viewAuditor = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdAuditor)
      allExistingViewsForOneAccount = MapperViews.availableViewsForAccount(bankIdAccountId)
  
      Then("Check the result from database again. it should have four views and with the right viewId, there should be not changed.")
      viewOwner.head.viewId.value should equal("owner".toLowerCase())
      viewPublic.head.viewId.value should equal("_public".toLowerCase())
      viewAccountant.head.viewId.value should equal("accountant".toLowerCase())
      viewAuditor.head.viewId.value should equal("auditor".toLowerCase())
      allExistingViewsForOneAccount.length should equal(4)
  
  
      Then("set up four wrong View name, do not support this viewId")
      val wrongViewId = "WrongViewId"
      val wrongView = MapperViews.getOrCreateAccountView(bankIdAccountId, wrongViewId)
  
      wrongView should equal(Failure(ViewIdNotSupported+ s"Your input viewId is :$wrongViewId"))
  
    }
  
    
  
  }
  
  
}
