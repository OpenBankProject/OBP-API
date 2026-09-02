package code.apiproductsubscription

import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper.{By, ByList, NotBy}
import net.liftweb.util.Helpers.tryo

import java.util.Date

trait ApiProductSubscriptionsProvider {
  def createApiProductSubscription(
    bankId: String,
    apiProductCode: String,
    consumerId: String,
    status: String,
    startDate: Date,
    endDate: Option[Date],
    createdByUserId: String
  ): Box[ApiProductSubscriptionTrait]

  def getApiProductSubscriptionById(apiProductSubscriptionId: String): Box[ApiProductSubscriptionTrait]

  def getApiProductSubscriptionsByConsumerId(consumerId: String): List[ApiProductSubscriptionTrait]

  def getApiProductSubscriptionsByConsumerIds(consumerIds: List[String]): List[ApiProductSubscriptionTrait]

  def getApiProductSubscriptionsByBankIdAndProductCode(bankId: String, apiProductCode: String): List[ApiProductSubscriptionTrait]

  /** The one subscription that is not `cancelled` for this consumer and product, if any. */
  def getNonCancelledApiProductSubscription(consumerId: String, bankId: String, apiProductCode: String): Box[ApiProductSubscriptionTrait]

  /**
   * Moves the status. Refuses (Failure) a transition that is not in ApiProductSubscriptionStatus.
   * `endDate`, when given, replaces the stored end date.
   */
  def updateApiProductSubscriptionStatus(apiProductSubscriptionId: String, newStatus: String, endDate: Option[Date]): Box[ApiProductSubscriptionTrait]

  def setRateLimitingId(apiProductSubscriptionId: String, rateLimitingId: Option[String]): Box[ApiProductSubscriptionTrait]

  def deleteApiProductSubscription(apiProductSubscriptionId: String): Box[Boolean]
}

object MappedApiProductSubscriptionsProvider extends MdcLoggable with ApiProductSubscriptionsProvider {

  private def find(apiProductSubscriptionId: String): Box[ApiProductSubscription] =
    ApiProductSubscription.find(By(ApiProductSubscription.ApiProductSubscriptionId, apiProductSubscriptionId))

  override def createApiProductSubscription(
    bankId: String,
    apiProductCode: String,
    consumerId: String,
    status: String,
    startDate: Date,
    endDate: Option[Date],
    createdByUserId: String
  ): Box[ApiProductSubscriptionTrait] = {
    if (!ApiProductSubscriptionStatus.isValid(status)) Failure(s"Invalid status: $status")
    else tryo {
      val row = ApiProductSubscription.create
        .BankId(bankId)
        .ApiProductCode(apiProductCode)
        .ConsumerId(consumerId)
        .Status(status)
        .StartDate(startDate)
        .CreatedByUserId(createdByUserId)
        .RateLimitingId("")
      endDate.foreach(row.EndDate(_))
      row.saveMe()
    }
  }

  override def getApiProductSubscriptionById(apiProductSubscriptionId: String): Box[ApiProductSubscriptionTrait] =
    find(apiProductSubscriptionId)

  override def getApiProductSubscriptionsByConsumerId(consumerId: String): List[ApiProductSubscriptionTrait] =
    ApiProductSubscription.findAll(By(ApiProductSubscription.ConsumerId, consumerId))

  override def getApiProductSubscriptionsByConsumerIds(consumerIds: List[String]): List[ApiProductSubscriptionTrait] =
    if (consumerIds.isEmpty) Nil
    else ApiProductSubscription.findAll(ByList(ApiProductSubscription.ConsumerId, consumerIds))

  override def getApiProductSubscriptionsByBankIdAndProductCode(bankId: String, apiProductCode: String): List[ApiProductSubscriptionTrait] =
    ApiProductSubscription.findAll(
      By(ApiProductSubscription.BankId, bankId),
      By(ApiProductSubscription.ApiProductCode, apiProductCode)
    )

  override def getNonCancelledApiProductSubscription(consumerId: String, bankId: String, apiProductCode: String): Box[ApiProductSubscriptionTrait] =
    ApiProductSubscription.find(
      By(ApiProductSubscription.ConsumerId, consumerId),
      By(ApiProductSubscription.BankId, bankId),
      By(ApiProductSubscription.ApiProductCode, apiProductCode),
      NotBy(ApiProductSubscription.Status, ApiProductSubscriptionStatus.Cancelled)
    )

  override def updateApiProductSubscriptionStatus(apiProductSubscriptionId: String, newStatus: String, endDate: Option[Date]): Box[ApiProductSubscriptionTrait] =
    find(apiProductSubscriptionId).flatMap { row =>
      if (!ApiProductSubscriptionStatus.canTransition(row.status, newStatus))
        Failure(s"Invalid status transition: ${row.status} -> $newStatus")
      else tryo {
        row.Status(newStatus)
        endDate.foreach(row.EndDate(_))
        row.saveMe()
      }
    }

  override def setRateLimitingId(apiProductSubscriptionId: String, rateLimitingId: Option[String]): Box[ApiProductSubscriptionTrait] =
    find(apiProductSubscriptionId).flatMap(row => tryo(row.RateLimitingId(rateLimitingId.getOrElse("")).saveMe()))

  override def deleteApiProductSubscription(apiProductSubscriptionId: String): Box[Boolean] =
    find(apiProductSubscriptionId) match {
      case Full(row) => tryo(row.delete_!)
      case _ => Full(false)
    }
}
