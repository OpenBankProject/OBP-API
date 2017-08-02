package code.common

import java.util.Date

// We use traits so we can override in the Provider for database access etc.
// We use case classes based on the traits so we can easily construct a data structure like the trait.


trait LicenseT {
    def id : String
    def name : String
  }

case class License (
   id : String,
   name : String
) extends LicenseT



  trait MetaT {
    def license : LicenseT
  }

case class Meta (
  license : License
) extends MetaT


  trait AddressT {
    def line1 : String
    def line2 : String
    def line3 : String
    def city : String
    def county : Option[String]
    def state : String
    def postCode : String
    //ISO_3166-1_alpha-2
    def countryCode : String
  }







case class Address(
line1 : String,
line2 : String,
line3 : String,
city : String,
county : Option[String],
state : String,
postCode : String,
//ISO_3166-1_alpha-2
countryCode : String) extends AddressT


  trait LocationT {
    def latitude: Double
    def longitude: Double
  }


case class Location(
                     latitude: Double,
                     longitude: Double,
                     date : Option[Date],
                     user: Option[BasicResourceUser]
) extends LocationT




case class OpeningTimes(
  openingTime: String,
  closingTime: String
)


trait RoutingT {
  def scheme: String
  def address: String
}

case class Routing(
  scheme: String,
  address: String
) extends RoutingT


/*
Basic User data
 */
case class BasicResourceUser(
   userId: String, // Should come from Resource User Id
   provider: String,
   username: String
)







