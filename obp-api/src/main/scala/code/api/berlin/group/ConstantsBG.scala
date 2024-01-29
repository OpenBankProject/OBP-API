package code.api.berlin.group

object ConstantsBG {
  object SigningBasketsStatus extends Enumeration {
    type SigningBasketsStatus = Value
    // Only the codes
    // 1) RCVD (Received),
    // 2) PATC (PartiallyAcceptedTechnical Correct) The payment initiation needs multiple authentications, where some but not yet all have been performed. Syntactical and semantical validations are successful.,
    // 3) ACTC (AcceptedTechnicalValidation) ,
    // 4) CANC (Cancelled) and
    // 5) RJCT (Rejected) are supported for signing baskets.
    val RCVD, PATC, ACTC, CANC, RJCT = Value
  }
}
