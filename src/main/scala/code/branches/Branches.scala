package code.branches


/* For branches */

// Need to import these one by one because in same package!
import code.api.v3_0_0.BranchJsonV300
import code.branches.Branches.{Branch, BranchId}
import code.common._
import code.model.BankId
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Branches extends SimpleInjector {

  case class BranchId(value : String)

  object BranchId {
    def unapply(id : String) = Some(BranchId(id))
  }


  trait BranchT {
  def branchId: BranchId
  def bankId: BankId
  def name: String
  def address: Address
  def location: Location
  def lobbyString: String
  def driveUpString: String
  def meta: Meta
  def branchRouting: Routing
  def lobby: Lobby
  def driveUp: DriveUp
  // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
  def isAccessible : String
  def branchType : String
  def moreInfo : String}





  case class Branch(
                     branchId: BranchId,
                     bankId: BankId,
                     name: String,
                     address: Address,
                     location: Location,
                     lobbyString: String,
                     driveUpString: String,
                     meta: Meta,
                     branchRouting: Routing,
                     lobby: Lobby,
                     driveUp: DriveUp,
                     // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
                     isAccessible : String,
                     branchType : String,
                     moreInfo : String
                   ) extends BranchT





//  trait Branch {
//    def branchId : BranchId
//    def bankId : BankId
//    def name : String
//    def address : AddressT
//    def location : LocationT
//    def lobbyString : LobbyString
//    def driveUpString : DriveUpString
//    def meta : Meta
//    def branchRoutingScheme: String
//    def branchRoutingAddress: String
//
//    // Opening / Closing times are expected to have the format 24 hour format e.g. 13:45
//    // but could also be 25:44 if we want to represent a time after midnight.
//
//    // Lobby
//    def  lobbyOpeningTimeOnMonday : String
//    def  lobbyClosingTimeOnMonday : String
//
//    def  lobbyOpeningTimeOnTuesday : String
//    def  lobbyClosingTimeOnTuesday : String
//
//    def  lobbyOpeningTimeOnWednesday : String
//    def  lobbyClosingTimeOnWednesday : String
//
//    def  lobbyOpeningTimeOnThursday : String
//    def  lobbyClosingTimeOnThursday: String
//
//    def  lobbyOpeningTimeOnFriday : String
//    def  lobbyClosingTimeOnFriday : String
//
//    def  lobbyOpeningTimeOnSaturday : String
//    def  lobbyClosingTimeOnSaturday : String
//
//    def  lobbyOpeningTimeOnSunday: String
//    def  lobbyClosingTimeOnSunday : String
//
//    // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
//    def  isAccessible : String
//
//    def  branchType : String
//    def  moreInfo : String
//
//    // Drive Up
//    def  driveUpOpeningTimeOnMonday : String
//    def  driveUpClosingTimeOnMonday : String
//
//    def  driveUpOpeningTimeOnTuesday : String
//    def  driveUpClosingTimeOnTuesday : String
//
//    def  driveUpOpeningTimeOnWednesday : String
//    def  driveUpClosingTimeOnWednesday : String
//
//    def  driveUpOpeningTimeOnThursday : String
//    def  driveUpClosingTimeOnThursday: String
//
//    def  driveUpOpeningTimeOnFriday : String
//    def  driveUpClosingTimeOnFriday : String
//
//    def  driveUpOpeningTimeOnSaturday : String
//    def  driveUpClosingTimeOnSaturday : String
//
//    def  driveUpOpeningTimeOnSunday: String
//    def  driveUpClosingTimeOnSunday : String
//  }





  ///

//  case class BranchCC(
//                       branchId: BranchId,
//                       bankId: BankId,
//                       name: String,
//                       address: AddressT,
//                       location: LocationT,
//                       meta: Meta,
//                       lobbyString: LobbyString,
//                       driveUpString: DriveUpString,
//                       branchRoutingScheme: String,
//                       branchRoutingAddress: String,
//
//                       // Lobby Times
//                       lobbyOpeningTimeOnMonday : String,
//                       lobbyClosingTimeOnMonday : String,
//
//                       lobbyOpeningTimeOnTuesday : String,
//                       lobbyClosingTimeOnTuesday : String,
//
//                       lobbyOpeningTimeOnWednesday : String,
//                       lobbyClosingTimeOnWednesday : String,
//
//                       lobbyOpeningTimeOnThursday : String,
//                       lobbyClosingTimeOnThursday: String,
//
//                       lobbyOpeningTimeOnFriday : String,
//                       lobbyClosingTimeOnFriday : String,
//
//                       lobbyOpeningTimeOnSaturday : String,
//                       lobbyClosingTimeOnSaturday : String,
//
//                       lobbyOpeningTimeOnSunday: String,
//                       lobbyClosingTimeOnSunday : String,
//
//                       // Drive Up times
//                       driveUpOpeningTimeOnMonday : String,
//                       driveUpClosingTimeOnMonday : String,
//
//                       driveUpOpeningTimeOnTuesday : String,
//                       driveUpClosingTimeOnTuesday : String,
//
//                       driveUpOpeningTimeOnWednesday : String,
//                       driveUpClosingTimeOnWednesday : String,
//
//                       driveUpOpeningTimeOnThursday : String,
//                       driveUpClosingTimeOnThursday: String,
//
//                       driveUpOpeningTimeOnFriday : String,
//                       driveUpClosingTimeOnFriday : String,
//
//                       driveUpOpeningTimeOnSaturday : String,
//                       driveUpClosingTimeOnSaturday : String,
//
//                       driveUpOpeningTimeOnSunday: String,
//                       driveUpClosingTimeOnSunday : String,
//
//                       // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
//                       isAccessible : String,
//
//                       branchType : String,
//                       moreInfo : String
//
//                       ) extends Branch





  case class Lobby(
                            monday: OpeningTimes,
                            tuesday: OpeningTimes,
                            wednesday: OpeningTimes,
                            thursday: OpeningTimes,
                            friday: OpeningTimes,
                            saturday: OpeningTimes,
                            sunday: OpeningTimes
                          )

  case class DriveUp(
                              monday: OpeningTimes,
                              tuesday: OpeningTimes,
                              wednesday: OpeningTimes,
                              thursday: OpeningTimes,
                              friday: OpeningTimes,
                              saturday: OpeningTimes,
                              sunday: OpeningTimes
                            )


  import code.common.Routing






  //

  @deprecated("Use detailed fields now, not this string","24 July 2017")
  trait LobbyString {
   def hours : String
  }

  @deprecated("Use detailed fields now, not this string","24 July 2017")
  trait DriveUpString {
    def hours : String
  }

  val branchesProvider = new Inject(buildOne _) {}

  def buildOne: BranchesProvider = MappedBranchesProvider


  // Helper to get the count out of an option
  def countOfBranches (listOpt: Option[List[Branch]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait BranchesProvider {

  private val logger = Logger(classOf[BranchesProvider])


  /*
  Common logic for returning branches.
  Implementation details in branchesData
   */
  final def getBranches(bankId : BankId) : Option[List[Branch]] = {
    // If we get branches filter them
    getBranchesFromProvider(bankId) match {
      case Some(branches) => {

        val branchesWithLicense = for {
         branch <- branches if branch.meta.license.name.size > 3
        } yield branch
        Option(branchesWithLicense)
      }
      case None => None
    }
  }

  /*
  Return one Branch
   */
  final def getBranch(branchId : BranchId) : Option[Branch] = {
    // Filter out if no license data
    getBranchFromProvider(branchId).filter(x => x.meta.license.id != "" && x.meta.license.name != "")
  }

  protected def getBranchFromProvider(branchId : BranchId) : Option[Branch]
  protected def getBranchesFromProvider(bank : BankId) : Option[List[Branch]]

// End of Trait
}

