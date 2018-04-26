package code.branches


/* For branches */

// Need to import these one by one because in same package!
import code.bankconnectors.OBPQueryParam
import code.branches.Branches.{Branch, BranchId, BranchT}
import code.common._
import code.model.BankId
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

object Branches extends SimpleInjector {

  case class BranchId(value : String) {
    override def toString = value
  }

  object BranchId {
    def unapply(id : String) = Some(BranchId(id))
  }

// MappedBranch will implement this.
// The trait defines the fields the API will interact with.

  trait BranchT {
  def branchId: BranchId
  def bankId: BankId
  def name: String
  def address: Address
  def location: Location
  def lobbyString: Option[LobbyStringT]
  def driveUpString: Option[DriveUpStringT]
  def meta: Meta
  def branchRouting: Option[RoutingT]
  def lobby: Option[Lobby]
  def driveUp: Option[DriveUp]
  // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
  def isAccessible : Option[Boolean]
  def accessibleFeatures: Option[String]
  def branchType : Option[String]
  def moreInfo : Option[String]
  def phoneNumber : Option[String] }




// This is the API Version indpendent case class for Branches.
  // Use this internally
  case class Branch(
                     branchId: BranchId,
                     bankId: BankId,
                     name: String,
                     address: Address,
                     location: Location,
                     lobbyString: Option[LobbyString],
                     driveUpString: Option[DriveUpString],
                     meta: Meta,
                     branchRouting: Option[Routing],
                     lobby: Option[Lobby],
                     driveUp: Option[DriveUp],
                     // Easy access for people who use wheelchairs etc.
                     isAccessible : Option[Boolean],
                     accessibleFeatures: Option[String],
                     branchType : Option[String],
                     moreInfo : Option[String],
                     phoneNumber : Option[String]
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
                    monday: List[OpeningTimes],
                    tuesday: List[OpeningTimes],
                    wednesday: List[OpeningTimes],
                    thursday: List[OpeningTimes],
                    friday: List[OpeningTimes],
                    saturday: List[OpeningTimes],
                    sunday: List[OpeningTimes]
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

  @deprecated("Use Lobby instead which contains detailed fields, not this string","24 July 2017")
  trait LobbyStringT {
   def hours : String
  }

  @deprecated("Use Lobby instead which contains detailed fields, not this string","24 July 2017")
  case class LobbyString (
    hours : String
                         ) extends LobbyStringT


  @deprecated("Use DriveUp instead which contains detailed fields now, not this string","24 July 2017")
  trait DriveUpStringT {
    def hours : String
  }

  @deprecated("Use DriveUp instead which contains detailed fields now, not this string","24 July 2017")
  case class DriveUpString (
         hours : String
       ) extends DriveUpStringT



  val branchesProvider = new Inject(buildOne _) {}

  def buildOne: BranchesProvider = MappedBranchesProvider


  // Helper to get the count out of an option
  def countOfBranches (listOpt: Option[List[BranchT]]) : Int = {
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
  final def getBranches(bankId : BankId ,queryParams: OBPQueryParam*) : Option[List[BranchT]] = {
    // If we get branches filter them
    val branches: Option[List[BranchT]] = getBranchesFromProvider(bankId : BankId ,queryParams:_*)

    branches match {
      case Some(branches) => {
        logger.debug(s"getBranches says there are ${branches.length} branches with or without license")

        val branchesWithLicense = for {
         branch <- branches if branch.meta.license.name.size > 3
        } yield branch
        logger.debug(s"getBranches says there are ${branches.length} branchesWithLicense")
        Option(branchesWithLicense)
      }
      case None => {
        logger.debug(s"getBranches says there are None branches")
        None
      }
    }
  }

  /*
  Return one Branch
   */
  final def getBranch(bankId: BankId, branchId : BranchId) : Option[BranchT] = {
    // Filter out if no license data
    getBranchFromProvider(bankId,branchId).filter(x => x.meta.license.id != "" && x.meta.license.name != "")
  }

  protected def getBranchFromProvider(bankId: BankId, branchId : BranchId) : Option[BranchT]
  protected def getBranchesFromProvider(bank : BankId, queryParams:OBPQueryParam*): Option[List[BranchT]]

// End of Trait
}

