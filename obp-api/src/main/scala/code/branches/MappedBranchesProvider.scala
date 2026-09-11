package code.branches

import code.api.util.{DoobieUtil, OBPLimit, OBPOffset, OBPQueryParam}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

/**
 * One branch of a bank.
 *
 * Most columns genuinely hold NULL: the connector writes mcounty, both branch-routing columns,
 * every drive-up time, mbranchtype, mmoreinfo and mphonenumber through orNull. They are bound as
 * Option and read back as null, reproducing Lift's MappedString round trip — binding them as bare
 * Strings would throw at write time. The lobby times are the exception: the connector defaults
 * them to "00:00", so they are never null in practice.
 *
 * `branchRouting` looks like it falls back to "BRANCH_ID" and the branch id when the routing
 * columns are unset, but it never does: the Lift accessor compared the FIELD OBJECT to null and to
 * "" rather than its value, and a MappedString object is neither. The fallback has therefore never
 * fired, and the stored value — including null — is what callers have always seen. Preserved
 * verbatim; correcting it would start returning "BRANCH_ID" to every caller of a branch that has
 * no routing scheme.
 */
case class MappedBranch(
  private val branchIdRaw: String,
  private val bankIdRaw: String,
  private val nameRaw: String,
  private val line1: String,
  private val line2: String,
  private val line3: String,
  private val city: String,
  private val county: String,
  private val state: String,
  private val postCode: String,
  private val countryCode: String,
  private val latitude: Double,
  private val longitude: Double,
  private val licenseId: String,
  private val licenseName: String,
  private val lobbyHours: String,
  private val driveUpHours: String,
  private val branchRoutingSchemeRaw: String,
  private val branchRoutingAddressRaw: String,
  private val lobbyOpenMonday: String,
  private val lobbyCloseMonday: String,
  private val lobbyOpenTuesday: String,
  private val lobbyCloseTuesday: String,
  private val lobbyOpenWednesday: String,
  private val lobbyCloseWednesday: String,
  private val lobbyOpenThursday: String,
  private val lobbyCloseThursday: String,
  private val lobbyOpenFriday: String,
  private val lobbyCloseFriday: String,
  private val lobbyOpenSaturday: String,
  private val lobbyCloseSaturday: String,
  private val lobbyOpenSunday: String,
  private val lobbyCloseSunday: String,
  private val driveUpOpenMonday: String,
  private val driveUpCloseMonday: String,
  private val driveUpOpenTuesday: String,
  private val driveUpCloseTuesday: String,
  private val driveUpOpenWednesday: String,
  private val driveUpCloseWednesday: String,
  private val driveUpOpenThursday: String,
  private val driveUpCloseThursday: String,
  private val driveUpOpenFriday: String,
  private val driveUpCloseFriday: String,
  private val driveUpOpenSaturday: String,
  private val driveUpCloseSaturday: String,
  private val driveUpOpenSunday: String,
  private val driveUpCloseSunday: String,
  private val isAccessibleRaw: String,
  private val accessibleFeaturesRaw: String,
  private val branchTypeRaw: String,
  private val moreInfoRaw: String,
  private val phoneNumberRaw: String,
  private val isDeletedRaw: Boolean
) extends BranchT {

  override def branchId: BranchId = BranchId(branchIdRaw)
  override def bankId: BankId = BankId(bankIdRaw)
  override def name: String = nameRaw

  // See the class comment: this fallback is dead code in Lift too, and is kept that way.
  override def branchRouting: Option[RoutingT] = Some(new RoutingT {
    override def scheme: String = branchRoutingSchemeRaw
    override def address: String = branchRoutingAddressRaw
  })

  override def address: Address = Address(
    line1 = line1,
    line2 = line2,
    line3 = line3,
    city = city,
    county = Some(county),
    state = state,
    countryCode = countryCode,
    postCode = postCode
  )

  override def meta: com.openbankproject.commons.model.Meta =
    com.openbankproject.commons.model.Meta(license = License(id = licenseId, name = licenseName))

  override def lobbyString: Some[LobbyStringT] = Some(new LobbyStringT {
    override def hours: String = lobbyHours
  })

  override def location: Location = Location(latitude, longitude, None, None)

  override def driveUpString: Some[DriveUpStringT] = Some(new DriveUpStringT {
    override def hours: String = driveUpHours
  })

  // Opening / Closing times are expected to have the format 24 hour format e.g. 13:45
  // but could also be 25:44 if we want to represent a time after midnight.
  override def lobby: Some[Lobby] = Some(
    Lobby(
    monday = List(OpeningTimes(
      openingTime = lobbyOpenMonday,
      closingTime = lobbyCloseMonday
    )),
    tuesday = List(OpeningTimes(
      openingTime = lobbyOpenTuesday,
      closingTime = lobbyCloseTuesday
    )),
    wednesday = List(OpeningTimes(
      openingTime = lobbyOpenWednesday,
      closingTime = lobbyCloseWednesday
    )),
    thursday = List(OpeningTimes(
      openingTime = lobbyOpenThursday,
      closingTime = lobbyCloseThursday
    )),
    friday = List(OpeningTimes(
      openingTime = lobbyOpenFriday,
      closingTime = lobbyCloseFriday
    )),
    saturday = List(OpeningTimes(
      openingTime = lobbyOpenSaturday,
      closingTime = lobbyCloseSaturday
    )),
    sunday = List(OpeningTimes(
      openingTime = lobbyOpenSunday,
      closingTime = lobbyCloseSunday
    ))
  )
  )

  override def driveUp: Some[DriveUp] = Some(
    DriveUp(
    monday = OpeningTimes(
      openingTime = driveUpOpenMonday,
      closingTime = driveUpCloseMonday
    ),
    tuesday = OpeningTimes(
      openingTime = driveUpOpenTuesday,
      closingTime = driveUpCloseTuesday
    ),
    wednesday = OpeningTimes(
      openingTime = driveUpOpenWednesday,
      closingTime = driveUpCloseWednesday
    ),
    thursday = OpeningTimes(
      openingTime = driveUpOpenThursday,
      closingTime = driveUpCloseThursday
    ),
    friday = OpeningTimes(
      openingTime = driveUpOpenFriday,
      closingTime = driveUpCloseFriday
    ),
    saturday = OpeningTimes(
      openingTime = driveUpOpenSaturday,
      closingTime = driveUpCloseSaturday
    ),
    sunday = OpeningTimes(
      openingTime = driveUpOpenSunday,
      closingTime = driveUpCloseSunday
    )
  )
  )

  // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
  override def isAccessible: Option[Boolean] = isAccessibleRaw match {
    case "Y" => Some(true)
    case "N" => Some(false)
    case _ => None
  }

  override def accessibleFeatures: Option[String] = Some(accessibleFeaturesRaw)
  override def branchType: Some[String] = Some(branchTypeRaw)
  override def moreInfo: Some[String] = Some(moreInfoRaw)
  override def phoneNumber: Some[String] = Some(phoneNumberRaw)
  override def isDeleted: Option[Boolean] = Some(isDeletedRaw)
}

object MappedBranch {

  private val selectColumns =
    fr"""SELECT mbranchid, mbankid, mname, mline1, mline2, mline3,
                mcity, mcounty, mstate, mpostcode, mcountrycode, mlocationlatitude,
                mlocationlongitude, mlicenseid, mlicensename, mlobbyhours, mdriveuphours, mbranchroutingscheme,
                mbranchroutingaddress, mlobbyopeningtimeonmonday, mlobbyclosingtimeonmonday, mlobbyopeningtimeontuesday, mlobbyclosingtimeontuesday, mlobbyopeningtimeonwednesday,
                mlobbyclosingtimeonwednesday, mlobbyopeningtimeonthursday, mlobbyclosingtimeonthursday, mlobbyopeningtimeonfriday, mlobbyclosingtimeonfriday, mlobbyopeningtimeonsaturday,
                mlobbyclosingtimeonsaturday, mlobbyopeningtimeonsunday, mlobbyclosingtimeonsunday, mdriveupopeningtimeonmonday, mdriveupclosingtimeonmonday, mdriveupopeningtimeontuesday,
                mdriveupclosingtimeontuesday, mdriveupopeningtimeonwednesday, mdriveupclosingtimeonwednesday, mdriveupopeningtimeonthursday, mdriveupclosingtimeonthursday, mdriveupopeningtimeonfriday,
                mdriveupclosingtimeonfriday, mdriveupopeningtimeonsaturday, mdriveupclosingtimeonsaturday, mdriveupopeningtimeonsunday, mdriveupclosingtimeonsunday, misaccessible,
                maccessiblefeatures, mbranchtype, mmoreinfo, mphonenumber, misdeleted
         FROM mappedbranch"""

  // Split across three tuples: Scala tuples stop at 22 elements and this table has 53 columns to
  // read.
  private type Row = ((String, String, Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Double, Double, Option[String], Option[String], Option[String], Option[String], Option[String]),
    (Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String]),
    (Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Boolean))

  private def fromRow(row: Row): MappedBranch = row match {
    case ((branchIdRaw, bankIdRaw, nameRaw, line1, line2, line3, city, county, state, postCode, countryCode, latitude, longitude, licenseId, licenseName, lobbyHours, driveUpHours, branchRoutingSchemeRaw),
          (branchRoutingAddressRaw, lobbyOpenMonday, lobbyCloseMonday, lobbyOpenTuesday, lobbyCloseTuesday, lobbyOpenWednesday, lobbyCloseWednesday, lobbyOpenThursday, lobbyCloseThursday, lobbyOpenFriday, lobbyCloseFriday, lobbyOpenSaturday, lobbyCloseSaturday, lobbyOpenSunday, lobbyCloseSunday, driveUpOpenMonday, driveUpCloseMonday, driveUpOpenTuesday),
          (driveUpCloseTuesday, driveUpOpenWednesday, driveUpCloseWednesday, driveUpOpenThursday, driveUpCloseThursday, driveUpOpenFriday, driveUpCloseFriday, driveUpOpenSaturday, driveUpCloseSaturday, driveUpOpenSunday, driveUpCloseSunday, isAccessibleRaw, accessibleFeaturesRaw, branchTypeRaw, moreInfoRaw, phoneNumberRaw, isDeletedRaw)) =>
      MappedBranch(
        branchIdRaw, bankIdRaw, nameRaw.orNull, line1.orNull, line2.orNull, line3.orNull, city.orNull, county.orNull, state.orNull, postCode.orNull, countryCode.orNull, latitude, longitude, licenseId.orNull, licenseName.orNull, lobbyHours.orNull, driveUpHours.orNull, branchRoutingSchemeRaw.orNull,
        branchRoutingAddressRaw.orNull, lobbyOpenMonday.orNull, lobbyCloseMonday.orNull, lobbyOpenTuesday.orNull, lobbyCloseTuesday.orNull, lobbyOpenWednesday.orNull, lobbyCloseWednesday.orNull, lobbyOpenThursday.orNull, lobbyCloseThursday.orNull, lobbyOpenFriday.orNull, lobbyCloseFriday.orNull, lobbyOpenSaturday.orNull, lobbyCloseSaturday.orNull, lobbyOpenSunday.orNull, lobbyCloseSunday.orNull, driveUpOpenMonday.orNull, driveUpCloseMonday.orNull, driveUpOpenTuesday.orNull,
        driveUpCloseTuesday.orNull, driveUpOpenWednesday.orNull, driveUpCloseWednesday.orNull, driveUpOpenThursday.orNull, driveUpCloseThursday.orNull, driveUpOpenFriday.orNull, driveUpCloseFriday.orNull, driveUpOpenSaturday.orNull, driveUpCloseSaturday.orNull, driveUpOpenSunday.orNull, driveUpCloseSunday.orNull, isAccessibleRaw.orNull, accessibleFeaturesRaw.orNull, branchTypeRaw.orNull, moreInfoRaw.orNull, phoneNumberRaw.orNull, isDeletedRaw)
  }

  private def query(condition: Fragment): List[MappedBranch] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def find(bankId: String, branchId: String): Box[MappedBranch] =
    query(fr"WHERE mbankid = $bankId AND mbranchid = $branchId ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findAllByBankId(bankId: String): List[MappedBranch] =
    query(fr"WHERE mbankid = $bankId ORDER BY id ASC")

  /** The listing hides soft-deleted branches; limit and offset are applied only when supplied. */
  def findLiveByBankId(bankId: String, queryParams: List[OBPQueryParam]): List[MappedBranch] = {
    val limit = queryParams.collectFirst { case OBPLimit(value) => fr"LIMIT $value" }.getOrElse(Fragment.empty)
    val offset = queryParams.collectFirst { case OBPOffset(value) => fr"OFFSET $value" }.getOrElse(Fragment.empty)
    query(fr"WHERE mbankid = $bankId AND misdeleted = false ORDER BY id ASC" ++ limit ++ offset)
  }

  def createOrUpdate(
branchIdRaw: String, bankIdRaw: String, nameRaw: String, line1: String,
             line2: String, line3: String, city: String, county: String,
             state: String, postCode: String, countryCode: String, latitude: Double,
             longitude: Double, licenseId: String, licenseName: String, lobbyHours: String,
             driveUpHours: String, branchRoutingSchemeRaw: String, branchRoutingAddressRaw: String, lobbyOpenMonday: String,
             lobbyCloseMonday: String, lobbyOpenTuesday: String, lobbyCloseTuesday: String, lobbyOpenWednesday: String,
             lobbyCloseWednesday: String, lobbyOpenThursday: String, lobbyCloseThursday: String, lobbyOpenFriday: String,
             lobbyCloseFriday: String, lobbyOpenSaturday: String, lobbyCloseSaturday: String, lobbyOpenSunday: String,
             lobbyCloseSunday: String, driveUpOpenMonday: String, driveUpCloseMonday: String, driveUpOpenTuesday: String,
             driveUpCloseTuesday: String, driveUpOpenWednesday: String, driveUpCloseWednesday: String, driveUpOpenThursday: String,
             driveUpCloseThursday: String, driveUpOpenFriday: String, driveUpCloseFriday: String, driveUpOpenSaturday: String,
             driveUpCloseSaturday: String, driveUpOpenSunday: String, driveUpCloseSunday: String, isAccessibleRaw: String,
             accessibleFeaturesRaw: String, branchTypeRaw: String, moreInfoRaw: String, phoneNumberRaw: String,
             isDeletedRaw: Boolean): MappedBranch = {
    val existing = find(bankIdRaw, branchIdRaw)
    if (existing.isDefined) {
      DoobieUtil.runUpdate(
        sql"""UPDATE mappedbranch SET mname = ${Option(nameRaw)}, mline1 = ${Option(line1)}, mline2 = ${Option(line2)}, mline3 = ${Option(line3)},
              mcity = ${Option(city)}, mcounty = ${Option(county)}, mstate = ${Option(state)}, mpostcode = ${Option(postCode)},
              mcountrycode = ${Option(countryCode)}, mlocationlatitude = $latitude, mlocationlongitude = $longitude, mlicenseid = ${Option(licenseId)},
              mlicensename = ${Option(licenseName)}, mlobbyhours = ${Option(lobbyHours)}, mdriveuphours = ${Option(driveUpHours)}, mbranchroutingscheme = ${Option(branchRoutingSchemeRaw)},
              mbranchroutingaddress = ${Option(branchRoutingAddressRaw)}, mlobbyopeningtimeonmonday = ${Option(lobbyOpenMonday)}, mlobbyclosingtimeonmonday = ${Option(lobbyCloseMonday)}, mlobbyopeningtimeontuesday = ${Option(lobbyOpenTuesday)},
              mlobbyclosingtimeontuesday = ${Option(lobbyCloseTuesday)}, mlobbyopeningtimeonwednesday = ${Option(lobbyOpenWednesday)}, mlobbyclosingtimeonwednesday = ${Option(lobbyCloseWednesday)}, mlobbyopeningtimeonthursday = ${Option(lobbyOpenThursday)},
              mlobbyclosingtimeonthursday = ${Option(lobbyCloseThursday)}, mlobbyopeningtimeonfriday = ${Option(lobbyOpenFriday)}, mlobbyclosingtimeonfriday = ${Option(lobbyCloseFriday)}, mlobbyopeningtimeonsaturday = ${Option(lobbyOpenSaturday)},
              mlobbyclosingtimeonsaturday = ${Option(lobbyCloseSaturday)}, mlobbyopeningtimeonsunday = ${Option(lobbyOpenSunday)}, mlobbyclosingtimeonsunday = ${Option(lobbyCloseSunday)}, mdriveupopeningtimeonmonday = ${Option(driveUpOpenMonday)},
              mdriveupclosingtimeonmonday = ${Option(driveUpCloseMonday)}, mdriveupopeningtimeontuesday = ${Option(driveUpOpenTuesday)}, mdriveupclosingtimeontuesday = ${Option(driveUpCloseTuesday)}, mdriveupopeningtimeonwednesday = ${Option(driveUpOpenWednesday)},
              mdriveupclosingtimeonwednesday = ${Option(driveUpCloseWednesday)}, mdriveupopeningtimeonthursday = ${Option(driveUpOpenThursday)}, mdriveupclosingtimeonthursday = ${Option(driveUpCloseThursday)}, mdriveupopeningtimeonfriday = ${Option(driveUpOpenFriday)},
              mdriveupclosingtimeonfriday = ${Option(driveUpCloseFriday)}, mdriveupopeningtimeonsaturday = ${Option(driveUpOpenSaturday)}, mdriveupclosingtimeonsaturday = ${Option(driveUpCloseSaturday)}, mdriveupopeningtimeonsunday = ${Option(driveUpOpenSunday)},
              mdriveupclosingtimeonsunday = ${Option(driveUpCloseSunday)}, misaccessible = ${Option(isAccessibleRaw)}, maccessiblefeatures = ${Option(accessibleFeaturesRaw)}, mbranchtype = ${Option(branchTypeRaw)},
              mmoreinfo = ${Option(moreInfoRaw)}, mphonenumber = ${Option(phoneNumberRaw)}, misdeleted = $isDeletedRaw
              WHERE mbankid = $bankIdRaw AND mbranchid = $branchIdRaw""".update.run)
    } else {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedbranch
            (mbranchid, mbankid, mname, mline1, mline2, mline3,
             mcity, mcounty, mstate, mpostcode, mcountrycode, mlocationlatitude,
             mlocationlongitude, mlicenseid, mlicensename, mlobbyhours, mdriveuphours, mbranchroutingscheme,
             mbranchroutingaddress, mlobbyopeningtimeonmonday, mlobbyclosingtimeonmonday, mlobbyopeningtimeontuesday, mlobbyclosingtimeontuesday, mlobbyopeningtimeonwednesday,
             mlobbyclosingtimeonwednesday, mlobbyopeningtimeonthursday, mlobbyclosingtimeonthursday, mlobbyopeningtimeonfriday, mlobbyclosingtimeonfriday, mlobbyopeningtimeonsaturday,
             mlobbyclosingtimeonsaturday, mlobbyopeningtimeonsunday, mlobbyclosingtimeonsunday, mdriveupopeningtimeonmonday, mdriveupclosingtimeonmonday, mdriveupopeningtimeontuesday,
             mdriveupclosingtimeontuesday, mdriveupopeningtimeonwednesday, mdriveupclosingtimeonwednesday, mdriveupopeningtimeonthursday, mdriveupclosingtimeonthursday, mdriveupopeningtimeonfriday,
             mdriveupclosingtimeonfriday, mdriveupopeningtimeonsaturday, mdriveupclosingtimeonsaturday, mdriveupopeningtimeonsunday, mdriveupclosingtimeonsunday, misaccessible,
             maccessiblefeatures, mbranchtype, mmoreinfo, mphonenumber, misdeleted)
            VALUES ($branchIdRaw, $bankIdRaw, ${Option(nameRaw)}, ${Option(line1)}, ${Option(line2)}, ${Option(line3)},
             ${Option(city)}, ${Option(county)}, ${Option(state)}, ${Option(postCode)}, ${Option(countryCode)}, $latitude,
             $longitude, ${Option(licenseId)}, ${Option(licenseName)}, ${Option(lobbyHours)}, ${Option(driveUpHours)}, ${Option(branchRoutingSchemeRaw)},
             ${Option(branchRoutingAddressRaw)}, ${Option(lobbyOpenMonday)}, ${Option(lobbyCloseMonday)}, ${Option(lobbyOpenTuesday)}, ${Option(lobbyCloseTuesday)}, ${Option(lobbyOpenWednesday)},
             ${Option(lobbyCloseWednesday)}, ${Option(lobbyOpenThursday)}, ${Option(lobbyCloseThursday)}, ${Option(lobbyOpenFriday)}, ${Option(lobbyCloseFriday)}, ${Option(lobbyOpenSaturday)},
             ${Option(lobbyCloseSaturday)}, ${Option(lobbyOpenSunday)}, ${Option(lobbyCloseSunday)}, ${Option(driveUpOpenMonday)}, ${Option(driveUpCloseMonday)}, ${Option(driveUpOpenTuesday)},
             ${Option(driveUpCloseTuesday)}, ${Option(driveUpOpenWednesday)}, ${Option(driveUpCloseWednesday)}, ${Option(driveUpOpenThursday)}, ${Option(driveUpCloseThursday)}, ${Option(driveUpOpenFriday)},
             ${Option(driveUpCloseFriday)}, ${Option(driveUpOpenSaturday)}, ${Option(driveUpCloseSaturday)}, ${Option(driveUpOpenSunday)}, ${Option(driveUpCloseSunday)}, ${Option(isAccessibleRaw)},
             ${Option(accessibleFeaturesRaw)}, ${Option(branchTypeRaw)}, ${Option(moreInfoRaw)}, ${Option(phoneNumberRaw)}, $isDeletedRaw)"""
          .update.run)
    }
    find(bankIdRaw, branchIdRaw).openOrThrowException("the branch just written must be readable")
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbranch".update.run)
    ()
  }
}

object MappedBranchesProvider extends BranchesProvider with MdcLoggable {

  override protected def getBranchFromProvider(bankId: BankId, branchId: BranchId): Option[BranchT] =
    MappedBranch.find(bankId.value, branchId.value)

  override protected def getBranchesFromProvider(bankId: BankId, queryParams: List[OBPQueryParam]): Option[List[BranchT]] = {
    logger.debug(s"getBranchesFromProvider says bankId is $bankId")
    Some(MappedBranch.findLiveByBankId(bankId.value, queryParams))
  }
}
