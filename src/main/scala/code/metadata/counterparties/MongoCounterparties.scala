package code.metadata.counterparties

import code.model.{GeoTag, OtherBankAccountMetadata, OtherBankAccount}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{BsonRecordField, ObjectIdPk}
import net.liftweb.record.field.StringField
import code.model.dataAccess.{Account, OBPGeoTag}
import java.util.Date
import com.mongodb.QueryBuilder
import net.liftweb.common.Full
import scala.util.Random

object MongoCounterparties extends Counterparties {

  def metadataQuery(originalPartyBankId: String, originalPartyAccountId: String) =
    QueryBuilder.start("originalPartyBankId").is(originalPartyBankId).put("originalPartyAccountId").is(originalPartyAccountId).get()

  def getOrCreateMetadata(originalPartyBankId: String, originalPartyAccountId : String, otherParty : OtherBankAccount) : OtherBankAccountMetadata = {
    val metadata : Metadata = Metadata.find(metadataQuery(originalPartyBankId, originalPartyAccountId)) match {
      case Full(m) => m
      case _ => {
        //create it
        Metadata.createRecord.
          originalPartyBankId(originalPartyBankId).
          originalPartyAccountId(originalPartyAccountId).
          holder(otherParty.label).
          publicAlias(newPublicAliasName(originalPartyBankId, originalPartyAccountId)).save
      }
    }

    createOtherBankAccountMetadata(metadata)
  }
  /**
   * Generates a new alias name that is guaranteed not to collide with any existing public alias names
   * for the account in question
   */
  private def newPublicAliasName(originalPartyBankId : String, originalPartyAccountId : String): String = {
    val firstAliasAttempt = "ALIAS_" + Random.nextLong().toString.take(6)

    /**
     * Returns true if @publicAlias is already the name of a public alias within @account
     */
    def isDuplicate(publicAlias: String) = {
      Metadata.find(metadataQuery(originalPartyBankId, originalPartyAccountId)).isDefined
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

class Metadata private() extends MongoRecord[Metadata] with ObjectIdPk[Metadata] {
  def meta = Metadata

  //originalPartyBankId and originalPartyAccountId are used to identify the account
  //which has the counterparty this metadata is associated with
  object originalPartyBankId extends StringField(this, 100)
  object originalPartyAccountId extends StringField(this, 100)

  object holder extends StringField(this, 255)
  object publicAlias extends StringField(this, 100)
  object privateAlias extends StringField(this, 100)
  object moreInfo extends StringField(this, 100)
  object url extends StringField(this, 100)
  object imageUrl extends StringField(this, 100)
  object openCorporatesUrl extends StringField(this, 100) {
    override def optional_? = true
  }
  object corporateLocation extends BsonRecordField(this, OBPGeoTag)
  object physicalLocation extends BsonRecordField(this, OBPGeoTag)

  def addCorporateLocation(userId: String, viewId : Long, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    val newTag = OBPGeoTag.createRecord.
      userId(userId).
      viewID(viewId).
      date(datePosted).
      geoLongitude(longitude).
      geoLatitude(latitude)
    corporateLocation(newTag).save
    true
  }

  def deleteCorporateLocation : Boolean = {
    corporateLocation.clear
    this.save
    true
  }

  def addPhysicalLocation(userId: String, viewId : Long, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    val newTag = OBPGeoTag.createRecord.
      userId(userId).
      viewID(viewId).
      date(datePosted).
      geoLongitude(longitude).
      geoLatitude(latitude)
    physicalLocation(newTag).save
    true
  }

  def deletePhysicalLocation : Boolean = {
    physicalLocation.clear
    this.save
    true
  }

}

object Metadata extends Metadata with MongoMetaRecord[Metadata]
