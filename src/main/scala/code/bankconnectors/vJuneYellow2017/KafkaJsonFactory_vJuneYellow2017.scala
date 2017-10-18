package code.bankconnectors.vJuneYellow2017

import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.bankconnectors.vJune2017.AuthInfo
import code.bankconnectors.vMar2017.InboundStatusMessage
import code.kafka.Topics.TopicTrait

case class OutboundCreateTransaction(
  authInfo: AuthInfo,
  
  // fromAccount
  fromAccountBankId : String,
  fromAccountId : String,
  
  // transaction details
  transactionRequestType: String,
  transactionChargePolicy: String,
  transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
  
  // toAccount or toCounterparty
  toCounterpartyId: String,
  toCounterpartyName: String,
  toCounterpartyCurrency: String,
  toCounterpartyRoutingAddress: String,
  toCounterpartyRoutingScheme: String,
  toCounterpartyBankRoutingAddress: String,
  toCounterpartyBankRoutingScheme: String

) extends TopicTrait

case class InternalTransactionId(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  id : String
)
case class InboundCreateTransactionId(authInfo: AuthInfo, data: InternalTransactionId)


object JsonFactory_vJuneYellow2017 {
  
}