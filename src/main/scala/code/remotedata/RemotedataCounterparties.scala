package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.counterparties.{Counterparties, CounterpartyTrait, RemotedataCounterpartiesCaseClasses}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataCounterparties extends ActorInit with Counterparties {

  val cc = RemotedataCounterpartiesCaseClasses

  override def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty): Box[CounterpartyMetadata] =
    extractFutureToBox(actor ? cc.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty))

  override def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId): List[CounterpartyMetadata] =
    extractFuture(actor ? cc.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId))

  override def getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[CounterpartyMetadata] =
    extractFutureToBox(actor ? cc.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String))

  override def getCounterparty(counterPartyId: String): Box[CounterpartyTrait] =
    extractFutureToBox(actor ? cc.getCounterparty(counterPartyId: String))

  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] =
    extractFutureToBox(actor ? cc.getCounterpartyByIban(iban: String))
  
  override def createCounterparty(createdByUserId: String,
                                  thisBankId: String,
                                  thisAccountId: String,
                                  thisViewId: String,
                                  name: String,
                                  otherAccountRoutingScheme: String,
                                  otherAccountRoutingAddress: String,
                                  otherBankRoutingScheme: String,
                                  otherBankRoutingAddress: String,
                                  isBeneficiary: Boolean): Box[CounterpartyTrait] =
    extractFutureToBox(actor ? cc.createCounterparty( createdByUserId, thisBankId,
                                                      thisAccountId, thisViewId, name,
                                                      otherAccountRoutingScheme,
                                                      otherAccountRoutingAddress,
                                                      otherBankRoutingScheme,
                                                      otherBankRoutingAddress,
                                                      isBeneficiary))

  override def checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String): Boolean =
    extractFuture(actor ? cc.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String))

}
