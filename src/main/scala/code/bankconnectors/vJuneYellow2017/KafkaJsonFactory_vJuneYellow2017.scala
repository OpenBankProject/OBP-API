package code.bankconnectors.vJuneYellow2017

import code.bankconnectors.vJune2017.AuthInfo
import code.bankconnectors.vMar2017.InboundStatusMessage


case class InternalTransactionId(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  id : String
)
case class InboundCreateTransactionId(authInfo: AuthInfo, data: InternalTransactionId)


object JsonFactory_vJuneYellow2017 {
  
}