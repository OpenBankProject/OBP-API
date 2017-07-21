package code.socialmedia

import java.util.Date
import code.model.dataAccess.ResourceUser
import code.util.{UUIDString}
import net.liftweb.mapper._

object MappedSocialMediasProvider extends SocialMediaHandleProvider {

  override def getSocialMedias(customerNumber: String): List[MappedSocialMedia] = {
    MappedSocialMedia.findAll(
      By(MappedSocialMedia.mCustomerNumber, customerNumber),
      OrderBy(MappedSocialMedia.updatedAt, Descending))
  }


  override def addSocialMedias(customerNumber: String, `type`: String, handle: String, dateAdded: Date, dateActivated: Date): Boolean = {
    MappedSocialMedia.create
      .mCustomerNumber(customerNumber)
      .mType(`type`)
      .mHandle(handle)
      .mDateAdded(dateAdded)
      .mDateActivated(dateActivated)
      .save()
  }
}

class MappedSocialMedia extends SocialMedia
with LongKeyedMapper[MappedSocialMedia] with IdPK with CreatedUpdated {

  def getSingleton = MappedSocialMedia

  object user extends MappedLongForeignKey(this, ResourceUser)
  object bank extends UUIDString(this)

  object mCustomerNumber extends MappedString(this, 64)
  object mType extends MappedString(this, 16)
  object mHandle extends MappedString(this, 64)
  object mDateAdded extends MappedDateTime(this)
  object mDateActivated extends MappedDateTime(this)


  override def customerNumber: String = mCustomerNumber.get
  override def `type`: String = mType.get
  override def handle: String = mHandle.get
  override def dateAdded: Date = mDateAdded.get
  override def dateActivated: Date = mDateActivated.get
}

object MappedSocialMedia extends MappedSocialMedia with LongKeyedMetaMapper[MappedSocialMedia] {
  override def dbIndexes = UniqueIndex(mCustomerNumber) :: super.dbIndexes
}