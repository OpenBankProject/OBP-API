package code.metadata.transactionimages

import java.net.URL
import java.util.Date
import code.model._
import code.model.dataAccess.ResourceUser
import code.util.{DefaultStringField, MappedUUID}
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MapperTransactionImages extends TransactionImages {
  override def getImagesForTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): List[TransactionImage] = {
    MappedTransactionImage.findAll(
      By(MappedTransactionImage.bank, bankId.value),
      By(MappedTransactionImage.account, accountId.value),
      By(MappedTransactionImage.transaction, transactionId.value),
      By(MappedTransactionImage.view, viewId.value)
    )
  }

  override def deleteTransactionImage(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(imageId: String): Box[Unit] = {
    //imageId is unique, so we don't need bankId, accountId, and transactionId
    //TODO: this should return something more useful than Box[Unit]
    MappedTransactionImage.find(By(MappedTransactionImage.imageId, imageId)).map(_.delete_!).map(x => ())

  }

  override def addTransactionImage(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
                                  (userId: UserId, viewId: ViewId, description: String, datePosted: Date, imageURL: URL): Box[TransactionImage] = {
    tryo {
      MappedTransactionImage.create
        .bank(bankId.value)
        .account(accountId.value)
        .transaction(transactionId.value)
        .view(viewId.value)
        .user(userId.value)
        .imageDescription(description)
        .url(imageURL.toString)
        .date(datePosted).saveMe
    }
  }
}

class MappedTransactionImage extends TransactionImage with LongKeyedMapper[MappedTransactionImage] with IdPK with CreatedUpdated {
  def getSingleton = MappedTransactionImage

  object bank extends MappedString(this, 255)
  object account extends MappedString(this, 255)
  object transaction extends MappedString(this, 255)
  object view extends MappedString(this, 255)

  object imageId extends MappedUUID(this)
  object user extends MappedLongForeignKey(this, ResourceUser)
  object date extends MappedDateTime(this)

  object url extends DefaultStringField(this)
  object imageDescription extends DefaultStringField(this)

  override def id_ : String = imageId.get
  override def postedBy: Box[User] = code.model.User.findByResourceUserId(user.get)
  override def description: String = imageDescription.get
  override def imageUrl: URL = tryo {new URL(url.get)} getOrElse MappedTransactionImage.notFoundUrl
  override def viewId: ViewId = ViewId(view.get)
  override def datePosted: Date = date.get
}

object MappedTransactionImage extends MappedTransactionImage with LongKeyedMetaMapper[MappedTransactionImage] {
  override def dbIndexes = Index(bank, account, transaction, view) :: UniqueIndex(imageId) :: super.dbIndexes


  val notFoundUrl = new URL("http://example.com/notfound.png") //TODO: Make this image exist?
}