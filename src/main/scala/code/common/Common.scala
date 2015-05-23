package code.common

trait License {
    def id : String
    def name : String
  }

  trait Meta {
    def license : License
  }

  trait Address {
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

  trait Location {
    def latitude: Double
    def longitude: Double
  }









