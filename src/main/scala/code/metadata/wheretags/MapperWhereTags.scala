package code.metadata.wheretags

import java.util.Date

import code.model._
import code.model.dataAccess.APIUser
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
                          (userId: UserId, viewId: ViewId, datePosted: Date, longitude: Double, latitude: Double): Boolean = {

    val found = findMappedWhereTag(bankId, accountId, transactionId, viewId)

    val toUpdate = found.getOrElse {
      MappedWhereTag.create
        .bank(bankId.value)
        .account(accountId.value)
        .transaction(transactionId.value)
        .view(viewId.value)
    }

    toUpdate
      .user(userId.value)
      .date(datePosted)
      .geoLatitude(latitude)
      .geoLongitude(longitude)


    tryo{toUpdate.saveMe}.isDefined
  }

  override def deleteWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): Boolean = {
    val found = findMappedWhereTag(bankId, accountId, transactionId, viewId)

    found.map(_.delete_!).getOrElse(false)
  }

  override def getWhereTagForTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): Option[GeoTag] = {
    findMappedWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId, viewId: ViewId)
  }
}

class MappedWhereTag extends GeoTag with LongKeyedMapper[MappedWhereTag] with IdPK with CreatedUpdated {

  def getSingleton = MappedWhereTag

  object bank extends MappedString(this, 255)
  object account extends MappedString(this, 255)
  object transaction extends MappedString(this, 255)
  object view extends MappedString(this, 255)

  object user extends MappedLongForeignKey(this, APIUser)
  object date extends MappedDate(this)

  //TODO: require these to be valid latitude/longitudes
  object geoLatitude extends MappedDouble(this)
  object geoLongitude extends MappedDouble(this)

  override def datePosted: Date = date.get
  override def postedBy: Box[User] = user.obj
  override def viewId: ViewId = ViewId(view.get)
  override def latitude: Double = geoLatitude.get
  override def longitude: Double = geoLongitude.get
}

object MappedWhereTag extends MappedWhereTag with LongKeyedMetaMapper[MappedWhereTag] {
  override def dbIndexes = Index(bank, account, transaction, view) :: super.dbIndexes
}
