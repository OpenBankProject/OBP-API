package code.api.MxOpenBanking

import code.api.util.CustomJsonFormats
import com.openbankproject.commons.model.Bank
import net.liftweb.json.JValue

import scala.collection.immutable.List
import com.openbankproject.commons.model._

case class JvalueCaseClass(jvalueToCaseclass: JValue)

case class MetaBis(
  LastUpdated: String = "2021-05-25T17:59:24.297Z",
  TotalResults: Double=0,
  Agreement: String ="To be confirmed",
  License: String="To be confirmed",
  TermsOfUse: String="To be confirmed"
)
case class OtherAccessibility(
  Code: String,
  Description: String,
  Name: String
)
case class MxBranchV100(
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
case class MxATMV100(
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
  Branch: MxBranchV100,
  Location: Location,
  FeeSurcharges: FeeSurcharges
)
case class Brand(
  BrandName: String,
  ATM: List[MxATMV100]
)
case class Data(
  Brand: List[Brand]
)
case class GetAtmsResponseJson(
  meta: MetaBis,
  data: List[Data]
)
object JSONFactory_MX_OPEN_FINANCE_0_0_1 extends CustomJsonFormats {
   def createGetAtmsResponse (banks: List[Bank], atms: List[AtmT]) :GetAtmsResponseJson = {
     val brandList = banks
       //first filter out the banks without the atms
       .filter(bank =>atms.map(_.bankId).contains(bank.bankId))
       .map(bank => {
       val bankAtms = atms.filter(_.bankId== bank.bankId)
       Brand(
         BrandName = bank.fullName,
         ATM = bankAtms.map{ bankAtm =>
           MxATMV100(
             Identification = bankAtm.atmId.value,
             SupportedLanguages = bankAtm.supportedLanguages,//TODO1 Add field ATMS.supported_languages (comma separated list) and add OBP PUT endpoint to set /atms/supported-languages
             ATMServices = Nil,  //TODO 2 # Add field ATM.services (comma separated list) and add OBP PUT endpoint to set /atms/services
             Accessibility = Nil, //TODO 3 # Add field ATM.accesibility_features (comma separated list) and add OBP PUT endpoint to set /atms/accesibility-features
             Access24HoursIndicator = true,//TODO 6 
             SupportedCurrencies = Nil, //TODO 4 Add field ATM.supported_currencies (comma separated list) and add OBP PUT endpoint to set /atms/supported-currencies
             MinimumPossibleAmount = "String", //TODO 5 Add String field ATM.minimum_withdrawal and add OBP PUT endpoint to set /atms/minimum-withdrawal
             Note = Nil,//TODO 7
             OtherAccessibility = Nil, //TODO8 Add table atm_other_accesibility_features with atm_id and the fields below and add OBP PUT endpoint to set /atms/ATM_ID/other-accesibility-features
             OtherATMServices = Nil, //TODO 9 Add table atm_other_services with atm_id and the fields below and add OBP PUT endpoint to set /atms/ATM_ID/other-services              
             Branch = MxBranchV100("'"), //TODO 10 Add field branch_identification String
             Location = Location(
               LocationCategory = Nil,
               OtherLocationCategory = Nil,
               Site = Site("",""),
               PostalAddress = PostalAddress(
                 AddressLine= "String",
                 BuildingNumber= "String",
                 StreetName= "String",
                 TownName= "String",
                 CountrySubDivision = Nil, 
                 Country = "String",
                 PostCode= "String",
                 GeoLocation = GeoLocation(
                   GeographicCoordinates(
                     bankAtm.location.latitude.toString,
                     bankAtm.location.longitude.toString
                     
                   )
                 )
               )
             ), //TODO 11
             FeeSurcharges = FeeSurcharges("","","") //TODO 12
           )
         }
       )
     }
     )
     GetAtmsResponseJson(
       meta = MetaBis(),
       data = List(Data(brandList))
     )
   }
}
