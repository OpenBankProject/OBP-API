package code.api.v1_3_0

import java.util.Date
import code.api.v1_2_1.ThisAccountJSON

case class PhysicalCardsJSON(
  cards : List[PhysicalCardJSON])

case class PhysicalCardJSON(
   bank_card_number : String,
   name_on_card : String,
   issue_number : String,
   serial_number : String,
   valid_from_date : Date,
   expires_date : Date,
   enabled : Boolean,
   cancelled : Boolean,
   on_hot_list : Boolean,
   technology : String,
   networks : List[String],
   allows : List[String],
   account : ThisAccountJSON,
   replacement : ReplacementJSON,
   pin_reset : List[PinResetJSON],
   collected : Date,
   posted : Date)

case class ReplacementJSON(
  requested_date : Date,
  reason_requested : String)

case class PinResetJSON(
   requested_date : Date,
   reason_requested : String)

class JSONFactory1_3_0 {

}
