package code.metadata.counterparties

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import code.model.{AccountId, BankId, CounterpartyMetadata, Counterparty}

object Counterparties extends SimpleInjector {

  val counterparties = new Inject(buildOne _) {}

  def buildOne: Counterparties = MapperCounterparties

}

trait Counterparties {

  def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherParty : Counterparty) : CounterpartyMetadata

  //get all counterparty metadatas for a single OBP account
  def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId : AccountId) : List[CounterpartyMetadata]

  def getMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, counterpartyMetadataId : String) : Box[CounterpartyMetadata]

  def getCounterparty(counterPartyId : String): Box[CounterpartyTrait]

  def getCounterpartyByIban(iban : String): Box[CounterpartyTrait]

  def createCounterparty(
                          createdByUserId: String,
                          thisBankId: String,
                          thisAccountId: String,
                          thisViewId: String,
                          name: String,
                          otherBankId: String,
                          otherAccountId: String,
                          accountRoutingScheme: String,
                          accountRoutingAddress: String,
                          bankRoutingScheme: String,
                          bankRoutingAddress: String,
                          isBeneficiary:Boolean
                        ): Box[CounterpartyTrait]

  def checkCounterpartyAvailable(
                                  name: String,
                                  thisBankId: String,
                                  thisAccountId: String,
                                  thisViewId: String
                                ): Boolean
}

trait CounterpartyTrait {
  def createdByUserId: String // The user that created this counterparty
  def name: String // The name the user knows this counterparty by
  def thisBankId: String // The local Account.bankId that uses this Counterparty
  def thisAccountId: String // The local Account.accountId that uses this Counterparty
  def thisViewId: String // The view that this Counterparty was created through
  def otherBankId: String // The remote Bank that this Counterparty represents.
  def otherAccountId: String // The remote Account.accountId that this Counterparty represents.
  def otherAccountProvider: String // The provider that services payments to this Counterparty
  def counterPartyId: String // A unique id for the Counterparty
  def accountRoutingScheme: String // A national, regional  or international routing scheme for the account number e.g. IBAN
  def accountRoutingAddress: String // The national, regional or international routing address id e.g. an IBAN number
  def bankRoutingScheme: String // A national, regional  or international routing scheme for banks e.g. BIC
  def bankRoutingAddress: String // The national, regional or international routing address id e.g. a BIC
  def isBeneficiary : Boolean // Must be set to true to create TransactionRequests

}