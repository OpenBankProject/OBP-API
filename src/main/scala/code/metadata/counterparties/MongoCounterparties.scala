package code.metadata.counterparties

import java.util.Date

import code.api.v2_1_0.PostCounterpartyBespoke
import code.model._
import net.liftweb.common.{Box, Empty}
import code.util.Helper.MdcLoggable
import com.mongodb.QueryBuilder
import net.liftweb.util.Helpers.tryo
import net.liftweb.common.Full
import org.bson.types.ObjectId

object MongoCounterparties extends Counterparties with MdcLoggable {


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

  def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherParty : Counterparty) : Box[CounterpartyMetadata] = {

    /**
     * This particular implementation requires the metadata id to be the same as the otherParty (OtherBankAccount) id
     */

    val existing = getMetadata(originalPartyBankId, originalPartyAccountId, otherParty.counterPartyId)

    val metadata = existing match {
      case Full(m) => m
      case _ => createMetadata(originalPartyBankId, originalPartyAccountId, otherParty.label, otherParty.thisAccountId.value)
    }

    Full(metadata)
  }

  /**
   * This only exists for OBPEnvelope. Avoid using it for any other reason outside of this class
   */
  def createMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherAccountHolder : String, otherAccountNumber : String) : Metadata = {
    //create it
    if(otherAccountHolder.isEmpty){
      logger.info("other account holder is Empty. creating a metadata record with private alias")
      //no holder name, nothing to hide, so we don't need to create a public alias
      Metadata
        .createRecord
        .originalPartyBankId(originalPartyBankId.value)
        .originalPartyAccountId(originalPartyAccountId.value)
        .accountNumber(otherAccountNumber)
        .holder("")
        .save(true)

    } else {
      Metadata.createRecord.
        originalPartyBankId(originalPartyBankId.value).
        originalPartyAccountId(originalPartyAccountId.value).
        holder(otherAccountHolder).
        accountNumber(otherAccountNumber).
        publicAlias(newPublicAliasName(originalPartyBankId, originalPartyAccountId)).save(true)
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
                                   otherAccountRoutingScheme: String,
                                   otherAccountRoutingAddress: String,
                                   otherBankRoutingScheme: String,
                                   otherBankRoutingAddress: String,
                                   otherBranchRoutingScheme: String,
                                   otherBranchRoutingAddress: String,
                                   isBeneficiary: Boolean,
                                   otherAccountSecondaryRoutingScheme: String,
                                   otherAccountSecondaryRoutingAddress: String,
                                   description: String,
                                   bespoke: List[PostCounterpartyBespoke]
                                 ): Box[CounterpartyTrait] = Empty

  override def checkCounterpartyAvailable(
                                        name: String,
                                        thisBankId: String,
                                        thisAccountId: String,
                                        thisViewId: String
                                      ): Boolean = false

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]] = ???

  override def addPublicAlias(counterPartyId : String, alias: String): Box[Boolean] = ???
  override def addPrivateAlias(counterPartyId : String, alias: String): Box[Boolean] = ???
  override def addURL(counterPartyId : String, url: String): Box[Boolean] = ???
  override def addImageURL(counterPartyId : String, imageUrl: String): Box[Boolean] = ???
  override def addOpenCorporatesURL(counterPartyId : String, url: String): Box[Boolean] = ???
  override def addMoreInfo(counterPartyId : String, moreInfo: String): Box[Boolean] = ???
  override def addPhysicalLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] = ???
  override def addCorporateLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] = ???
  override def deletePhysicalLocation(counterPartyId : String): Box[Boolean] = ???
  override def deleteCorporateLocation(counterPartyId : String): Box[Boolean] = ???
  override def getCorporateLocation(counterPartyId : String): Box[GeoTag] = ???
  override def getPhysicalLocation(counterPartyId : String): Box[GeoTag] = ???
  override def getOpenCorporatesURL(counterPartyId : String): Box[String] = ???
  override def getImageURL(counterPartyId : String): Box[String] = ???
  override def getUrl(counterPartyId : String): Box[String] = ???
  override def getMoreInfo(counterPartyId : String): Box[String] = ???
  override def getPublicAlias(counterPartyId : String): Box[String] = ???
  override def getPrivateAlias(counterPartyId : String): Box[String] = ???
}
