package code.common

trait License {
    def id : String
    def name : String
  }

  trait Meta {
    def license : License
  }

  trait AddressT {
    def line1 : String
    def line2 : String
    def line3 : String
    def city : String
    def county : String
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
county : String,
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
  longitude: Double
                      ) extends LocationT




case class OpeningTimes(
                         openingTime: String,
                         closingTime: String
                       )

case class Routing(
                    scheme: String,
                    address: String
                  )







