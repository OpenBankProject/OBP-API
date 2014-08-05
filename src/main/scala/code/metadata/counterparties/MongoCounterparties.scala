package code.metadata.counterparties

import code.model.{User, GeoTag, OtherBankAccountMetadata, OtherBankAccount}
import net.liftweb.mongodb.record.{BsonMetaRecord, BsonRecord, MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{DateField, BsonRecordField, ObjectIdPk}
import net.liftweb.record.field.{DoubleField, LongField, StringField}
import java.util.Date
import com.mongodb.QueryBuilder
import net.liftweb.common.{Loggable, Full}
import scala.util.Random
import org.bson.types.ObjectId
import net.liftweb.util.Helpers._

object MongoCounterparties extends Counterparties with Loggable {

  def getOrCreateMetadata(originalPartyBankId: String, originalPartyAccountId : String, otherParty : OtherBankAccount) : OtherBankAccountMetadata = {

    /**
     * This particular implementation requires the metadata id to be the same as the otherParty (OtherBankAccount) id
     */

    val existing = for {
      objId <- tryo { new ObjectId(otherParty.id) }
      query = QueryBuilder.start("originalPartyBankId").is(originalPartyBankId).put("originalPartyAccountId").is(originalPartyAccountId).
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
  def createMetadata(originalPartyBankId: String, originalPartyAccountId : String, otherAccountHolder : String) : Metadata = {
    //create it
    if(otherAccountHolder.isEmpty){
      logger.info("other account holder is Empty. creating a metadata record with no public alias")
      //no holder name, nothing to hide, so we don't need to create a public alias
      //otherwise several transactions where the holder is empty (like here)
      //would automatically share the metadata and then the alias
      Metadata
        .createRecord
        .originalPartyBankId(originalPartyBankId)
        .originalPartyAccountId(originalPartyAccountId)
        .holder("")
        .save

    } else {
      Metadata.createRecord.
        originalPartyBankId(originalPartyBankId).
        originalPartyAccountId(originalPartyAccountId).
        holder(otherAccountHolder).
        publicAlias(newPublicAliasName(originalPartyBankId, originalPartyAccountId)).save
    }
  }

  /**
   * Generates a new alias name that is guaranteed not to collide with any existing public alias names
   * for the account in question
   */
  def newPublicAliasName(originalPartyBankId : String, originalPartyAccountId : String): String = {
    val firstAliasAttempt = "ALIAS_" + Random.nextLong().toString.take(6)

    /**
     * Returns true if @publicAlias is already the name of a public alias within @account
     */
    def isDuplicate(publicAlias: String) = {
      val query = QueryBuilder.start("originalPartyBankId").is(originalPartyBankId).put("originalPartyAccountId").is(originalPartyAccountId).get()
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


class OBPGeoTag private() extends BsonRecord[OBPGeoTag] with GeoTag {
  def meta = OBPGeoTag

  //These fields are used to link this to its transaction
  object transactionId extends StringField(this, 255)
  object accountId extends StringField(this, 255)
  object bankId extends StringField(this, 255)

  object userId extends StringField(this,255)
  object viewID extends LongField(this)
  object date extends DateField(this)

  object geoLongitude extends DoubleField(this,0)
  object geoLatitude extends DoubleField(this,0)

  def datePosted = date.get
  def postedBy = User.findByApiId(userId.get)
  def viewId = viewID.get
  def longitude = geoLongitude.get
  def latitude = geoLatitude.get

}
object OBPGeoTag extends OBPGeoTag with BsonMetaRecord[OBPGeoTag]
