# Contributing


## Hello!

Thank you for your interest in contributing to the Open Bank Project!

## Pull requests

If submitting a pull request please read and sign our [CLA](http://github.com/OpenBankProject/OBP-API/blob/develop/Harmony_Individual_Contributor_Assignment_Agreement.txt) and send it to contact@tesobe.com - We'll send you back a code to include in the comment section of subsequent pull requests.

Please reference Issue Numbers in your commits.

## Git commit messages

Please structure git commit messages in a way as shown below:
1. bugfix/Something
2. feature/Something
3. docfix/Something
4. refactor/Something
5. performance/Something
6. test/Something

## Code comments

Please comment your code ! :-) Imagine an engineer is trying to fix a production issue: she is working on a tiny screen, via a dodgy mobile Internet connection, in a sandstorm - Your code is fresh in your mind. Your comments could help her!

## Code style

When naming variables use strict camel case e.g. use myUrl not myURL. This is so we can automatically convert from camelCase to snake_case for JSON output.

## Writing an API endpoint

```scala
    resourceDocs += ResourceDoc(
      getCustomersForUser,
      implementedInApiVersion,
      nameOf(getCustomersForUser),
      "GET",
      "/users/current/customers",
      "Get Customers for Current User",
      s"""Gets all Customers that are linked to a User.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        |""",
      emptyObjectJson,
      customerJsonV300,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagUser, apiTagNewStyle))



    // This can be considered a reference new style endpoint.
    // This is a partial function. The lazy value should have a meaningful name.
    lazy val getCustomersForUser : OBPEndpoint = {
      // This defines the URL path and method (GET) for which this partial function will accept the call.
      case "users" :: "current" :: "customers" :: Nil JsonGet _ => {
        // We have the Call Context (cc) object (provided through the OBPEndpoint type)
        // The Call Context contains the authorisation headers etc.
        cc => {
          for {
            // Extract the user from the headers and get an updated callContext
            (Full(u), callContext) <- authorizedAccess(cc)
            // Now here is the business logic.
            // Get The customers related to a user. Process the resonse which might be an Exception
            (customers,callContext) <- Connector.connector.vend.getCustomersByUserIdFuture(u.userId, callContext) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            // Create the JSON to return. We also return the callContext
            (JSONFactory300.createCustomersJson(customers), HttpCode.`200`(callContext))
          }
        }
      }
    }
```
### Recommended order of checks at an endpoint

```scala
    lazy val createProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            // 1. makes sure the user which attempts to use the endpoint is authorized
            (Full(u), callContext) <- authorizedAccess(cc)
            // 2. makes sure the user which attempts to use the endpoint is allowed to consume it 
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(bankId.value, u.userId, createProductEntitlements, callContext)
            // 3. checks the endpoint constraints
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostPutProductJsonV310 "
            ...
```
Please note that that checks at an endpoint should be applied only in case an user is authorized and has privilege to consume the endpoint. Otherwise we can reveal sensitive data to the user. For instace if we reorder the checks in next way:
```scala
            // 1. makes sure the user which attempts to use the endpoint is authorized
            (Full(u), callContext) <- authorizedAccess(cc)
            // 3. checks the endpoint constraints
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostPutProductJsonV310 "      
            (Full(u), callContext) <- authorizedAccess(cc)
            // 2. makes sure the user which attempts to use the endpoint is allowed to consume it 
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(bankId.value, u.userId, createProductEntitlements, callContext)   
```
the user which cannot consume the endpoint still can check does some bank exist or not at that instance. It's not the issue if banks are public data at the instance but it wouldn't be the only business case all the time.

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

## Code Generation
We support to generate the OBP-API code from the following three types of json. You can choose one of them as your own requirements. 

    1 Choose one of the following types: type1 or type2 or type3
    2 Modify the json file your selected, for now, we only support these three types: String, Double, Int. other types may throw the exceptions
    3 Run the Main method according to your json file
    4 Run/Restart OBP-API project.
    5 Run API_Exploer project to test your new APIs. (click the Tag `APIBuilder B1)

Here are the three types: 

Type1: If you use `modelSource.json`, please run `APIBuilderModel.scala` main method
```
/OBP-API/obp-api/src/main/resources/apiBuilder/APIModelSource.json
/OBP-API/obp-api/src/main/scala/code/api/APIBuilder/APIBuilderModel.scala
```
Type2: If you use `apisResource.json`, please run `APIBuilder.scala` main method
```
/OBP-API/obp-api/src/main/resources/apiBuilder/apisResource.json
OBP-API/src/main/scala/code/api/APIBuilder/APIBuilder.scala
```
Type3: If you use `swaggerResource.json`, please run `APIBuilderSwagger.scala` main method
```
/OBP-API/obp-api/src/main/resources/apiBuilder/swaggerResource.json
OBP-API/src/main/scala/code/api/APIBuilder/APIBuilderSwagger.scala
```

## Issues

If would like to report an issue or suggest any kind of improvement please use Github Issues.

## Licenses

Open Bank Project API, API Explorer and Sofit are dual licenced under the AGPL and commercial licenses. Open Bank Project SDKs are licenced under Apache 2 or MIT style licences.

Please see the NOTICE for each project licence.

## Setup and Tests

See the README for instructions on setup and running the tests :-)

Welcome!
