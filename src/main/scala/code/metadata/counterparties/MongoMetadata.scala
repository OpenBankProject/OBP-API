package code.metadata.counterparties


import code.util.Helper
import net.liftweb.mongodb.record.{BsonMetaRecord, BsonRecord, MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.ObjectIdPk
import net.liftweb.record.field.StringField
import code.model.{CounterpartyMetadata, UserPrimaryKey, ViewId, GeoTag}
//TODO: this should be private
class Metadata private() extends CounterpartyMetadata with MongoRecord[Metadata] with ObjectIdPk[Metadata] {
  import net.liftweb.mongodb.record.field.BsonRecordField
  import java.util.Date

  def meta = Metadata

  //originalPartyBankId and originalPartyAccountId are used to identify the account
  //which has the counterparty this metadata is associated with
  object originalPartyBankId extends StringField(this, 100)
  object originalPartyAccountId extends StringField(this, 100)
  object counterpartyId extends StringField(this,100)
  
  object holder extends StringField(this, 255)
//  object accountNumber extends  StringField(this, 100)
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

  def addCorporateLocationFn(userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    val newTag = OBPGeoTag.createRecord.
      userId(userId.value).
      date(datePosted).
      geoLongitude(longitude).
      geoLatitude(latitude)
    corporateLocation(newTag).saveTheRecord()
    true
  }

  def deleteCorporateLocationFn : Boolean = {
    corporateLocation.clear
    this.saveTheRecord()
    true
  }

  def addPhysicalLocationFn(userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    val newTag = OBPGeoTag.createRecord.
      userId(userId.value).
      date(datePosted).
      geoLongitude(longitude).
      geoLatitude(latitude)
    physicalLocation(newTag).saveTheRecord()
    true
  }

  def deletePhysicalLocationFn : Boolean = {
    physicalLocation.clear
    this.saveTheRecord()
    true
  }

  private def locationTag(loc: OBPGeoTag): Option[GeoTag]={
    if(loc.longitude==0 && loc.latitude==0)
      None
    else
      Some(loc)
  }

  override def getCounterpartyId = counterpartyId.get
  override def getCounterpartyName = holder.get
//  override def getAccountNumber = accountNumber.get
  override def getUrl = url.get
  override def getCorporateLocation = locationTag(corporateLocation.get)
  override def getPhysicalLocation = locationTag(physicalLocation.get)
  override val deleteCorporateLocation = deleteCorporateLocationFn _
  override val deletePhysicalLocation = deletePhysicalLocationFn _
  override def getPrivateAlias = privateAlias.get
  override def getPublicAlias = publicAlias.get
  override def getMoreInfo = moreInfo.get
  override def getImageURL: String = imageUrl.get
  override val addPrivateAlias: (String) => Boolean = (alias => {
    privateAlias(alias).saveTheRecord()
    //the save method does not return a Boolean to inform about the saving state,
    //so we a true
    true
  })
  override val addURL: (String) => Boolean = (text => {
    url(text).saveTheRecord()
    //the save method does not return a Boolean to inform about the saving state,
    //so we a true
    true
  })
  override val addPhysicalLocation: (UserPrimaryKey, Date, Double, Double) => Boolean = addPhysicalLocationFn _
  override val addCorporateLocation: (UserPrimaryKey, Date, Double, Double) => Boolean = addCorporateLocationFn _
  override val addMoreInfo: (String) => Boolean = (text => {
    moreInfo(text).saveTheRecord()
    //the save method does not return a Boolean to inform about the saving state,
    //so we a true
    true
  })
  override def getOpenCorporatesURL: String = openCorporatesUrl.get
  override val addPublicAlias: (String) => Boolean = (alias => {
    publicAlias(alias).saveTheRecord()
    //the save method does not return a Boolean to inform about the saving state,
    //so we a true
    true
  })
  override val addOpenCorporatesURL: (String) => Boolean = (text => {
    openCorporatesUrl(text).saveTheRecord()
    //the save method does not return a Boolean to inform about the saving state,
    //so we a true
    true
  })
  override val addImageURL: (String) => Boolean = (text => {
    imageUrl(text).saveTheRecord()
    //the save method does not return a Boolean to inform about the saving state,
    //so we a true
    true
  })
}

//TODO: this should be private
object Metadata extends Metadata with MongoMetaRecord[Metadata]

class OBPGeoTag private() extends BsonRecord[OBPGeoTag] with GeoTag {
  import code.model.User
  import net.liftweb.record.field.{DoubleField, LongField}
  import net.liftweb.mongodb.record.field.DateField

  def meta = OBPGeoTag

  //These fields are used to link this to its transaction
  object transactionId extends StringField(this, 255)
  object accountId extends StringField(this, 255)
  object bankId extends StringField(this, 255)

  object userId extends LongField(this)

  object date extends DateField(this)

  object geoLongitude extends DoubleField(this,0)
  object geoLatitude extends DoubleField(this,0)

  override def datePosted = date.get
  override def postedBy = User.findByResourceUserId(userId.get)
  override def longitude = geoLongitude.get
  override def latitude = geoLatitude.get

}
//TODO: this should be private
object OBPGeoTag extends OBPGeoTag with BsonMetaRecord[OBPGeoTag]