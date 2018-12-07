package code.bankconnectors.akka

import code.api.util.CallContextAkka
import code.model.{BankId, Bank => BankTrait}

import scala.collection.immutable.List


/**
  *
  * case classes used to define outbound Akka messages
  *
  */
case class OutboundGetAdapterInfo(date: String, callContext: Option[CallContextAkka])
case class OutboundGetBanks(callContext: Option[CallContextAkka])
case class OutboundGetBank(bankId: String, callContext: Option[CallContextAkka])

/**
  *
  * case classes used to define inbound Akka messages
  *
  */
case class InboundGetBanks(banks: Option[List[Bank]], callContext: Option[CallContextAkka])
case class InboundGetBank(bank: Option[Bank], callContext: Option[CallContextAkka])
case class InboundAdapterInfo(
                               name: String,
                               version: String,
                               git_commit: String,
                               date: String, 
                               callContext: Option[CallContextAkka]
                             )



case class Bank(bankId: BankId,
                shortName: String,
                fullName: String,
                logoUrl: String,
                websiteUrl: String,
                bankRoutingScheme: String,
                bankRoutingAddress: String
               )

case class BankAkka(b: Bank) extends BankTrait {
  override def bankId = b.bankId
  override def fullName = b.fullName
  override def shortName = b.shortName
  override def logoUrl = b.logoUrl
  override def websiteUrl = b.websiteUrl
  override def bankRoutingScheme = b.bankRoutingScheme
  override def bankRoutingAddress = b.bankRoutingAddress
  override def swiftBic = ""
  override def nationalIdentifier: String = ""
}