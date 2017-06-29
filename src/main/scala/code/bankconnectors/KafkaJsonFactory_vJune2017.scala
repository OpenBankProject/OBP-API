package code.bankconnectors

import code.api.util.APIUtil.InboundMessageBase

case class InboundAccountJune2017(
                                   errorCode: String,
                                   bankId: String,
                                   branchId: String,
                                   accountId: String,
                                   number: String,
                                   accountType: String,
                                   balanceAmount: String,
                                   balanceCurrency: String,
                                   owners: List[String],
                                   generateViews: List[String],
                                   bankRoutingScheme:String,
                                   bankRoutingAddress:String,
                                   branchRoutingScheme:String,
                                   branchRoutingAddress:String,
                                   accountRoutingScheme:String,
                                   accountRoutingAddress:String
                                 ) extends InboundMessageBase