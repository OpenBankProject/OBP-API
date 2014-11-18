package code.metadata.counterparties

import java.util.Date

import code.model._
import code.model.dataAccess.APIUser
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MapperCounterparties extends Counterparties {
  override def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: OtherBankAccount): OtherBankAccountMetadata = {
    val existing = findMappedCounterpartyMetadata(originalPartyBankId, originalPartyAccountId, otherParty)

    existing match {
      case Full(e) => e.toOtherBankAccountMetadata
      case _ => MappedCounterpartyMetadata.create
        .thisAccountBankId(originalPartyBankId.value)
        .thisAccountId(originalPartyAccountId.value)
        .holder(otherParty.label)
        .accountNumber(otherParty.number).saveMe.toOtherBankAccountMetadata
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

class MappedCounterpartyMetadata extends LongKeyedMapper[MappedCounterpartyMetadata] with IdPK with CreatedUpdated {
  override def getSingleton = MappedCounterpartyMetadata

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

  private def setWhere(whereTag : Box[MappedCounterpartyWhereTag])
                      (userId: UserId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    val toUpdate = whereTag match {
      case Full(c) => c
      case _ => MappedCounterpartyWhereTag.create
    }

    tryo{
      toUpdate
        .postedBy(userId.value)
        .date(datePosted)
        .geoLongitude(longitude)
        .geoLatitude(latitude)
        .save()
    }.getOrElse(false)
  }

  def setCorporateLocation(userId: UserId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    setWhere(corporateLocation.obj)(userId, datePosted, longitude, latitude)
  }

  def deleteCorporateLocation : Boolean = {
    corporateLocation.obj.map(_.delete_!).getOrElse(false)
  }

  def setPhysicalLocation(userId: UserId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    setWhere(physicalLocation.obj)(userId, datePosted, longitude, latitude)
  }

  def deletePhysicalLocation : Boolean = {
    physicalLocation.obj.map(_.delete_!).getOrElse(false)
  }

  //TODO: make OtherBankAccountMetadata a trait?
  def toOtherBankAccountMetadata : OtherBankAccountMetadata = ??? //TODO
}

object MappedCounterpartyMetadata extends MappedCounterpartyMetadata with LongKeyedMetaMapper[MappedCounterpartyMetadata] {
  override def dbIndexes =
    Index(thisAccountBankId, thisAccountId, holder, accountNumber) ::
    super.dbIndexes
}

class MappedCounterpartyWhereTag extends LongKeyedMapper[MappedCounterpartyWhereTag] with IdPK with CreatedUpdated {

  def getSingleton = MappedCounterpartyWhereTag

  object postedBy extends MappedLongForeignKey(this, APIUser)
  object date extends MappedDate(this)

  //TODO: require these to be valid latitude/longitudes
  object geoLatitude extends MappedDouble(this)
  object geoLongitude extends MappedDouble(this)

}

object MappedCounterpartyWhereTag extends MappedCounterpartyWhereTag with LongKeyedMetaMapper[MappedCounterpartyWhereTag]