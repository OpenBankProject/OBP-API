package code.api.MxOF

import code.api.util.{APIUtil, CustomJsonFormats}
import code.atms.MappedAtm
import com.openbankproject.commons.model.Bank
import net.liftweb.json.JValue

import scala.collection.immutable.List
import com.openbankproject.commons.model._
import net.liftweb.mapper.{Descending, NotNullRef, OrderBy}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

case class JvalueCaseClass(jvalueToCaseclass: JValue)

case class MetaBis(
  LastUpdated: String = "",
  TotalResults: Double=0,
  Agreement: String ="",
  License: String="",
  TermsOfUse: String=""
)
case class OtherAccessibility(
  Code: String,
  Description: String,
  Name: String
)
case class MxofBranchV100(
  Identification: String
)
case class Site(
  Identification: String,
  Name: String
)
case class GeographicCoordinates(
  Latitude: String,
  Longitude: String
)
case class GeoLocation(
  GeographicCoordinates: GeographicCoordinates
)
case class PostalAddress(
  AddressLine: String,
  BuildingNumber: String,
  StreetName: String,
  TownName: String,
  CountrySubDivision: List[String],
  Country: String,
  PostCode: String,
  GeoLocation: GeoLocation
)
case class Location(
  LocationCategory: List[String],
  OtherLocationCategory: List[OtherAccessibility],
  Site: Site,
  PostalAddress: PostalAddress
)
case class FeeSurcharges(
  CashWithdrawalNational: String,
  CashWithdrawalInternational: String,
  BalanceInquiry: String
)
case class MxofATMV100(
  Identification: String,
  SupportedLanguages: Option[List[String]],
  ATMServices: List[String],
  Accessibility: List[String],
  Access24HoursIndicator: Boolean,
  SupportedCurrencies: List[String],
  MinimumPossibleAmount: String,
  Note: List[String],
  OtherAccessibility: List[OtherAccessibility],
  OtherATMServices: List[OtherAccessibility],
  Branch: MxofBranchV100,
  Location: Location,
  FeeSurcharges: FeeSurcharges
)
case class Brand(
  BrandName: String,
  ATM: List[MxofATMV100]
)
case class Data(
  Brand: List[Brand]
)
case class GetAtmsResponseJson(
  meta: MetaBis,
  data: List[Data],
)
object JSONFactory_MXOF_0_0_1 extends CustomJsonFormats {
   def createGetAtmsResponse (banks: List[Bank], atms: List[AtmT]) :GetAtmsResponseJson = {
     val brandList = banks
       //first filter out the banks without the atms
       .filter(bank =>atms.map(_.bankId).contains(bank.bankId))
       .map(bank => {
       val bankAtms = atms.filter(_.bankId== bank.bankId)
       Brand(
         BrandName = bank.fullName,
         ATM = bankAtms.map{ bankAtm =>
           MxofATMV100(
             Identification = bankAtm.atmId.value,
             SupportedLanguages = bankAtm.supportedLanguages,
             ATMServices = bankAtm.services.getOrElse(Nil),  
             Accessibility = bankAtm.accessibilityFeatures.getOrElse(Nil),
             Access24HoursIndicator = true, //TODO this can be caculated from all 7 days bankAtm.OpeningTimes
             SupportedCurrencies = bankAtm.supportedCurrencies.getOrElse(Nil),
             MinimumPossibleAmount = bankAtm.minimumWithdrawal.getOrElse(""),   
             Note = bankAtm.notes.getOrElse(Nil),
             OtherAccessibility = List(OtherAccessibility("","","")), //TODO 1 from attributes?
             OtherATMServices = List(OtherAccessibility("","","")), //TODO 2 from attributes?
             Branch = MxofBranchV100(bankAtm.branchIdentification.getOrElse("")), 
             Location = Location(
               LocationCategory = bankAtm.locationCategories.getOrElse(Nil),
               OtherLocationCategory = List(OtherAccessibility("","","")), //TODO 3 from attributes?
               Site = Site(
                 Identification = bankAtm.siteIdentification.getOrElse(""),
                 Name= bankAtm.siteName.getOrElse("")
               ),
               PostalAddress = PostalAddress(
                 AddressLine= bankAtm.address.line1,
                 BuildingNumber= bankAtm.address.line2,
                 StreetName= bankAtm.address.line3,
                 TownName= bankAtm.address.city,
                 CountrySubDivision = List(bankAtm.address.state), 
                 Country = bankAtm.address.county.getOrElse(""),
                 PostCode= bankAtm.address.postCode,
                 GeoLocation = GeoLocation(
                   GeographicCoordinates(
                     bankAtm.location.latitude.toString,
                     bankAtm.location.longitude.toString
                     
                   )
                 )
               )
             ),
             FeeSurcharges = FeeSurcharges(
               CashWithdrawalNational = bankAtm.cashWithdrawalNationalFee.getOrElse(""),
               CashWithdrawalInternational = bankAtm.cashWithdrawalNationalFee.getOrElse(""),
               BalanceInquiry = bankAtm.balanceInquiryFee.getOrElse(""))
           )
         }
       )
     }
     )
     val lastUpdated: Date = MappedAtm.findAll(
       NotNullRef(MappedAtm.updatedAt),
       OrderBy(MappedAtm.updatedAt, Descending),
     ).head.updatedAt.get
     
     GetAtmsResponseJson(
       meta = MetaBis(
         LastUpdated = APIUtil.DateWithMsFormat.format(lastUpdated),
         TotalResults = atms.size.toDouble
       ),
       data = List(Data(brandList))
     )
   }
}
