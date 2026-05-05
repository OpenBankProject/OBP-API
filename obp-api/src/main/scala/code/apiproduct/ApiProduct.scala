package code.apiproduct

import code.util.{MappedUUID, UUIDString}
import net.liftweb.mapper._

class ApiProduct extends ApiProductTrait with LongKeyedMapper[ApiProduct] with IdPK with CreatedUpdated {
  def getSingleton = ApiProduct

  object ApiProductId extends MappedUUID(this)
  object BankId extends UUIDString(this)
  object ApiProductCode extends MappedString(this, 50)
  object ParentApiProductCode extends MappedString(this, 50)
  object Name extends MappedString(this, 256)
  object Category extends MappedString(this, 256)
  object MoreInfoUrl extends MappedString(this, 2000)
  object TermsAndConditionsUrl extends MappedString(this, 2000)
  object Description extends MappedString(this, 2000)
  object CollectionId extends MappedString(this, 50)
  object MonthlySubscriptionCurrency extends MappedString(this, 3)
  object MonthlySubscriptionAmount extends MappedString(this, 50)
  object PerSecondCallLimit extends MappedLong(this) { override def defaultValue = -1L }
  object PerMinuteCallLimit extends MappedLong(this) { override def defaultValue = -1L }
  object PerHourCallLimit extends MappedLong(this) { override def defaultValue = -1L }
  object PerDayCallLimit extends MappedLong(this) { override def defaultValue = -1L }
  object PerWeekCallLimit extends MappedLong(this) { override def defaultValue = -1L }
  object PerMonthCallLimit extends MappedLong(this) { override def defaultValue = -1L }
  // Pipe-delimited list of tags, e.g. "|featured|beta|". Leading/trailing pipes make LIKE filtering exact.
  object Tags extends MappedString(this, 2000)

  override def apiProductId: String = ApiProductId.get
  override def bankId: String = BankId.get
  override def apiProductCode: String = ApiProductCode.get
  override def parentApiProductCode: String = ParentApiProductCode.get
  override def name: String = Name.get
  override def category: String = Category.get
  override def moreInfoUrl: String = MoreInfoUrl.get
  override def termsAndConditionsUrl: String = TermsAndConditionsUrl.get
  override def description: String = Description.get
  override def collectionId: String = CollectionId.get
  override def monthlySubscriptionCurrency: String = MonthlySubscriptionCurrency.get
  override def monthlySubscriptionAmount: String = MonthlySubscriptionAmount.get
  override def perSecondCallLimit: Long = PerSecondCallLimit.get
  override def perMinuteCallLimit: Long = PerMinuteCallLimit.get
  override def perHourCallLimit: Long = PerHourCallLimit.get
  override def perDayCallLimit: Long = PerDayCallLimit.get
  override def perWeekCallLimit: Long = PerWeekCallLimit.get
  override def perMonthCallLimit: Long = PerMonthCallLimit.get
  override def tags: List[String] = ApiProduct.decodeTags(Tags.get)
}

object ApiProduct extends ApiProduct with LongKeyedMetaMapper[ApiProduct] {
  override def dbIndexes = UniqueIndex(BankId, ApiProductCode) :: Index(BankId) :: super.dbIndexes

  // Wire format: List[String]. Storage format: "|tag1|tag2|" (leading/trailing pipes so LIKE '%|foo|%' matches exactly).
  // Tags are normalised: trimmed, lower-cased, pipe-stripped, de-duplicated, empty entries dropped.
  def encodeTags(tags: List[String]): String = {
    val normalised = tags
      .map(_.trim.toLowerCase.replace("|", ""))
      .filter(_.nonEmpty)
      .distinct
    if (normalised.isEmpty) "" else normalised.mkString("|", "|", "|")
  }

  def decodeTags(stored: String): List[String] = {
    if (stored == null || stored.isEmpty) Nil
    else stored.split('|').toList.filter(_.nonEmpty)
  }
}

trait ApiProductTrait {
  def apiProductId: String
  def bankId: String
  def apiProductCode: String
  def parentApiProductCode: String
  def name: String
  def category: String
  def moreInfoUrl: String
  def termsAndConditionsUrl: String
  def description: String
  def collectionId: String
  def monthlySubscriptionCurrency: String
  def monthlySubscriptionAmount: String
  def perSecondCallLimit: Long
  def perMinuteCallLimit: Long
  def perHourCallLimit: Long
  def perDayCallLimit: Long
  def perWeekCallLimit: Long
  def perMonthCallLimit: Long
  def tags: List[String]
}
