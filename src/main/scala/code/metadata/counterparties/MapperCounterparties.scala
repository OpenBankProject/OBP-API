package code.metadata.counterparties

import java.util.Date

import code.model._
import code.model.dataAccess.APIUser
import code.util.{DefaultStringField, MappedUUID}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MapperCounterparties extends Counterparties {
  override def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: OtherBankAccount): OtherBankAccountMetadata = {

    /**
     * Generates a new alias name that is guaranteed not to collide with any existing public alias names
     * for the account in question
     */
    def newPublicAliasName(): String = {
      import scala.util.Random
      val firstAliasAttempt = "ALIAS_" + Random.nextLong().toString.take(6)

      /**
       * Returns true if @publicAlias is already the name of a public alias within @account
       */
      def isDuplicate(publicAlias: String) : Boolean = {
        MappedCounterpartyMetadata.find(
          By(MappedCounterpartyMetadata.thisAccountBankId, originalPartyBankId.value),
          By(MappedCounterpartyMetadata.thisAccountId, originalPartyAccountId.value),
          By(MappedCounterpartyMetadata.publicAlias, publicAlias)
        ).isDefined
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


    //can't find by MappedCounterpartyMetadata.counterpartyId = otherParty.id because in this implementation
    //if the metadata doesn't exist, the id field of ther OtherBankAccount is not known yet, and will be empty
    def findMappedCounterpartyMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId,
                                       otherParty: OtherBankAccount) : Box[MappedCounterpartyMetadata] = {
      MappedCounterpartyMetadata.find(
        By(MappedCounterpartyMetadata.thisAccountBankId, originalPartyBankId.value),
        By(MappedCounterpartyMetadata.thisAccountId, originalPartyAccountId.value),
        By(MappedCounterpartyMetadata.holder, otherParty.label),
        By(MappedCounterpartyMetadata.accountNumber, otherParty.number))
    }

    val existing = findMappedCounterpartyMetadata(originalPartyBankId, originalPartyAccountId, otherParty)

    existing match {
      case Full(e) => e
      case _ => MappedCounterpartyMetadata.create
        .thisAccountBankId(originalPartyBankId.value)
        .thisAccountId(originalPartyAccountId.value)
        .holder(otherParty.label)
        .publicAlias(newPublicAliasName())
        .accountNumber(otherParty.number).saveMe
    }
  }

  //get all counterparty metadatas for a single OBP account
  override def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId): List[OtherBankAccountMetadata] = {
    MappedCounterpartyMetadata.findAll(
      By(MappedCounterpartyMetadata.thisAccountBankId, originalPartyBankId.value),
      By(MappedCounterpartyMetadata.thisAccountId, originalPartyAccountId.value)
    )
  }

  override def getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[OtherBankAccountMetadata] = {
    /**
     * This particular implementation requires the metadata id to be the same as the otherParty (OtherBankAccount) id
     */
    MappedCounterpartyMetadata.find(
      By(MappedCounterpartyMetadata.thisAccountBankId, originalPartyBankId.value),
      By(MappedCounterpartyMetadata.thisAccountId, originalPartyAccountId.value),
      By(MappedCounterpartyMetadata.counterpartyId, counterpartyMetadataId)
    )
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
  object publicAlias extends DefaultStringField(this)
  object privateAlias extends DefaultStringField(this)
  object moreInfo extends DefaultStringField(this)
  object url extends DefaultStringField(this)
  object imageUrl extends DefaultStringField(this)
  object openCorporatesUrl extends DefaultStringField(this)

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

  override def metadataId: String = counterpartyId.get
  override def getAccountNumber: String = accountNumber.get
  override def getHolder: String = holder.get
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
  object date extends MappedDateTime(this)

  //TODO: require these to be valid latitude/longitudes
  object geoLatitude extends MappedDouble(this)
  object geoLongitude extends MappedDouble(this)

  override def postedBy: Box[User] = user.obj
  override def datePosted: Date = date.get
  override def latitude: Double = geoLatitude.get
  override def longitude: Double = geoLongitude.get
}

object MappedCounterpartyWhereTag extends MappedCounterpartyWhereTag with LongKeyedMetaMapper[MappedCounterpartyWhereTag]