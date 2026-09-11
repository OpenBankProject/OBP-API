package code.apiproduct

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

case class ApiProduct(
  apiProductId: String,
  bankId: String,
  apiProductCode: String,
  parentApiProductCode: String,
  name: String,
  category: String,
  moreInfoUrl: String,
  termsAndConditionsUrl: String,
  description: String,
  collectionId: String,
  monthlySubscriptionCurrency: String,
  monthlySubscriptionAmount: String,
  perSecondCallLimit: Long,
  perMinuteCallLimit: Long,
  perHourCallLimit: Long,
  perDayCallLimit: Long,
  perWeekCallLimit: Long,
  perMonthCallLimit: Long,
  tagsEncoded: String
) extends ApiProductTrait {
  override def tags: List[String] = ApiProduct.decodeTags(tagsEncoded)
}

object ApiProduct {

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

  /** The unset default for every call-limit column, matching Mapper's `defaultValue = -1L`. */
  private val NoCallLimit = -1L

  private val selectColumns =
    fr"""SELECT apiproductid, bankid, apiproductcode, parentapiproductcode, name, category,
                moreinfourl, termsandconditionsurl, description, collectionid,
                monthlysubscriptioncurrency, monthlysubscriptionamount,
                persecondcalllimit, perminutecalllimit, perhourcalllimit,
                perdaycalllimit, perweekcalllimit, permonthcalllimit, tags
         FROM apiproduct"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[Long], Option[Long], Option[Long],
    Option[Long], Option[Long], Option[Long], Option[String])

  // MappedLong's reader is `if (isNull) defaultValue else v`, and every call-limit field here
  // declared `defaultValue = -1L`. A row written before these columns existed holds NULL, so
  // reading the column as a bare Long turns a legacy row into a failed query instead of -1.
  private val noCallLimit = -1L

  private def fromRow(row: Row): ApiProduct = row match {
    case (apiProductId, bankId, apiProductCode, parentApiProductCode, name, category,
          moreInfoUrl, termsAndConditionsUrl, description, collectionId,
          monthlySubscriptionCurrency, monthlySubscriptionAmount,
          perSecond, perMinute, perHour, perDay, perWeek, perMonth, tags) =>
      ApiProduct(apiProductId.orNull, bankId.orNull, apiProductCode.orNull,
        parentApiProductCode.orNull, name.orNull, category.orNull, moreInfoUrl.orNull,
        termsAndConditionsUrl.orNull, description.orNull, collectionId.orNull,
        monthlySubscriptionCurrency.orNull, monthlySubscriptionAmount.orNull,
        perSecond.getOrElse(noCallLimit), perMinute.getOrElse(noCallLimit),
        perHour.getOrElse(noCallLimit), perDay.getOrElse(noCallLimit),
        perWeek.getOrElse(noCallLimit), perMonth.getOrElse(noCallLimit), tags.orNull)
  }

  private def query(condition: Fragment): List[ApiProduct] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findByBankIdAndCode(bankId: String, apiProductCode: String): Box[ApiProduct] =
    query(fr"WHERE bankid = $bankId AND apiproductcode = $apiProductCode LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findAllByBankId(bankId: String): List[ApiProduct] =
    query(fr"WHERE bankid = $bankId")

  /** Tag filter matches a whole tag thanks to the surrounding pipes in the stored form. */
  def findAllByBankIdAndTag(bankId: String, tag: String): List[ApiProduct] =
    query(fr"WHERE bankid = $bankId AND tags LIKE ${s"%|$tag|%"}")

  def insert(
    bankId: String, apiProductCode: String, parentApiProductCode: String, name: String,
    category: String, moreInfoUrl: String, termsAndConditionsUrl: String, description: String,
    collectionId: String, monthlySubscriptionCurrency: String, monthlySubscriptionAmount: String,
    perSecondCallLimit: Long, perMinuteCallLimit: Long, perHourCallLimit: Long,
    perDayCallLimit: Long, perWeekCallLimit: Long, perMonthCallLimit: Long, encodedTags: String
  ): ApiProduct = {
    val newId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    import doobie.implicits.javasql._
    DoobieUtil.runUpdate(
      sql"""INSERT INTO apiproduct
            (apiproductid, bankid, apiproductcode, parentapiproductcode, name, category,
             moreinfourl, termsandconditionsurl, description, collectionid,
             monthlysubscriptioncurrency, monthlysubscriptionamount,
             persecondcalllimit, perminutecalllimit, perhourcalllimit,
             perdaycalllimit, perweekcalllimit, permonthcalllimit, tags, createdat, updatedat)
            VALUES
            ($newId, $bankId, $apiProductCode, $parentApiProductCode, $name, $category,
             $moreInfoUrl, $termsAndConditionsUrl, $description, $collectionId,
             $monthlySubscriptionCurrency, $monthlySubscriptionAmount,
             $perSecondCallLimit, $perMinuteCallLimit, $perHourCallLimit,
             $perDayCallLimit, $perWeekCallLimit, $perMonthCallLimit, $encodedTags, $now, $now)"""
        .update.run)
    ApiProduct(newId, bankId, apiProductCode, parentApiProductCode, name, category,
      moreInfoUrl, termsAndConditionsUrl, description, collectionId,
      monthlySubscriptionCurrency, monthlySubscriptionAmount,
      perSecondCallLimit, perMinuteCallLimit, perHourCallLimit,
      perDayCallLimit, perWeekCallLimit, perMonthCallLimit, encodedTags)
  }

  /**
   * Overwrite everything except the natural key (bankId, apiProductCode), which is what the row
   * was found by - matching Mapper's update branch, which likewise left those two columns alone.
   */
  def updateByBankIdAndCode(
    bankId: String, apiProductCode: String, parentApiProductCode: String, name: String,
    category: String, moreInfoUrl: String, termsAndConditionsUrl: String, description: String,
    collectionId: String, monthlySubscriptionCurrency: String, monthlySubscriptionAmount: String,
    perSecondCallLimit: Long, perMinuteCallLimit: Long, perHourCallLimit: Long,
    perDayCallLimit: Long, perWeekCallLimit: Long, perMonthCallLimit: Long, encodedTags: String
  ): Box[ApiProduct] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    import doobie.implicits.javasql._
    DoobieUtil.runUpdate(
      sql"""UPDATE apiproduct SET
              parentapiproductcode = $parentApiProductCode, name = $name, category = $category,
              moreinfourl = $moreInfoUrl, termsandconditionsurl = $termsAndConditionsUrl,
              description = $description, collectionid = $collectionId,
              monthlysubscriptioncurrency = $monthlySubscriptionCurrency,
              monthlysubscriptionamount = $monthlySubscriptionAmount,
              persecondcalllimit = $perSecondCallLimit, perminutecalllimit = $perMinuteCallLimit,
              perhourcalllimit = $perHourCallLimit, perdaycalllimit = $perDayCallLimit,
              perweekcalllimit = $perWeekCallLimit, permonthcalllimit = $perMonthCallLimit,
              tags = $encodedTags, updatedat = $now
            WHERE bankid = $bankId AND apiproductcode = $apiProductCode"""
        .update.run)
    findByBankIdAndCode(bankId, apiProductCode)
  }

  def deleteByBankIdAndCode(bankId: String, apiProductCode: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM apiproduct WHERE bankid = $bankId AND apiproductcode = $apiProductCode".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM apiproduct".update.run)
    ()
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
