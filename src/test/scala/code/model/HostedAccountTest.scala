package test.code.model

import org.scalatest._
import net.liftweb.common.Loggable

class HostedAccountTest extends FeatureSpec
  with BeforeAndAfterEach with GivenWhenThen
  with ShouldMatchers with Loggable{

  object Current extends Tag("current")
  feature("Unique Hosted Account"){
    scenario("We can save two Hosted Accounts with different account numbers", Current) {
      import code.model.dataAccess.HostedAccount
      import net.liftweb.util.Helpers.{randomString, tryo}

      Given(s"We will save a Hosted Account with a random accountID")
        HostedAccount
        .create
        .accountID(randomString(5))
        .saveMe
      When("We try to save an other Hosted Account with the a different accountID ")
      val hostedAccount =
        HostedAccount
        .create
        .accountID(randomString(5))
      tryo{
        hostedAccount.saveMe
      }
      Then("it should be saved")
      hostedAccount.saved_? should equal (true)
    }
    scenario("We cannot save two Hosted Accounts with the same account number") {
      import code.model.dataAccess.HostedAccount
      import net.liftweb.util.Helpers.{randomString, tryo}

      val accountID = randomString(5)
      Given(s"We will save a Hosted Account with accountID set to $accountID")
        HostedAccount
        .create
        .accountID(accountID)
        .saveMe
      When("We try to save an other Hosted Account with the same accountID field")
      val hostedAccount =
        HostedAccount
        .create
        .accountID(accountID)
      tryo{
        hostedAccount.saveMe
      }
      Then("we should get an exception")
      hostedAccount.saved_? should equal (false)
    }
  }
}