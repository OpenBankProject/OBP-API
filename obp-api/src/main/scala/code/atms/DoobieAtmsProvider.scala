package code.atms

import code.api.util.{DoobieUtil, OBPLimit, OBPOffset, OBPQueryParam}
import code.util.Helper.optionBooleanToString
import com.openbankproject.commons.model.{Meta => CommonMeta, _}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._  // Provides Meta instances for java.sql.Timestamp
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import scala.collection.immutable.List

object DoobieAtmsProvider extends AtmsProvider {

  // 48 columns — split across three case classes to stay within Scala 2's 22-field case-class limit.

  private case class AtmRow1(
    matmid: Option[String],
    mbankid: Option[String],
    mname: Option[String],
    mline1: Option[String],
    mline2: Option[String],
    mline3: Option[String],
    mcity: Option[String],
    mcounty: Option[String],
    mstate: Option[String],
    mcountrycode: Option[String],
    mpostcode: Option[String],
    mlocationlatitude: Option[Double],
    mlocationlongitude: Option[Double],
    mlicenseid: Option[String],
    mlicensename: Option[String],
    mopeningtimeonmonday: Option[String],
    mclosingtimeonmonday: Option[String],
    mopeningtimeontuesday: Option[String],
    mclosingtimeontuesday: Option[String],
    mopeningtimeonwednesday: Option[String],
    mclosingtimeonwednesday: Option[String],
    mopeningtimeonthursday: Option[String]
  )

  private case class AtmRow2(
    mclosingtimeonthursday: Option[String],
    mopeningtimeonfriday: Option[String],
    mclosingtimeonfriday: Option[String],
    mopeningtimeonsaturday: Option[String],
    mclosingtimeonsaturday: Option[String],
    mopeningtimeonsunday: Option[String],
    mclosingtimeonsunday: Option[String],
    misaccessible: Option[String],
    mlocatedat: Option[String],
    mmoreinfo: Option[String],
    mhasdepositcapability: Option[String],
    msupportedlanguages: Option[String],
    mservices: Option[String],
    mnotes: Option[String],
    maccessibilityfeatures: Option[String],
    msupportedcurrencies: Option[String],
    mlocationcategories: Option[String],
    mminimumwithdrawal: Option[String],
    mbranchidentification: Option[String],
    msiteidentification: Option[String],
    msitename: Option[String],
    mcashwithdrawalnationalfee: Option[String]
  )

  private case class AtmRow3(
    mcashwithdrawalinternationalfee: Option[String],
    mbalanceinquiryfee: Option[String],
    matmtype: Option[String],
    mphone: Option[String]
  )

  private type AtmRow = (AtmRow1, AtmRow2, AtmRow3)

  private def nn(s: String): String = if (s == null) "" else s
  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  private def toOptBool(s: Option[String]): Option[Boolean] = s.flatMap {
    case "Y" => Some(true)
    case "N" => Some(false)
    case _   => None
  }

  private def toOptList(s: Option[String]): Option[List[String]] = s.collect {
    case v if v.nonEmpty => v.split(",").toList
  }

  private def toOptStr(s: Option[String]): Option[String] = s.filter(_.nonEmpty)

  private def rowToAtm(r: AtmRow): Atms.Atm = {
    val (r1, r2, r3) = r
    Atms.Atm(
      atmId  = AtmId(r1.matmid.getOrElse("")),
      bankId = BankId(r1.mbankid.getOrElse("")),
      name   = r1.mname.getOrElse(""),
      address = Address(
        line1       = r1.mline1.getOrElse(""),
        line2       = r1.mline2.getOrElse(""),
        line3       = r1.mline3.getOrElse(""),
        city        = r1.mcity.getOrElse(""),
        county      = r1.mcounty.filter(_.nonEmpty),
        state       = r1.mstate.getOrElse(""),
        postCode    = r1.mpostcode.getOrElse(""),
        countryCode = r1.mcountrycode.getOrElse("")
      ),
      location = Location(
        latitude  = r1.mlocationlatitude.getOrElse(0.0),
        longitude = r1.mlocationlongitude.getOrElse(0.0),
        date = None,
        user = None
      ),
      meta = CommonMeta(license = License(
        id   = r1.mlicenseid.getOrElse(""),
        name = r1.mlicensename.getOrElse("")
      )),
      OpeningTimeOnMonday    = r1.mopeningtimeonmonday,
      ClosingTimeOnMonday    = r1.mclosingtimeonmonday,
      OpeningTimeOnTuesday   = r1.mopeningtimeontuesday,
      ClosingTimeOnTuesday   = r1.mclosingtimeontuesday,
      OpeningTimeOnWednesday = r1.mopeningtimeonwednesday,
      ClosingTimeOnWednesday = r1.mclosingtimeonwednesday,
      OpeningTimeOnThursday  = r1.mopeningtimeonthursday,
      ClosingTimeOnThursday  = r2.mclosingtimeonthursday,
      OpeningTimeOnFriday    = r2.mopeningtimeonfriday,
      ClosingTimeOnFriday    = r2.mclosingtimeonfriday,
      OpeningTimeOnSaturday  = r2.mopeningtimeonsaturday,
      ClosingTimeOnSaturday  = r2.mclosingtimeonsaturday,
      OpeningTimeOnSunday    = r2.mopeningtimeonsunday,
      ClosingTimeOnSunday    = r2.mclosingtimeonsunday,
      isAccessible           = toOptBool(r2.misaccessible),
      locatedAt              = toOptStr(r2.mlocatedat),
      moreInfo               = toOptStr(r2.mmoreinfo),
      hasDepositCapability   = toOptBool(r2.mhasdepositcapability),
      supportedLanguages     = toOptList(r2.msupportedlanguages),
      services               = toOptList(r2.mservices),
      accessibilityFeatures  = toOptList(r2.maccessibilityfeatures),
      supportedCurrencies    = toOptList(r2.msupportedcurrencies),
      notes                  = toOptList(r2.mnotes),
      locationCategories     = toOptList(r2.mlocationcategories),
      minimumWithdrawal      = toOptStr(r2.mminimumwithdrawal),
      branchIdentification   = toOptStr(r2.mbranchidentification),
      siteIdentification     = toOptStr(r2.msiteidentification),
      siteName               = toOptStr(r2.msitename),
      cashWithdrawalNationalFee      = toOptStr(r2.mcashwithdrawalnationalfee),
      cashWithdrawalInternationalFee = toOptStr(r3.mcashwithdrawalinternationalfee),
      balanceInquiryFee      = toOptStr(r3.mbalanceinquiryfee),
      atmType                = toOptStr(r3.matmtype),
      phone                  = toOptStr(r3.mphone)
    )
  }

  private val selectCols: Fragment =
    fr"""SELECT
         matmid, mbankid, mname, mline1, mline2, mline3, mcity, mcounty, mstate, mcountrycode, mpostcode,
         mlocationlatitude, mlocationlongitude, mlicenseid, mlicensename,
         mopeningtimeonmonday, mclosingtimeonmonday, mopeningtimeontuesday, mclosingtimeontuesday,
         mopeningtimeonwednesday, mclosingtimeonwednesday, mopeningtimeonthursday,
         mclosingtimeonthursday, mopeningtimeonfriday, mclosingtimeonfriday,
         mopeningtimeonsaturday, mclosingtimeonsaturday, mopeningtimeonsunday, mclosingtimeonsunday,
         misaccessible, mlocatedat, mmoreinfo, mhasdepositcapability,
         msupportedlanguages, mservices, mnotes, maccessibilityfeatures, msupportedcurrencies, mlocationcategories,
         mminimumwithdrawal, mbranchidentification, msiteidentification, msitename, mcashwithdrawalnationalfee,
         mcashwithdrawalinternationalfee, mbalanceinquiryfee, matmtype, mphone
         FROM mappedatm"""

  override protected def getAtmFromProvider(bankId: BankId, atmId: AtmId): Option[AtmT] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)} AND matmid = ${nn(atmId.value)} LIMIT 1")
        .query[AtmRow].option).map(rowToAtm)

  override protected def getAtmsFromProvider(bankId: BankId, queryParams: List[OBPQueryParam]): Option[List[AtmT]] = {
    val limitFr  = queryParams.collectFirst { case OBPLimit(v)  => fr"LIMIT $v"  }.getOrElse(Fragment.empty)
    val offsetFr = queryParams.collectFirst { case OBPOffset(v) => fr"OFFSET $v" }.getOrElse(Fragment.empty)
    Some(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)}" ++ limitFr ++ offsetFr)
        .query[AtmRow].to[List]).map(rowToAtm))
  }

  override def createOrUpdateAtm(atm: AtmT): Box[AtmT] = tryo {
    val isAccessibleStr = optionBooleanToString(atm.isAccessible)
    val hasDepositStr   = optionBooleanToString(atm.hasDepositCapability)
    val langsStr        = atm.supportedLanguages.map(_.mkString(",")).getOrElse("")
    val servicesStr     = atm.services.map(_.mkString(",")).getOrElse("")
    val accessStr       = atm.accessibilityFeatures.map(_.mkString(",")).getOrElse("")
    val currStr         = atm.supportedCurrencies.map(_.mkString(",")).getOrElse("")
    val notesStr        = atm.notes.map(_.mkString(",")).getOrElse("")
    val locCatsStr      = atm.locationCategories.map(_.mkString(",")).getOrElse("")
    val county          = atm.address.county.getOrElse("")

    getAtmFromProvider(atm.bankId, atm.atmId) match {
      case Some(_) =>
        DoobieUtil.runUpdate(sql"""UPDATE mappedatm SET
            mname = ${nn(atm.name)},
            mline1 = ${nn(atm.address.line1)}, mline2 = ${nn(atm.address.line2)}, mline3 = ${nn(atm.address.line3)},
            mcity = ${nn(atm.address.city)}, mcounty = $county, mstate = ${nn(atm.address.state)},
            mcountrycode = ${nn(atm.address.countryCode)}, mpostcode = ${nn(atm.address.postCode)},
            mlocationlatitude = ${atm.location.latitude}, mlocationlongitude = ${atm.location.longitude},
            mlicenseid = ${nn(atm.meta.license.id)}, mlicensename = ${nn(atm.meta.license.name)},
            mopeningtimeonmonday = ${atm.OpeningTimeOnMonday}, mclosingtimeonmonday = ${atm.ClosingTimeOnMonday},
            mopeningtimeontuesday = ${atm.OpeningTimeOnTuesday}, mclosingtimeontuesday = ${atm.ClosingTimeOnTuesday},
            mopeningtimeonwednesday = ${atm.OpeningTimeOnWednesday}, mclosingtimeonwednesday = ${atm.ClosingTimeOnWednesday},
            mopeningtimeonthursday = ${atm.OpeningTimeOnThursday}, mclosingtimeonthursday = ${atm.ClosingTimeOnThursday},
            mopeningtimeonfriday = ${atm.OpeningTimeOnFriday}, mclosingtimeonfriday = ${atm.ClosingTimeOnFriday},
            mopeningtimeonsaturday = ${atm.OpeningTimeOnSaturday}, mclosingtimeonsaturday = ${atm.ClosingTimeOnSaturday},
            mopeningtimeonsunday = ${atm.OpeningTimeOnSunday}, mclosingtimeonsunday = ${atm.ClosingTimeOnSunday},
            misaccessible = $isAccessibleStr, mlocatedat = ${atm.locatedAt}, mmoreinfo = ${atm.moreInfo},
            mhasdepositcapability = $hasDepositStr,
            msupportedlanguages = $langsStr, mservices = $servicesStr, mnotes = $notesStr,
            maccessibilityfeatures = $accessStr, msupportedcurrencies = $currStr, mlocationcategories = $locCatsStr,
            mminimumwithdrawal = ${atm.minimumWithdrawal}, mbranchidentification = ${atm.branchIdentification},
            msiteidentification = ${atm.siteIdentification}, msitename = ${atm.siteName},
            mcashwithdrawalnationalfee = ${atm.cashWithdrawalNationalFee},
            mcashwithdrawalinternationalfee = ${atm.cashWithdrawalInternationalFee},
            mbalanceinquiryfee = ${atm.balanceInquiryFee}, matmtype = ${atm.atmType}, mphone = ${atm.phone},
            updatedat = $now
            WHERE mbankid = ${nn(atm.bankId.value)} AND matmid = ${nn(atm.atmId.value)}""".update.run)
      case None =>
        DoobieUtil.runUpdate(sql"""INSERT INTO mappedatm (
            matmid, mbankid, mname, mline1, mline2, mline3, mcity, mcounty, mstate, mcountrycode, mpostcode,
            mlocationlatitude, mlocationlongitude, mlicenseid, mlicensename,
            mopeningtimeonmonday, mclosingtimeonmonday, mopeningtimeontuesday, mclosingtimeontuesday,
            mopeningtimeonwednesday, mclosingtimeonwednesday, mopeningtimeonthursday, mclosingtimeonthursday,
            mopeningtimeonfriday, mclosingtimeonfriday, mopeningtimeonsaturday, mclosingtimeonsaturday,
            mopeningtimeonsunday, mclosingtimeonsunday,
            misaccessible, mlocatedat, mmoreinfo, mhasdepositcapability,
            msupportedlanguages, mservices, mnotes, maccessibilityfeatures, msupportedcurrencies, mlocationcategories,
            mminimumwithdrawal, mbranchidentification, msiteidentification, msitename,
            mcashwithdrawalnationalfee, mcashwithdrawalinternationalfee, mbalanceinquiryfee, matmtype, mphone,
            createdat, updatedat)
            VALUES (
            ${nn(atm.atmId.value)}, ${nn(atm.bankId.value)}, ${nn(atm.name)},
            ${nn(atm.address.line1)}, ${nn(atm.address.line2)}, ${nn(atm.address.line3)},
            ${nn(atm.address.city)}, $county, ${nn(atm.address.state)},
            ${nn(atm.address.countryCode)}, ${nn(atm.address.postCode)},
            ${atm.location.latitude}, ${atm.location.longitude},
            ${nn(atm.meta.license.id)}, ${nn(atm.meta.license.name)},
            ${atm.OpeningTimeOnMonday}, ${atm.ClosingTimeOnMonday},
            ${atm.OpeningTimeOnTuesday}, ${atm.ClosingTimeOnTuesday},
            ${atm.OpeningTimeOnWednesday}, ${atm.ClosingTimeOnWednesday},
            ${atm.OpeningTimeOnThursday}, ${atm.ClosingTimeOnThursday},
            ${atm.OpeningTimeOnFriday}, ${atm.ClosingTimeOnFriday},
            ${atm.OpeningTimeOnSaturday}, ${atm.ClosingTimeOnSaturday},
            ${atm.OpeningTimeOnSunday}, ${atm.ClosingTimeOnSunday},
            $isAccessibleStr, ${atm.locatedAt}, ${atm.moreInfo}, $hasDepositStr,
            $langsStr, $servicesStr, $notesStr, $accessStr, $currStr, $locCatsStr,
            ${atm.minimumWithdrawal}, ${atm.branchIdentification}, ${atm.siteIdentification}, ${atm.siteName},
            ${atm.cashWithdrawalNationalFee}, ${atm.cashWithdrawalInternationalFee},
            ${atm.balanceInquiryFee}, ${atm.atmType}, ${atm.phone},
            $now, $now)""".update.run)
    }
    rowToAtm(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${nn(atm.bankId.value)} AND matmid = ${nn(atm.atmId.value)} LIMIT 1")
        .query[AtmRow].option)
      .getOrElse(throw new RuntimeException(s"ATM not found after upsert: ${atm.atmId.value}")))
  }

  override def deleteAtm(atm: AtmT): Box[Boolean] =
    Full(DoobieUtil.runUpdate(
      sql"DELETE FROM mappedatm WHERE matmid = ${nn(atm.atmId.value)}".update.run) > 0)

  // Mirrors Lift `MappedAtm.findAll()` (no bankId filter); keeps OBP LIMIT/OFFSET handling.
  override def getAllAtms(queryParams: List[OBPQueryParam]): List[AtmT] = {
    val limitFr  = queryParams.collectFirst { case OBPLimit(v)  => fr"LIMIT $v"  }.getOrElse(Fragment.empty)
    val offsetFr = queryParams.collectFirst { case OBPOffset(v) => fr"OFFSET $v" }.getOrElse(Fragment.empty)
    DoobieUtil.runQuery((selectCols ++ limitFr ++ offsetFr).query[AtmRow].to[List]).map(rowToAtm)
  }

  // Single-column update helper. Mirrors Lift `find().map(_.mXxx(..).saveMe())`: returns Empty when the
  // ATM does not exist (the Lift `.map` over an empty Box yielded Empty), and the re-read row otherwise.
  private def updateColumn(bankId: BankId, atmId: AtmId, setFr: Fragment): Box[AtmT] =
    getAtmFromProvider(bankId, atmId) match {
      case Some(_) =>
        tryo {
          DoobieUtil.runUpdate((fr"UPDATE mappedatm SET" ++ setFr ++
            fr", updatedat = $now WHERE mbankid = ${nn(bankId.value)} AND matmid = ${nn(atmId.value)}").update.run)
          getAtmFromProvider(bankId, atmId).get
        }
      case None => Empty
    }

  override def updateAtmSupportedLanguages(bankId: BankId, atmId: AtmId, v: List[String]): Box[AtmT] =
    updateColumn(bankId, atmId, fr"msupportedlanguages = ${v.mkString(",")}")

  override def updateAtmSupportedCurrencies(bankId: BankId, atmId: AtmId, v: List[String]): Box[AtmT] =
    updateColumn(bankId, atmId, fr"msupportedcurrencies = ${v.mkString(",")}")

  override def updateAtmAccessibilityFeatures(bankId: BankId, atmId: AtmId, v: List[String]): Box[AtmT] =
    updateColumn(bankId, atmId, fr"maccessibilityfeatures = ${v.mkString(",")}")

  override def updateAtmServices(bankId: BankId, atmId: AtmId, v: List[String]): Box[AtmT] =
    updateColumn(bankId, atmId, fr"mservices = ${v.mkString(",")}")

  override def updateAtmNotes(bankId: BankId, atmId: AtmId, v: List[String]): Box[AtmT] =
    updateColumn(bankId, atmId, fr"mnotes = ${v.mkString(",")}")

  override def updateAtmLocationCategories(bankId: BankId, atmId: AtmId, v: List[String]): Box[AtmT] =
    updateColumn(bankId, atmId, fr"mlocationcategories = ${v.mkString(",")}")
}
