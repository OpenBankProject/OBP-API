package code.metadata.counterparties

import java.util.Date

import code.api.util.CallContext
import code.model._
import code.util.Helper.MdcLoggable
import com.mongodb.QueryBuilder
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
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

  def getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String)  : Box[CounterpartyMetadata] = {

    /**
     * This particular implementation requires the metadata id to be the same as the otherParty (OtherBankAccount) id
     */

    val existing = getMetadata(bankId, accountId, counterpartyId)

    val metadata = existing match {
      case Full(m) => m
      case _ => createMetadata(bankId, accountId, counterpartyName, counterpartyId)
    }

    Full(metadata)
  }

  /**
   * This only exists for OBPEnvelope. Avoid using it for any other reason outside of this class
   */
  def createMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherAccountHolder : String, counterpartyId : String) : Metadata = {
    //create it
    if(otherAccountHolder.isEmpty){
      logger.info("other account holder is Empty. creating a metadata record with private alias")
      //no holder name, nothing to hide, so we don't need to create a public alias
      Metadata
        .createRecord
        .counterpartyId(counterpartyId)
        .originalPartyBankId(originalPartyBankId.value)
        .originalPartyAccountId(originalPartyAccountId.value)
//        .accountNumber(otherAccountNumber)
        .holder("")
        .save(true)

    } else {
      Metadata.createRecord.
        counterpartyId(counterpartyId).
        originalPartyBankId(originalPartyBankId.value).
        originalPartyAccountId(originalPartyAccountId.value).
        holder(otherAccountHolder).
//        accountNumber(otherAccountNumber).
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

  override def getCounterparty(counterpartyId : String): Box[CounterpartyTrait] = Empty

  override def getCounterpartyByIban(counterpartyId : String): Box[CounterpartyTrait] = Empty

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
                                   bespoke: List[CounterpartyBespoke]
                                 ): Box[CounterpartyTrait] = Empty

  override def checkCounterpartyAvailable(
                                        name: String,
                                        thisBankId: String,
                                        thisAccountId: String,
                                        thisViewId: String
                                      ): Boolean = false

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]] = ???

  override def addPublicAlias(counterpartyId : String, alias: String): Box[Boolean] = ???
  override def addPrivateAlias(counterpartyId : String, alias: String): Box[Boolean] = ???
  override def addURL(counterpartyId : String, url: String): Box[Boolean] = ???
  override def addImageURL(counterpartyId : String, imageUrl: String): Box[Boolean] = ???
  override def addOpenCorporatesURL(counterpartyId : String, url: String): Box[Boolean] = ???
  override def addMoreInfo(counterpartyId : String, moreInfo: String): Box[Boolean] = ???
  override def addPhysicalLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] = ???
  override def addCorporateLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] = ???
  override def deletePhysicalLocation(counterpartyId : String): Box[Boolean] = ???
  override def deleteCorporateLocation(counterpartyId : String): Box[Boolean] = ???
  override def getCorporateLocation(counterpartyId : String): Box[GeoTag] = ???
  override def getPhysicalLocation(counterpartyId : String): Box[GeoTag] = ???
  override def getOpenCorporatesURL(counterpartyId : String): Box[String] = ???
  override def getImageURL(counterpartyId : String): Box[String] = ???
  override def getUrl(counterpartyId : String): Box[String] = ???
  override def getMoreInfo(counterpartyId : String): Box[String] = ???
  override def getPublicAlias(counterpartyId : String): Box[String] = ???
  override def getPrivateAlias(counterpartyId : String): Box[String] = ???
  override def bulkDeleteAllCounterparties(): Box[Boolean] = ???
}
