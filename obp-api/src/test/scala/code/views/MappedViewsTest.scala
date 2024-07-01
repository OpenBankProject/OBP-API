package code.views

import code.api.Constant
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
  
  val viewIdOwner = Constant.SYSTEM_OWNER_VIEW_ID
  val viewIdAccountant = "accountant"
  val viewIdAuditor = "auditor"
  val viewIdNotSupport = "NotSupport"
  
  
  feature("test some important methods in MappedViews ") {
    
    scenario("test - getOrCreateAccountView") {
      
      Given("set up four normal Views")
      var viewOwner = MapperViews.getOrCreateSystemViewFromCbs(viewIdOwner)
      var viewAccountant = MapperViews.getOrCreateSystemViewFromCbs(viewIdAccountant)
      var viewAuditor = MapperViews.getOrCreateSystemViewFromCbs(viewIdAuditor)
      var allExistingViewsForOneAccount = MapperViews.availableViewsForAccount(bankIdAccountId)
      
      Then("Check the result from database. it should have 4 views and with the right viewId")
      viewOwner.head.viewId.value should equal(Constant.SYSTEM_OWNER_VIEW_ID.toLowerCase())
      viewAccountant.head.viewId.value should equal("accountant".toLowerCase())
      viewAuditor.head.viewId.value should equal("auditor".toLowerCase())
      allExistingViewsForOneAccount.length should equal(3)
      
      Then("We set the four normal views again")
      viewOwner = MapperViews.getOrCreateSystemViewFromCbs(viewIdOwner)
      viewAccountant = MapperViews.getOrCreateSystemViewFromCbs(viewIdAccountant)
      viewAuditor = MapperViews.getOrCreateSystemViewFromCbs(viewIdAuditor)
      allExistingViewsForOneAccount = MapperViews.availableViewsForAccount(bankIdAccountId)
  
      Then("Check the result from database again. it should have four views and with the right viewId, there should be not changed.")
      viewOwner.head.viewId.value should equal(Constant.SYSTEM_OWNER_VIEW_ID.toLowerCase())
      viewAccountant.head.viewId.value should equal("accountant".toLowerCase())
      viewAuditor.head.viewId.value should equal("auditor".toLowerCase())
      allExistingViewsForOneAccount.length should equal(3)
  
  
      Then("set up four wrong View name, do not support this viewId")
      val wrongViewId = "WrongViewId"
      val wrongView = MapperViews.getOrCreateSystemViewFromCbs(wrongViewId)
  
      wrongView.toString contains  ViewIdNotSupported shouldBe (true)
      
      wrongView.toString contains  wrongViewId shouldBe(true)
  
    }
  
    
  
  }
  
  
}
