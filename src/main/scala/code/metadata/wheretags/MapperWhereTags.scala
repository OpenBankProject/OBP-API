package code.metadata.wheretags

import java.util.Date

import code.model._
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.util.{AccountIdString, UUIDString}
import code.views.Views
import net.liftweb.util.Helpers.tryo
import net.liftweb.common.Box
import net.liftweb.mapper._

object MapperWhereTags extends WhereTags {

  private def findMappedWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId, viewId : ViewId) = {
    MappedWhereTag.find(
      By(MappedWhereTag.bank, bankId.value),
      By(MappedWhereTag.account, accountId.value),
      By(MappedWhereTag.transaction, transactionId.value),
      By(MappedWhereTag.view, viewId.value))
  }

  override def addWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
                          (userId: UserPrimaryKey, viewId: ViewId, datePosted: Date, longitude: Double, latitude: Double): Boolean = {

    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    val found = findMappedWhereTag(bankId, accountId, transactionId, ViewId(metadateViewId))

    val toUpdate = found.getOrElse {
      MappedWhereTag.create
        .bank(bankId.value)
        .account(accountId.value)
        .transaction(transactionId.value)
        .view(metadateViewId)
    }

    toUpdate
      .user(userId.value)
      .date(datePosted)
      .geoLatitude(latitude)
      .geoLongitude(longitude)


    tryo{toUpdate.saveMe}.isDefined
  }

  override def deleteWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): Boolean = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    val found = findMappedWhereTag(bankId, accountId, transactionId, ViewId(metadateViewId))

    found.map(_.delete_!).getOrElse(false)
  }

  override def getWhereTagForTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): Box[GeoTag] = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    findMappedWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId, ViewId(metadateViewId))
  }

  override def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId): Boolean = {
    val whereTagsDeleted = MappedWhereTag.bulkDelete_!!(
      By(MappedWhereTag.bank, bankId.value),
      By(MappedWhereTag.account, accountId.value)
    )
    whereTagsDeleted
  }
}

class MappedWhereTag extends GeoTag with LongKeyedMapper[MappedWhereTag] with IdPK with CreatedUpdated {

  def getSingleton = MappedWhereTag

  object bank extends UUIDString(this)
  object account extends AccountIdString(this)
  object transaction extends UUIDString(this)
  object view extends UUIDString(this)

  object user extends MappedLongForeignKey(this, ResourceUser)
  object date extends MappedDateTime(this)

  //TODO: require these to be valid latitude/longitudes
  object geoLatitude extends MappedDouble(this)
  object geoLongitude extends MappedDouble(this)

  override def datePosted: Date = date.get
  override def postedBy: Box[User] = Users.users.vend.getUserByResourceUserId(user.get)
  override def latitude: Double = geoLatitude.get
  override def longitude: Double = geoLongitude.get
}

object MappedWhereTag extends MappedWhereTag with LongKeyedMetaMapper[MappedWhereTag] {
  override def dbIndexes = Index(bank, account, transaction, view) :: super.dbIndexes
}
