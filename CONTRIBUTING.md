# Contributing


## Hello!

Thank you for your interest in contributing to the Open Bank Project!

## Pull requests

If submitting a pull request please read and sign our [CLA](http://github.com/OpenBankProject/OBP-API/blob/develop/Harmony_Individual_Contributor_Assignment_Agreement.txt) and send it to contact@tesobe.com - We'll send you back a code to include in the comment section of subsequent pull requests.

Please reference Issue Numbers in your commits.

## Code comments

Please comment your code ! :-) Imagine an engineer is trying to fix a production issue: she is working on a tiny screen, via a dodgy mobile Internet connection, in a sandstorm - Your code is fresh in your mind. Your comments could help her!

## Code style

When naming variables use strict camel case e.g. use myUrl not myURL. This is so we can automatically convert from camelCase to snake_case for JSON output.

## Writing tests

When you write a test for an endpoint please tag it with a version and the endpoint.
An example of how to tag tests:
```scala
class FundsAvailableTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations3_1_0.checkFundsAvailable))

  feature("Check available funds v3.1.0 - Unauthorized access")
  {
    scenario("We will check available without user credentials", ApiEndpoint, VersionOfApi) {
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.bank_id / view / "funds-available").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
  }

}
``` 

## Issues

If would like to report an issue or suggest any kind of improvement please use Github Issues.

## Licenses

Open Bank Project API, API Explorer and Sofit are dual licenced under the AGPL and commercial licenses. Open Bank Project SDKs are licenced under Apache 2 or MIT style licences.

Please see the NOTICE for each project licence.

## Setup and Tests

See the README for instructions on setup and running the tests :-)

Welcome!
