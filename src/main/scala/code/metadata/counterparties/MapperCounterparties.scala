package code.metadata.counterparties

import java.util.Date

import code.model._
import code.model.dataAccess.APIUser
import code.util.MappedUUID
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MapperCounterparties extends Counterparties {
  override def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: OtherBankAccount): OtherBankAccountMetadata = {
    val existing = findMappedCounterpartyMetadata(originalPartyBankId, originalPartyAccountId, otherParty)

    existing match {
      case Full(e) => e
      case _ => MappedCounterpartyMetadata.create
        .thisAccountBankId(originalPartyBankId.value)
        .thisAccountId(originalPartyAccountId.value)
        .holder(otherParty.label)
        .accountNumber(otherParty.number).saveMe
    }
  }


  def findMappedCounterpartyMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId,
                                     otherParty: OtherBankAccount) : Box[MappedCounterpartyMetadata] = {
    MappedCounterpartyMetadata.find(
      By(MappedCounterpartyMetadata.thisAccountBankId, originalPartyBankId.value),
      By(MappedCounterpartyMetadata.thisAccountId, originalPartyAccountId.value),
      By(MappedCounterpartyMetadata.holder, otherParty.label),
      By(MappedCounterpartyMetadata.accountNumber, otherParty.number))
  }
}

class MappedCounterpartyMetadata extends OtherBankAccountMetadata with LongKeyedMapper[MappedCounterpartyMetadata] with IdPK with CreatedUpdated {
  override def getSingleton = MappedCounterpartyMetadata

  object counterpartyId extends MappedUUID(this)

  //these define the obp account to which this counterparty belongs
  object thisAccountBankId extends MappedString(this, 255)
  object thisAccountId extends MappedString(this, 255)

  //these define the counterparty
  object holder extends MappedString(this, 255)
  object accountNumber extends MappedString(this, 100)

  //this is the counterparty's metadata
  object publicAlias extends MappedString(this, 100)
  object privateAlias extends MappedString(this, 100)
  object moreInfo extends MappedString(this, 100)
  object url extends MappedString(this, 100)
  object imageUrl extends MappedString(this, 100)
  object openCorporatesUrl extends MappedString(this, 100)

  object physicalLocation extends MappedLongForeignKey(this, MappedCounterpartyWhereTag)
  object corporateLocation extends MappedLongForeignKey(this, MappedCounterpartyWhereTag)

  /**
   * Evaluates f, and then attempts to save. If no exceptions are thrown and save executes successfully,
   * true is returned. If an exception is thrown or if the save fails, false is returned.
   * @param f the expression to evaluate (e.g. imageUrl("http://example.com/foo.png")
   * @return If saving the model worked after having evaluated f
   */
  private def trySave(f : => Any) : Boolean =
    tryo{
      f
      save()
    }.getOrElse(false)

  private def setWhere(whereTag : Box[MappedCounterpartyWhereTag])
                      (userId: UserId, datePosted : Date, longitude : Double, latitude : Double) : Box[MappedCounterpartyWhereTag] = {
    val toUpdate = whereTag match {
      case Full(c) => c
      case _ => MappedCounterpartyWhereTag.create
    }

    tryo{
      toUpdate
        .user(userId.value)
        .date(datePosted)
        .geoLongitude(longitude)
        .geoLatitude(latitude)
        .saveMe
    }
  }

  def setCorporateLocation(userId: UserId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    //save where tag
    val savedWhere = setWhere(corporateLocation.obj)(userId, datePosted, longitude, latitude)
    //set where tag for counterparty
    savedWhere.map(location => trySave{corporateLocation(location)}).getOrElse(false)
  }

  def setPhysicalLocation(userId: UserId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    //save where tag
    val savedWhere = setWhere(physicalLocation.obj)(userId, datePosted, longitude, latitude)
    //set where tag for counterparty
    savedWhere.map(location => trySave{physicalLocation(location)}).getOrElse(false)
  }

  override def getPublicAlias: String = publicAlias.get
  override def getCorporateLocation: Option[GeoTag] =
    corporateLocation.obj
  override def getOpenCorporatesURL: String = openCorporatesUrl.get
  override def getMoreInfo: String = moreInfo.get
  override def getPrivateAlias: String = privateAlias.get
  override def getImageURL: String = imageUrl.get
  override def getPhysicalLocation: Option[GeoTag] =
    physicalLocation.obj
  override def getUrl: String = url.get

  override val addPhysicalLocation: (UserId, Date, Double, Double) => Boolean = setPhysicalLocation _
  override val addCorporateLocation: (UserId, Date, Double, Double) => Boolean = setCorporateLocation _
  override val addPrivateAlias: (String) => Boolean = (x) =>
    trySave{privateAlias(x)}
  override val addURL: (String) => Boolean = (x) =>
    trySave{url(x)}
  override val addMoreInfo: (String) => Boolean = (x) =>
    trySave{moreInfo(x)}
  override val addPublicAlias: (String) => Boolean = (x) =>
    trySave{publicAlias(x)}
  override val addOpenCorporatesURL: (String) => Boolean = (x) =>
    trySave{openCorporatesUrl(x)}
  override val addImageURL: (String) => Boolean = (x) =>
    trySave{imageUrl(x)}
  override val deleteCorporateLocation = () =>
    corporateLocation.obj.map(_.delete_!).getOrElse(false)
  override val deletePhysicalLocation = () =>
    physicalLocation.obj.map(_.delete_!).getOrElse(false)

}

object MappedCounterpartyMetadata extends MappedCounterpartyMetadata with LongKeyedMetaMapper[MappedCounterpartyMetadata] {
  override def dbIndexes =
    UniqueIndex(counterpartyId) ::
    Index(thisAccountBankId, thisAccountId, holder, accountNumber) ::
    super.dbIndexes
}

class MappedCounterpartyWhereTag extends GeoTag with LongKeyedMapper[MappedCounterpartyWhereTag] with IdPK with CreatedUpdated {

  def getSingleton = MappedCounterpartyWhereTag

  object user extends MappedLongForeignKey(this, APIUser)
  object date extends MappedDate(this)

  //TODO: require these to be valid latitude/longitudes
  object geoLatitude extends MappedDouble(this)
  object geoLongitude extends MappedDouble(this)

  override def postedBy: Box[User] = user.obj
  override def datePosted: Date = date.get
  override def latitude: Double = geoLatitude.get
  override def longitude: Double = geoLongitude.get
}

object MappedCounterpartyWhereTag extends MappedCounterpartyWhereTag with LongKeyedMetaMapper[MappedCounterpartyWhereTag]