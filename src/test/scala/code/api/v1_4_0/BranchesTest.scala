package code.api.v1_4_0

import code.api.v1_4_0.JSONFactory1_4_0.{BranchJson, BranchesJson}
import code.api.util.APIUtil.OAuth._
import dispatch._
import code.common.{Address, License, Location, Meta}
import code.branches.Branches.{Branch, BranchId, DriveUp, Lobby}
import code.branches.{Branches, BranchesProvider}
import code.model.BankId
import code.setup.DefaultUsers

/*
Note This does not test retrieval from a backend.
We mock the backend so get test the API
 */
class BranchesTest extends V140ServerSetup with DefaultUsers {

  val BankWithLicense = BankId("testBank1")
  val BankWithoutLicense = BankId("testBank2")

  // Have to repeat the constructor parameters from the trait
  case class BranchImpl(
    branchId: BranchId,
    bankId: BankId,
    name: String,
    address: Address,
    location: Location,
    meta: Meta,
    lobby: Lobby,
    driveUp: DriveUp,
    branchRoutingScheme: String,
    branchRoutingAddress: String
  ) extends Branch
  case class AddressImpl(line1 : String, line2 : String, line3 : String, city : String, county : String,
                         state : String, postCode : String, countryCode : String) extends Address


  val fakeAddress1 = AddressImpl("Dunckerstraße 73 ApS", "Udemarken", "Hjørring", "Berlin", "Denmark", "Denmark", "10437", "DE")
  val fakeAddress2 = fakeAddress1.copy(line1 = "00000")

  val fakeMeta = new Meta {
    val license = new License {
      override def id: String = "sample-license"
      override def name: String = "Sample License"
    }
  }

  val fakeMetaNoLicense = new Meta {
    val license = new License {
      override def id: String = ""
      override def name: String = ""
    }
  }

  val fakeLocation = new Location {
   override def latitude: Double = 1.11
   override def longitude: Double = 2.22
  }


  val fakeLocation2 = new Location {
    override def latitude: Double = 1.1111
    override def longitude: Double = 2.2222
  }


  val fakeLobby = new Lobby {
   val hours = "M-Th 9-5, Fri 9-6, Sat 9-1"
  }


  val fakeLobby2 = new Lobby {
    val hours = "9-5"
  }

  val fakeDriveUp = new DriveUp {
    override def hours: String = "M-Th 8:30 - 5:30, Fri 8:30 - 6, Sat: 9-12"
  }


  val fakeDriveUp2 = new DriveUp {
    override def hours: String = "M-Th 8:30 - 5:30"
  }

  val fakeBranch1 = BranchImpl(BranchId("branch1"), BankId("uk"),"Branch 1 Müdürlük", fakeAddress1, fakeLocation, fakeMeta, fakeLobby, fakeDriveUp,"","")
  val fakeBranch2 = BranchImpl(BranchId("branch2"), BankId("uk"), "Branch 2 Lala", fakeAddress2, fakeLocation2, fakeMeta, fakeLobby2, fakeDriveUp2,"","")
  val fakeBranch3 = BranchImpl(BranchId("branch3"), BankId("uk"), "Branch 3", fakeAddress2, fakeLocation, fakeMetaNoLicense, fakeLobby, fakeDriveUp2,"","") // Should not be returned

  // This mock provider is returning same branches for the fake banks
  val mockConnector = new BranchesProvider {
    override protected def getBranchesFromProvider(bank: BankId): Option[List[Branch]] = {
      bank match {
        // have it return branches even for the bank without a license so we can test the API does not return them
        case BankWithLicense | BankWithoutLicense=> Some(List(fakeBranch1, fakeBranch2, fakeBranch3))
        case _ => None
      }
    }

    // Mock a badly behaving connector that returns data that doesn't have license.
    override protected def getBranchFromProvider(branchId: BranchId): Option[Branch] = {
      branchId match {
         case BankWithLicense => Some(fakeBranch1)
         case BankWithoutLicense=> Some(fakeBranch3) // In case the connector returns, the API must guard
        case _ => None
      }
    }

  }

  def verifySameData(branch: Branch, branchJson : BranchJson) = {
    branch.name must equal (branchJson.name)
    branch.branchId must equal(BranchId(branchJson.id))
    branch.address.line1 must equal(branchJson.address.line_1)
    branch.address.line2 must equal(branchJson.address.line_2)
    branch.address.line3 must equal(branchJson.address.line_3)
    branch.address.city must equal(branchJson.address.city)
    branch.address.state must equal(branchJson.address.state)
    branch.address.countryCode must equal(branchJson.address.country)
    branch.address.postCode must equal(branchJson.address.postcode)
    branch.location.latitude must equal(branchJson.location.latitude)
    branch.location.longitude must equal(branchJson.location.longitude)
    branch.lobby.hours must equal(branchJson.lobby.hours)
    branch.driveUp.hours must equal(branchJson.drive_up.hours)
  }

  /*
  So we can test the API layer, rather than the connector, use a mock connector.
   */
  override def beforeAll() {
    super.beforeAll()
    //use the mock connector
    Branches.branchesProvider.default.set(mockConnector)
  }

  override def afterAll() {
    super.afterAll()
    //reset the default connector
    Branches.branchesProvider.default.set(Branches.buildOne)
  }

  feature("Getting bank branches") {

    scenario("We try to get bank branches for a bank without a data license for branch information") {

      When("We make a request")
      val request = (v1_4Request / "banks" / BankWithoutLicense.value / "branches").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We must get a 200")
      response.code must equal(200)
    }

    scenario("We try to get bank branches for a bank with a data license for branch information") {
      When("We make a request")
      val request = (v1_4Request / "banks" / BankWithLicense.value / "branches").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We must get a 200")
      response.code must equal(200)

      And("We must get the right json format containing a list of Branches")
      val wholeResponseBody = response.body
      val responseBodyOpt = wholeResponseBody.extractOpt[BranchesJson]
      responseBodyOpt.isDefined must equal(true)

      val responseBody = responseBodyOpt.get

      And("We must get the right branches")
      val branches = responseBody.branches

      // Order of branches in the list is arbitrary
      branches.size must equal(2)
      val first = branches(0)
      if(first.id == fakeBranch1.branchId.value) {
        verifySameData(fakeBranch1, first)
        verifySameData(fakeBranch2, branches(1))
      } else if (first.id == fakeBranch2.branchId.value) {
        verifySameData(fakeBranch2, first)
        verifySameData(fakeBranch1, branches(1))
      } else {
        fail("incorrect branches")
      }

    }
  }

}
