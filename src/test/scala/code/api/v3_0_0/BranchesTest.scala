package code.api.v3_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import code.api.v1_4_0.JSONFactory1_4_0.{BranchJson, BranchesJson}
import code.bankconnectors.OBPQueryParam
import code.branches.Branches._
import code.branches.{Branches, BranchesProvider}
import code.common._
import code.model.BankId
import code.setup.DefaultUsers
import net.liftweb.json

/*
Note This does not test retrieval from a backend.
We mock the backend so get test the API
 */
class BranchesTest extends V300ServerSetup with DefaultUsers {

  val BankWithLicense = BankId("testBank1")
  val BankWithoutLicense = BankId("testBank2")
  val BankWithoutBranches = BankId("testBankWithoutBranches")

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
                         phoneNumber : Option[String]

  ) extends BranchT



  val fakeAddress1 = Address("Dunckerstraße 73 ApS", "Udemarken", "Hjørring", "Berlin", Some("Denmark"), "Denmark", "10437", "DE")
  val fakeAddress2 = fakeAddress1.copy(line1 = "00000")

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

  val fakeLocation = Location (
    latitude = 1.11,
    longitude = 2.22,
    date =None,
    user = None
  )


  val fakeLocation2 = Location (
    latitude = 1.1111,
    longitude = 2.2222,
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
    fakeMoreInfo, None, None, None, None)
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
    fakeMoreInfo, None, None, None, None)
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
    fakeMoreInfo, None, None, None, None) // Should not be returned

  // This mock provider is returning same branches for the fake banks
  val mockConnector = new BranchesProvider {
    override protected def getBranchesFromProvider(bank: BankId, queryParams:OBPQueryParam*): Option[List[BranchT]] = {
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

  feature("getBranches -- /banks/BANK_ID/branches -- V300") {

    scenario("We try to get bank branches for a bank without a data license for branch information") {

      When("We make a request v3.0.0")
      val request300 = (v3_0Request / "banks" / BankWithoutBranches.value / "branches").GET <@(user1)
      val response300 = makeGetRequest(request300)
      Then("We should get a 400 and correct response jons format")
      response300.code should equal(400)
      response300.body.extract[BranchesJsonV300]
      json.compactRender(response300.body \ "error").replaceAll("\"", "") should include (ErrorMessages.BranchesNotFound)
    }
   
  }

}
