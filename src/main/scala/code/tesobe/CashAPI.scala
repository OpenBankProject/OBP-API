package code.tesobe

import java.util.Date

case class CashTransaction(
  otherParty : String,
  date : Date,
  amount : Double,
  kind : String,
  label : String,
  otherInformation : String
  )

case class ErrorMessage(error: String)

case class SuccessMessage(success: String)


