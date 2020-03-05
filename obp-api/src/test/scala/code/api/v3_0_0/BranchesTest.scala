package code.api.v3_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanDeleteBranchAtAnyBank
import com.openbankproject.commons.util.ApiVersion
import code.api.util.{ ErrorMessages, OBPQueryParam}
import code.api.v3_1_0.OBPAPI3_1_0
import code.bankconnectors.Connector
import code.branches.Branches.Branch
import code.branches.{Branches, BranchesProvider}
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model._
import org.scalatest.Tag

/*
Note This does not test retrieval from a backend.
We mock the backend so get test the API
 */
class BranchesTest extends V300ServerSetup with DefaultUsers {

  val BankWithLicense = BankId("testBank1")
  val BankWithoutLicense = BankId("testBank2")
  val BankWithoutBranches = BankId("testBankWithoutBranches")
  val bankId = "exist_bank_id"

  // Have to repeat the constructor parameters from the trait
  case class BranchImpl(
                         branchId: BranchId,
                         bankId: BankId,
                         name: String,
                         address: Address,
                         location: Location,
                         meta: Meta,
                         lobbyString: Option[LobbyStringT],
                         driveUpString: Option[DriveUpStringT],
                         branchRoutingScheme: String,
                         branchRoutingAddress: String,

                         // Lobby Times
                         lobbyOpeningTimeOnMonday : String,
                         lobbyClosingTimeOnMonday : String,

                         lobbyOpeningTimeOnTuesday : String,
                         lobbyClosingTimeOnTuesday : String,

                         lobbyOpeningTimeOnWednesday : String,
                         lobbyClosingTimeOnWednesday : String,

                         lobbyOpeningTimeOnThursday : String,
                         lobbyClosingTimeOnThursday: String,

                         lobbyOpeningTimeOnFriday : String,
                         lobbyClosingTimeOnFriday : String,

                         lobbyOpeningTimeOnSaturday : String,
                         lobbyClosingTimeOnSaturday : String,

                         lobbyOpeningTimeOnSunday: String,
                         lobbyClosingTimeOnSunday : String,

                         // Drive Up times
                         driveUpOpeningTimeOnMonday : String,
                         driveUpClosingTimeOnMonday : String,

                         driveUpOpeningTimeOnTuesday : String,
                         driveUpClosingTimeOnTuesday : String,

                         driveUpOpeningTimeOnWednesday : String,
                         driveUpClosingTimeOnWednesday : String,

                         driveUpOpeningTimeOnThursday : String,
                         driveUpClosingTimeOnThursday: String,

                         driveUpOpeningTimeOnFriday : String,
                         driveUpClosingTimeOnFriday : String,

                         driveUpOpeningTimeOnSaturday : String,
                         driveUpClosingTimeOnSaturday : String,

                         driveUpOpeningTimeOnSunday: String,
                         driveUpClosingTimeOnSunday : String,



                         // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
                         isAccessible : Option[Boolean],
                         accessibleFeatures: Option[String],
                         branchType : Option[String],
                         moreInfo : Option[String],
                         driveUp: Option[DriveUp],
                         lobby: Option[Lobby],
                         branchRouting: Option[RoutingT],
                         phoneNumber : Option[String],
                         isDeleted : Option[Boolean]
                       ) extends BranchT



  val fakeAddress1 = Address("Dunckerstraße 73 ApS", "Udemarken", "Hjørring", "Berlin", Some("Denmark"), "Denmark", "10437", "DE")
  val fakeAddress2 = fakeAddress1.copy(line1 = "00000", city="Aachen")

  val fakeMeta = Meta (
    License (
      id = "sample-license",
      name = "Sample License"
    )
  )

  val fakeMetaNoLicense = Meta (
    License (
      id = "",
      name = ""
    )
  )
  //"latitude":54.300288,
  //      "longitude":-2.236626
  val fakeLocation = Location (
    latitude = 54.300288,
    longitude = -2.236626,
    date =None,
    user = None
  )


  val fakeLocation2 = Location (
    latitude = 55.8,
    longitude = -2.6,
    date =None,
    user = None
  )


  val fakeLobby = Some(new LobbyStringT {
    val hours = "M-Th 9-5, Fri 9-6, Sat 9-1"
  }
  )


  val fakeLobby2 = Some(new LobbyStringT {
    val hours = "9-5"
  })

  val fakeDriveUp = Some(new DriveUpStringT {
    override def hours: String = "M-Th 8:30 - 5:30, Fri 8:30 - 6, Sat: 9-12"
  })


  val fakeDriveUp2 = Some(new DriveUpStringT {
    override def hours: String = "M-Th 8:30 - 5:30"
  })

  val fakeBranchRoutingScheme : String = "Bank X Scheme"
  val fakeBranchRoutingAddress : String = "78676"


  val fakeOpeningTime : String = "10:00"
  val fakeClosingTime : String = "18:00"

  val fakeIsAccessible: Option[Boolean] = Some(true)
  val fakeBranchType : Option[String]  = Some("Main")
  val fakeMoreInfo  : Option[String] = Some("Very near to the lake")


  val fakeBranch1 = BranchImpl(BranchId("branch1"), BankId("uk"),"Branch 1 Müdürlük", fakeAddress1, fakeLocation, fakeMeta, fakeLobby, fakeDriveUp, fakeBranchRoutingScheme,fakeBranchRoutingAddress,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeIsAccessible,
    None,
    fakeBranchType,
    fakeMoreInfo, None, None, None, None, None)
  val fakeBranch2 = BranchImpl(BranchId("branch2"), BankId("uk"), "Branch 2 Lala", fakeAddress2, fakeLocation2, fakeMeta, fakeLobby2, fakeDriveUp2,fakeBranchRoutingScheme,fakeBranchRoutingAddress,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeIsAccessible,
    None,
    fakeBranchType,
    fakeMoreInfo, None, None, None, None, None)
  val fakeBranch3 = BranchImpl(BranchId("branch3"), BankId("uk"), "Branch 3", fakeAddress2, fakeLocation, fakeMetaNoLicense, fakeLobby, fakeDriveUp2,fakeBranchRoutingScheme,fakeBranchRoutingAddress,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeClosingTime,
    fakeOpeningTime, fakeIsAccessible,
    None,
    fakeBranchType,
    fakeMoreInfo, None, None, None, None, None) // Should not be returned

  val deletedBranch = Branch(
    BranchId("deleted_branch_id"),
    BankId(bankId),
    "Deleted Branch",
    fakeAddress2,
    fakeLocation,
    None,
    None,
    fakeMeta,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Some(true))

  val existsBranch1 = Branch(BranchId("not_deleted_branch_id"),
    BankId(bankId),
    "Not deleted Branch",
    fakeAddress1,
    fakeLocation,
    None,
    None,
    fakeMeta,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None)

  val existsBranch2 = Branch(BranchId("not_deleted_branch_id_2"),
    BankId(bankId),
    "Not deleted Branch2",
    fakeAddress2,
    fakeLocation2,
    None,
    None,
    fakeMeta,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None)

  // This mock provider is returning same branches for the fake banks
  val mockConnector = new BranchesProvider {
    override protected def getBranchesFromProvider(bank: BankId, queryParams: List[OBPQueryParam]): Option[List[BranchT]] = {
      bank match {
        // have it return branches even for the bank without a license so we can test the API does not return them
        case BankWithLicense | BankWithoutLicense=> Some(List(fakeBranch1, fakeBranch2, fakeBranch3))
        case _ => None
      }
    }

    // Mock a badly behaving connector that returns data that doesn't have license.
    override protected def getBranchFromProvider(bankId: BankId, branchId: BranchId): Option[BranchT] = {
      branchId match {
        case BankWithLicense => Some(fakeBranch1)
        case BankWithoutLicense=> Some(fakeBranch3) // In case the connector returns, the API should guard
        case _ => None
      }
    }

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

  override def beforeEach(): Unit = {
    super.beforeEach()
    Connector.connector.vend.createOrUpdateBank(bankId, "exists bank", "bank", "string", "string", "string", "string", "string", "string")
    Connector.connector.vend.createOrUpdateBranch(deletedBranch)
    Connector.connector.vend.createOrUpdateBranch(existsBranch1)
    Connector.connector.vend.createOrUpdateBranch(existsBranch2)
  }

  override def afterEach(): Unit = super.afterEach()

  object VersionOfApi extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(OBPAPI3_0_0.Implementations3_0_0.getBranches))

  feature("getBranches -- /banks/BANK_ID/branches -- V300") {

    scenario("We try to get bank branches for a bank without a data license for branch information", VersionOfApi, ApiEndpoint) {

      When("We make a request v3.0.0")
      val request300 = (v3_0Request / "banks" / BankWithoutBranches.value / "branches").GET <@(user1)
      val response300 = makeGetRequest(request300)
      Then("We should get a 200 and correct response json format")
      response300.code should equal(200)
      val result = response300.body.extract[BranchesJsonV300]
      result.branches.size should be (0)
    }


    scenario("We try to get bank branches those all not deleted", VersionOfApi, ApiEndpoint) {
      Connector.connector.vend.createOrUpdateBank(bankId, "exists bank", "bank", "string", "string", "string", "string", "string", "string")
      When("We make a request v3.0.0")
      val request300 = (v3_0Request / "banks" / bankId / "branches").GET <@(user1)
      val response300 = makeGetRequest(request300)
      Then("We should get a 200 and all branches is not deleted")
      response300.code should equal(200)
      //check return branches not deleted
      val result = response300.body.extract[BranchesJsonV300]
      result.branches.size should be (2)
      result.branches.forall(_.name.startsWith("Not deleted Branch")) should be (true)
    }


    scenario("We try to get bank branches query by city", VersionOfApi, ApiEndpoint) {

      When("We make a request v3.0.0")
      var request300 = (v3_0Request / "banks" / bankId / "branches").GET <@(user1)
      //query parameter must add in this way, So request300 defined as var instead of val
      request300 = request300.addQueryParameter("city",  existsBranch1.address.city)

      val response300 = makeGetRequest(request300)
      Then("We should get a 200 and city matches query parameter")
      response300.code should equal(200)
      //check return branches not deleted
      val result = response300.body.extract[BranchesJsonV300]
      result.branches.size should be (1)
      result.branches(0).address.city should be (existsBranch1.address.city)
    }

    scenario("We try to get bank branches query by distance fond one branch", VersionOfApi, ApiEndpoint) {

      When("We make a request v3.0.0")
      var request300 = (v3_0Request / "banks" / bankId / "branches").GET <@(user1)
      request300 = request300.addQueryParameter("withinMetersOf", "220").addQueryParameter("nearLatitude", "54.3").addQueryParameter("nearLongitude", "-2.24")
      val response300 = makeGetRequest(request300, List(("city", existsBranch1.address.city)))
      Then("We should get a 200 and there is one branch in the given distance of latitude and longitude")
      response300.code should equal(200)
      //check return branches not deleted
      val result = response300.body.extract[BranchesJsonV300]
      result.branches.size should be (0)

    }

    scenario("We try to get bank branches query by distance fond none branch", VersionOfApi, ApiEndpoint) {

      When("We make a request v3.0.0")
      var request300 = (v3_0Request / "banks" / bankId / "branches").GET <@(user1)
      request300 = request300.addQueryParameter("withinMetersOf", "250").addQueryParameter("nearLatitude", "54.3").addQueryParameter("nearLongitude", "-2.24")
      val response300 = makeGetRequest(request300)
      Then("We should get a 200 and there is none branch in the given distance of latitude and longitude")
      response300.code should equal(200)
      //check return branches not deleted
      val result = response300.body.extract[BranchesJsonV300]
      result.branches.size should be (1)
      result.branches(0).name should be (existsBranch1.name)

    }


    // note：get all branches endpoint belongs v3.0.0, and delete branch endpoint belongs 3.1.0.
    // But, because the delete branch endpoint unitest need get all branches endpoint, to check whether given branch is deleted
    // So the delete branch endpoint unit test put at here.
    object VersionOfApi_3_1_0 extends Tag(ApiVersion.v3_1_0.toString)
    object ApiEndpoint_delete_branch extends Tag(nameOf(OBPAPI3_1_0.Implementations3_1_0.deleteBranch))

    scenario("We try to delete bank branche", VersionOfApi_3_1_0, ApiEndpoint_delete_branch) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteBranchAtAnyBank.toString())
      When("We make a request v3.0.0")
      val requestDelete = (baseRequest / "obp" / "v3.1.0" / "banks" / bankId / "branches"/ existsBranch1.branchId.value).DELETE <@(user1)
      makeDeleteRequest(requestDelete)

      val request300 = (v3_0Request / "banks" / bankId / "branches").GET <@(user1)
      val response300 = makeGetRequest(request300)
      Then("We should delete one branch and left one branch")
      response300.code should equal(200)
      //check return branches not deleted
      val result = response300.body.extract[BranchesJsonV300]
      result.branches.size should be (1)
      result.branches(0).name should be (existsBranch2.name)
    }

  }

}
