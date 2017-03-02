package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.counterparties.{RemoteCounterpartiesCaseClasses, CounterpartyTrait, Counterparties}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataCounterparties extends Counterparties {

  implicit val timeout = Timeout(10000 milliseconds)
  val TIMEOUT = 10 seconds
  val rCounterparties = RemoteCounterpartiesCaseClasses
  var counterpartiesActor = RemotedataActorSystem.getActor("counterparties")


  override def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty): Box[CounterpartyMetadata] = {
    val res = try {
      Full(
        Await.result(
          (counterpartiesActor ? rCounterparties.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty)).mapTo[CounterpartyMetadata],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getOrCreateMetadata", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId): List[CounterpartyMetadata] = {
    val res = try {
      Full(
        Await.result(
          (counterpartiesActor ? rCounterparties.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId)).mapTo[List[CounterpartyMetadata]],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getMetadatas", 404)
      case e: Throwable => throw e
    }
    res.get
  }

  override def getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[CounterpartyMetadata] = {
    val res = try {
      Full(
        Await.result(
          (counterpartiesActor ? rCounterparties.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String)).mapTo[CounterpartyMetadata],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getMetadata", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getCounterparty(counterPartyId: String): Box[CounterpartyTrait] = {
    val res = try {
      Full(
        Await.result(
          (counterpartiesActor ? rCounterparties.getCounterparty(counterPartyId: String)).mapTo[CounterpartyTrait],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getCounterparty", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] = {
    val res = try {
      Full(
        Await.result(
          (counterpartiesActor ? rCounterparties.getCounterpartyByIban(iban: String)).mapTo[CounterpartyTrait],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getCounterpartyByIban", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def createCounterparty(createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, name: String, otherBankId: String, otherAccountId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, isBeneficiary: Boolean): Box[CounterpartyTrait] = {
    val res = try {
      Full(
        Await.result(
          (counterpartiesActor ? rCounterparties.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId, name, otherBankId, otherAccountId,
                                                           otherAccountRoutingScheme, otherAccountRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress,
                                                           isBeneficiary)).mapTo[CounterpartyTrait],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not  createCounterparty", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String): Boolean = {
    Await.result(
      (counterpartiesActor ? rCounterparties.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)).mapTo[Boolean],
      TIMEOUT
    )
  }

}
