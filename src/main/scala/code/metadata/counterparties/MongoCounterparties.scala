package code.metadata.counterparties

import code.model.{AccountId, BankId, OtherBankAccountMetadata, OtherBankAccount}
import net.liftweb.common.Loggable
import com.mongodb.QueryBuilder

private object MongoCounterparties extends Counterparties with Loggable {
  import code.model.GeoTag

  def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherParty : OtherBankAccount) : OtherBankAccountMetadata = {
    import net.liftweb.util.Helpers.tryo
    import net.liftweb.common.Full
    import org.bson.types.ObjectId

    /**
     * This particular implementation requires the metadata id to be the same as the otherParty (OtherBankAccount) id
     */

    val existing = for {
      objId <- tryo { new ObjectId(otherParty.id) }
      query = QueryBuilder.start("originalPartyBankId").is(originalPartyBankId.value).put("originalPartyAccountId").is(originalPartyAccountId.value).
        put("_id").is(objId).get()
      m <- Metadata.find(query)
    } yield m

    val metadata = existing match {
      case Full(m) => m
      case _ => createMetadata(originalPartyBankId, originalPartyAccountId, otherParty.label)
    }

    createOtherBankAccountMetadata(metadata)
  }

  /**
   * This only exists for OBPEnvelope. Avoid using it for any other reason outside of this class
   */
  def createMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherAccountHolder : String) : Metadata = {
    //create it
    if(otherAccountHolder.isEmpty){
      logger.info("other account holder is Empty. creating a metadata record with no public alias")
      //no holder name, nothing to hide, so we don't need to create a public alias
      //otherwise several transactions where the holder is empty (like here)
      //would automatically share the metadata and then the alias
      Metadata
        .createRecord
        .originalPartyBankId(originalPartyBankId.value)
        .originalPartyAccountId(originalPartyAccountId.value)
        .holder("")
        .save

    } else {
      Metadata.createRecord.
        originalPartyBankId(originalPartyBankId.value).
        originalPartyAccountId(originalPartyAccountId.value).
        holder(otherAccountHolder).
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



  private def createOtherBankAccountMetadata(otherAccountMetadata : Metadata): OtherBankAccountMetadata = {
    new OtherBankAccountMetadata(
      publicAlias = otherAccountMetadata.publicAlias.get,
      privateAlias = otherAccountMetadata.privateAlias.get,
      moreInfo = otherAccountMetadata.moreInfo.get,
      url = otherAccountMetadata.url.get,
      imageURL = otherAccountMetadata.imageUrl.get,
      openCorporatesURL = otherAccountMetadata.openCorporatesUrl.get,
      corporateLocation = locationTag(otherAccountMetadata.corporateLocation.get),
      physicalLocation = locationTag(otherAccountMetadata.physicalLocation.get),
      addMoreInfo = (text => {
        otherAccountMetadata.moreInfo(text).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      addURL = (text => {
        otherAccountMetadata.url(text).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      addImageURL = (text => {
        otherAccountMetadata.imageUrl(text).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      addOpenCorporatesURL = (text => {
        otherAccountMetadata.openCorporatesUrl(text).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      addCorporateLocation = otherAccountMetadata.addCorporateLocation,
      addPhysicalLocation = otherAccountMetadata.addPhysicalLocation,
      addPublicAlias = (alias => {
        otherAccountMetadata.publicAlias(alias).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      addPrivateAlias = (alias => {
        otherAccountMetadata.privateAlias(alias).save
        //the save method does not return a Boolean to inform about the saving state,
        //so we a true
        true
      }),
      deleteCorporateLocation = otherAccountMetadata.deleteCorporateLocation _,
      deletePhysicalLocation = otherAccountMetadata.deletePhysicalLocation _
    )
  }

  private def locationTag(loc: OBPGeoTag): Option[GeoTag]={
    if(loc.longitude==0 && loc.latitude==0 && loc.userId.get.isEmpty)
      None
    else
      Some(loc)
  }
}