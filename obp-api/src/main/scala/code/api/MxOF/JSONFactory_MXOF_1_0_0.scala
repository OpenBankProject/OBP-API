package code.api.MxOF

import code.api.util.CustomJsonFormats
import com.openbankproject.commons.model.Bank
import net.liftweb.json.JValue

import scala.collection.immutable.List
import com.openbankproject.commons.model._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
             SupportedLanguages = Some(List("")),//TODO provide dummy data firstly, need to prepare obp data and map it.
             ATMServices = List(""),  //TODO provide dummy data firstly, need to prepare obp data and map it. 
             Accessibility = List(""), //TODO provide dummy data firstly, need to prepare obp data and map it. 
             Access24HoursIndicator = true,//TODO 6 
             SupportedCurrencies = List(""), //TODO provide dummy data firstly, need to prepare obp data and map it.
             MinimumPossibleAmount = "", //TODO provide dummy data firstly, need to prepare obp data and map it. 
             Note = List(""),//TODO provide dummy data firstly, need to prepare obp data and map it. 
             OtherAccessibility = List(OtherAccessibility("","","")), //TODO8 Add table atm_other_accessibility_features with atm_id and the fields below and add OBP PUT endpoint to set /atms/ATM_ID/other-accessibility-features
             OtherATMServices = List(OtherAccessibility("","","")), //TODO 9 Add table atm_other_services with atm_id and the fields below and add OBP PUT endpoint to set /atms/ATM_ID/other-services              
             Branch = MxofBranchV100(""), //TODO provide dummy data firstly, need to prepare obp data and map it. 
             Location = Location(
               LocationCategory = List("",""), //TODO provide dummy data firstly, need to prepare obp data and map it. 
               OtherLocationCategory = List(OtherAccessibility("","","")), //TODO 12 Add Table atm_other_location_category with atm_id and the following fields and a PUT endpoint /atms/ATM_ID/other-location-categories
               Site = Site(
                 Identification = "",
                 Name= ""
               ),//TODO provide dummy data firstly, need to prepare obp data and map it. 
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
               CashWithdrawalNational = "",
               CashWithdrawalInternational = "",
               BalanceInquiry = "") //TODO provide dummy data firstly, need to prepare obp data and map it. 
           )
         }
       )
     }
     )
     GetAtmsResponseJson(
       meta = MetaBis(
         LastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")),
         TotalResults = atms.size.toDouble
       ),
       data = List(Data(brandList))
     )
   }
}
