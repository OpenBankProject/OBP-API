package code.api.MxOF

import code.api.util.{APIUtil, CustomJsonFormats}
import code.api.util.APIUtil.{defaultBankId, listOrNone, stringOrNone, theEpochTime}
import code.atms.MappedAtm
import code.bankattribute.BankAttribute
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
  LastUpdated: String,
  TotalResults: Double,
  Agreement: String,
  License: String,
  TermsOfUse: String
)
case class OtherAccessibility(
  Code: Option[String],
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
  AddressLine: Option[String],
  BuildingNumber: Option[String],
  StreetName: Option[String],
  TownName: Option[String],
  CountrySubDivision: Option[List[String]],
  Country: Option[String],
  PostCode: Option[String],
  GeoLocation: Option[GeoLocation]
)
case class Location(
  LocationCategory: Option[List[String]],
  OtherLocationCategory: Option[List[OtherAccessibility]],
  Site: Option[Site],
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
  Accessibility: Option[List[String]],
  Access24HoursIndicator: Option[Boolean],
  SupportedCurrencies: List[String],
  MinimumPossibleAmount: Option[String],
  Note: Option[List[String]],
  OtherAccessibility: Option[List[OtherAccessibility]],
  OtherATMServices: Option[List[OtherAccessibility]],
  Branch: Option[MxofBranchV100],
  Location: Location,
  FeeSurcharges: Option[FeeSurcharges]
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

  //get the following values from default bank Attributes:
  final val BANK_ATTRIBUTE_AGREEMENT = "ATM_META_AGREEMENT"
  final val BANK_ATTRIBUTE_LICENSE = "ATM_META_LICENCE"
  final val BANK_ATTRIBUTE_TERMSOFUSE = "ATM_META_TERMS_OF_USE"
  
   def createGetAtmsResponse (banks: List[Bank], atms: List[AtmT], attributes:List[BankAttribute]) :GetAtmsResponseJson = {
     def access24HoursIndicator (atm: AtmT) = {
       atm.OpeningTimeOnMonday.equals(Some("00:00")) && atm.ClosingTimeOnMonday.equals(Some("23:59"))
       atm.OpeningTimeOnTuesday.equals(Some("00:00")) && atm.ClosingTimeOnTuesday.equals(Some("23:59"))
       atm.OpeningTimeOnWednesday.equals(Some("00:00")) && atm.ClosingTimeOnWednesday.equals(Some("23:59"))
       atm.OpeningTimeOnThursday.equals(Some("00:00")) && atm.ClosingTimeOnThursday.equals(Some("23:59"))
       atm.OpeningTimeOnFriday.equals(Some("00:00")) && atm.ClosingTimeOnFriday.equals(Some("23:59"))
       atm.OpeningTimeOnSaturday.equals(Some("00:00")) && atm.ClosingTimeOnSaturday.equals(Some("23:59"))
       atm.OpeningTimeOnSunday.equals(Some("00:00")) && atm.ClosingTimeOnSunday.equals(Some("23:59"))
     }
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
             Accessibility = bankAtm.accessibilityFeatures,
             Access24HoursIndicator = Some(access24HoursIndicator(bankAtm)), 
             SupportedCurrencies = bankAtm.supportedCurrencies.getOrElse(Nil),
             MinimumPossibleAmount = bankAtm.minimumWithdrawal,   
             Note = bankAtm.notes,
             OtherAccessibility = None, // List(OtherAccessibility("","","")), //TODO 1 from attributes?
             OtherATMServices =  None, // List(OtherAccessibility("","","")), //TODO 2 from attributes?
             Branch = if (bankAtm.branchIdentification.getOrElse("").isEmpty) 
               None 
             else 
               Some(MxofBranchV100(bankAtm.branchIdentification.getOrElse(""))), 
             Location = Location(
               LocationCategory = bankAtm.locationCategories,
               OtherLocationCategory = None, //List(OtherAccessibility(None,"","")), //TODO 3 from attributes?
               Site = if(bankAtm.siteIdentification.getOrElse("").isEmpty && bankAtm.siteName.getOrElse("").isEmpty) 
                 None 
               else Some(Site(
                 Identification = bankAtm.siteIdentification.getOrElse(""),
                 Name= bankAtm.siteName.getOrElse("")
               )),
               PostalAddress = PostalAddress(
                 AddressLine = stringOrNone(bankAtm.address.line1),
                 BuildingNumber = stringOrNone(bankAtm.address.line2),
                 StreetName = stringOrNone(bankAtm.address.line3),
                 TownName = stringOrNone(bankAtm.address.city),
                 CountrySubDivision = listOrNone(bankAtm.address.state), 
                 Country = bankAtm.address.county,
                 PostCode = stringOrNone(bankAtm.address.postCode),
                 GeoLocation = if (bankAtm.location.latitude.toString.isEmpty && bankAtm.location.longitude.toString.isEmpty)
                   None
                 else 
                   Some(GeoLocation(
                     GeographicCoordinates(
                       bankAtm.location.latitude.toString,
                       bankAtm.location.longitude.toString))))
             ),
             FeeSurcharges = if (bankAtm.branchIdentification.getOrElse("").isEmpty && 
               bankAtm.cashWithdrawalNationalFee.getOrElse("").isEmpty &&
               bankAtm.balanceInquiryFee.getOrElse("").isEmpty
             ) 
               None 
             else 
               Some(FeeSurcharges(
                 CashWithdrawalNational = bankAtm.cashWithdrawalNationalFee.getOrElse(""),
                 CashWithdrawalInternational = bankAtm.cashWithdrawalNationalFee.getOrElse(""),
                 BalanceInquiry = bankAtm.balanceInquiryFee.getOrElse("")))
           )
         }
       )
     }
     )
     val mappedAtmList: List[MappedAtm] = MappedAtm.findAll(
       NotNullRef(MappedAtm.updatedAt),
       OrderBy(MappedAtm.updatedAt, Descending),
     )
     
     val lastUpdated: Date = if(mappedAtmList.nonEmpty) {
        mappedAtmList.head.updatedAt.get
       }else{
        theEpochTime
       }
     
     val agreement = attributes.find(_.name.equals(BANK_ATTRIBUTE_AGREEMENT)).map(_.value).getOrElse("")
     val license = attributes.find(_.name.equals(BANK_ATTRIBUTE_LICENSE)).map(_.value).getOrElse("")
     val termsOfUse = attributes.find(_.name.equals(BANK_ATTRIBUTE_TERMSOFUSE)).map(_.value).getOrElse("")
       
     GetAtmsResponseJson(
       meta = MetaBis(
         LastUpdated = APIUtil.DateWithMsFormat.format(lastUpdated),
         TotalResults = atms.size.toDouble,
         Agreement = agreement,
         License = license,
         TermsOfUse = termsOfUse
       ),
       data = List(Data(brandList))
     )
   }
}
