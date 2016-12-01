package code.metadata.counterparties

import code.model.{AccountId, BankId, CounterpartyMetadata, Counterparty}
import net.liftweb.common.{Box, Loggable, Empty}
import com.mongodb.QueryBuilder
import net.liftweb.util.Helpers.tryo
import net.liftweb.common.Full
import org.bson.types.ObjectId

object MongoCounterparties extends Counterparties with Loggable {


  def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId : AccountId) : List[CounterpartyMetadata] = {
    val query = QueryBuilder.start("originalPartyBankId").is(originalPartyBankId.value).put("originalPartyAccountId").is(originalPartyAccountId.value).get
    Metadata.findAll(query)
  }

  def getMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, counterpartyMetadataId : String) : Box[CounterpartyMetadata] = {
    /**
     * This particular implementation requires the metadata id to be the same as the otherParty (OtherBankAccount) id
     */
    for {
      objId <- tryo { new ObjectId(counterpartyMetadataId) }
      query = QueryBuilder.start("originalPartyBankId").is(originalPartyBankId.value).put("originalPartyAccountId").is(originalPartyAccountId.value).
        put("_id").is(objId).get()
      m <- Metadata.find(query)
    } yield m
  }

  def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherParty : Counterparty) : CounterpartyMetadata = {

    /**
     * This particular implementation requires the metadata id to be the same as the otherParty (OtherBankAccount) id
     */

    val existing = getMetadata(originalPartyBankId, originalPartyAccountId, otherParty.counterPartyId)

    val metadata = existing match {
      case Full(m) => m
      case _ => createMetadata(originalPartyBankId, originalPartyAccountId, otherParty.label, otherParty.otherBankId)
    }

    metadata
  }

  /**
   * This only exists for OBPEnvelope. Avoid using it for any other reason outside of this class
   */
  def createMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherAccountHolder : String, otherAccountNumber : String) : Metadata = {
    //create it
    if(otherAccountHolder.isEmpty){
      logger.info("other account holder is Empty. creating a metadata record with no public alias")
      //no holder name, nothing to hide, so we don't need to create a public alias
      Metadata
        .createRecord
        .originalPartyBankId(originalPartyBankId.value)
        .originalPartyAccountId(originalPartyAccountId.value)
        .accountNumber(otherAccountNumber)
        .holder("")
        .save

    } else {
      Metadata.createRecord.
        originalPartyBankId(originalPartyBankId.value).
        originalPartyAccountId(originalPartyAccountId.value).
        holder(otherAccountHolder).
        accountNumber(otherAccountNumber).
        publicAlias(newPublicAliasName(originalPartyBankId, originalPartyAccountId)).save
    }
  }

  /**
   * Generates a new alias name that is guaranteed not to collide with any existing public alias names
   * for the account in question
   */
  def newPublicAliasName(originalPartyBankId : BankId, originalPartyAccountId : AccountId): String = {
    import scala.util.Random

    val firstAliasAttempt = "ALIAS_" + Random.nextLong().toString.take(6)

    /**
     * Returns true if @publicAlias is already the name of a public alias within @account
     */
    def isDuplicate(publicAlias: String) = {
      val query = QueryBuilder.start("originalPartyBankId").is(originalPartyBankId.value).put("originalPartyAccountId").is(originalPartyAccountId.value).get()
      Metadata.findAll(query).exists(m => {
        m.publicAlias.get == publicAlias
      })
    }

    /**
     * Appends things to @publicAlias until it a unique public alias name within @account
     */
    def appendUntilUnique(publicAlias: String): String = {
      val newAlias = publicAlias + Random.nextLong().toString.take(1)
      if (isDuplicate(newAlias)) appendUntilUnique(newAlias)
      else newAlias
    }

    if (isDuplicate(firstAliasAttempt)) appendUntilUnique(firstAliasAttempt)
    else firstAliasAttempt
  }

  override def getCounterparty(counterPartyId : String): Box[CounterpartyTrait] = Empty

  override def getCounterpartyByIban(counterPartyId : String): Box[CounterpartyTrait] = Empty

  override def createCounterparty(
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
                                   isBeneficiary: Boolean
                                 ): Box[CounterpartyTrait] = Empty

  override def checkCounterpartyAvailable(
                                        name: String,
                                        thisBankId: String,
                                        thisAccountId: String,
                                        thisViewId: String
                                      ): Boolean = false
}