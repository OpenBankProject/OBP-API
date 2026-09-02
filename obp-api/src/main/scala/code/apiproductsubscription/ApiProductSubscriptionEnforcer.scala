package code.apiproductsubscription

import code.api.util.APIUtil.ResourceDoc
import code.api.util.ApiRole
import code.api.util.RoleCombination
import code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
import code.apiproduct.{ApiProductTrait, MappedApiProductsProvider}
import code.ratelimiting.RateLimitingDI
import code.scope.Scope
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Full

import java.util.{Calendar, Date, TimeZone}
import scala.concurrent.Future

/**
 * Phase 3 of API_PRODUCT_SUBSCRIPTION_PLAN.md: makes a subscription's status enforceable.
 *
 * Called after every status change (NewStyle.updateApiProductSubscriptionStatus, which the POST
 * auto-activation and the DELETE route also go through). Only three statuses touch anything:
 *
 *   active     - one RateLimiting row for the consumer with the product's six limits (-1 copied as
 *                -1 = unlimited), and a Scope for each Role required by the endpoints in the
 *                product's API Collection
 *   suspended  - the same row rewritten to six zeros. Rows are summed, so the row grants nothing:
 *                a consumer with no other active row is blocked (sum 0, 429); Scopes are kept so
 *                reinstatement is a limits-only change
 *   cancelled  - the row deleted and the Scopes this subscription added removed
 *
 * requested and past_due are bookkeeping. Everything this object creates is remembered on the
 * subscription (RateLimitingId, ApiProductSubscriptionScope rows) and only that is ever touched:
 * limits and Scopes granted by hand are never removed. If an admin has deleted the row by hand, a
 * fresh one is created and the new id stored; cancelling a subscription whose row is gone is a no-op.
 */
object ApiProductSubscriptionEnforcer extends MdcLoggable {

  /** A RateLimiting row needs a toDate; an open-ended subscription gets this one. */
  val OpenEndedToDate: Date = {
    val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    c.clear()
    c.set(2100, Calendar.JANUARY, 1, 0, 0, 0)
    c.getTime
  }

  private def rateLimiting = RateLimitingDI.rateLimiting.vend
  private def scopes = Scope.scope.vend
  private def subscriptions = MappedApiProductSubscriptionsProvider
  private def scopeRecords = MappedApiProductSubscriptionScopesProvider

  /** Apply the consequences of the subscription's current status and return the refreshed subscription. */
  def onStatusChanged(subscription: ApiProductSubscriptionTrait): Future[ApiProductSubscriptionTrait] = {
    val applied: Future[Unit] = subscription.status match {
      case ApiProductSubscriptionStatus.Active    => applyActive(subscription)
      case ApiProductSubscriptionStatus.Suspended => applySuspended(subscription)
      case ApiProductSubscriptionStatus.Cancelled => applyCancelled(subscription)
      case _                                      => Future.successful(())
    }
    applied.map(_ => subscriptions.getApiProductSubscriptionById(subscription.apiProductSubscriptionId).getOrElse(subscription))
  }

  private def limitsOf(product: ApiProductTrait): List[Long] = List(
    product.perSecondCallLimit, product.perMinuteCallLimit, product.perHourCallLimit,
    product.perDayCallLimit, product.perWeekCallLimit, product.perMonthCallLimit
  )

  private def applyActive(subscription: ApiProductSubscriptionTrait): Future[Unit] =
    MappedApiProductsProvider.getApiProductByBankIdAndCode(subscription.bankId, subscription.apiProductCode) match {
      case Full(product) =>
        val limits = limitsOf(product)
        // A product with no limits at all (all -1) needs no row; but a row may exist from `suspended`, so remove it.
        val rowDone = if (limits.exists(_ != -1L)) writeRow(subscription, limits) else deleteRow(subscription)
        rowDone.map(_ => grantScopes(subscription, product))
      case _ =>
        logger.warn(s"ApiProductSubscriptionEnforcer: product ${subscription.bankId}/${subscription.apiProductCode} not found for subscription ${subscription.apiProductSubscriptionId}; nothing enforced")
        Future.successful(())
    }

  private def applySuspended(subscription: ApiProductSubscriptionTrait): Future[Unit] =
    writeRow(subscription, List.fill(6)(0L))

  private def applyCancelled(subscription: ApiProductSubscriptionTrait): Future[Unit] =
    deleteRow(subscription).map(_ => revokeScopes(subscription))

  /** Create or rewrite the subscription's own RateLimiting row with these six values. */
  private def writeRow(subscription: ApiProductSubscriptionTrait, limits: List[Long]): Future[Unit] = {
    val from = subscription.startDate
    val to = subscription.endDate.getOrElse(OpenEndedToDate)
    val List(s, m, h, d, w, mo) = limits.map(l => Option(l.toString))
    def create: Future[Unit] =
      rateLimiting.createConsumerCallLimits(subscription.consumerId, from, to, None, None, Some(subscription.bankId), s, m, h, d, w, mo).map {
        case Full(row) => subscriptions.setRateLimitingId(subscription.apiProductSubscriptionId, Some(row.rateLimitingId)); ()
        case other     => logger.warn(s"ApiProductSubscriptionEnforcer: could not create rate limit row for subscription ${subscription.apiProductSubscriptionId}: $other")
      }
    subscription.rateLimitingId match {
      case Some(id) =>
        rateLimiting.getByRateLimitingId(id).flatMap {
          case Full(_) => rateLimiting.updateConsumerCallLimits(id, from, to, None, None, Some(subscription.bankId), s, m, h, d, w, mo).map(_ => ())
          case _       => create // deleted by hand: start again and remember the new id
        }
      case None => create
    }
  }

  /** Delete the subscription's own RateLimiting row, if any, and forget its id. Other rows are never touched. */
  private def deleteRow(subscription: ApiProductSubscriptionTrait): Future[Unit] =
    subscription.rateLimitingId match {
      case Some(id) => rateLimiting.deleteByRateLimitingId(id).map { _ => subscriptions.setRateLimitingId(subscription.apiProductSubscriptionId, None); () }
      case None     => Future.successful(())
    }

  /** Roles required by the endpoints in the product's API Collection, RoleCombinations flattened. Empty when no collection. */
  def requiredRoles(product: ApiProductTrait): List[ApiRole] =
    Option(product.collectionId).filter(_.nonEmpty) match {
      case None => Nil
      case Some(collectionId) =>
        val operationIds = MappedApiCollectionEndpointsProvider.getApiCollectionEndpoints(collectionId).map(_.operationId)
        ResourceDoc.getResourceDocs(operationIds)
          .flatMap(_.roles.getOrElse(Nil))
          .flatMap { case RoleCombination(rs) => rs; case r => List(r) }
          .distinct
    }

  /**
   * Add a Scope per required Role at the product's bank (or "" for roles that are not bank-scoped) and
   * record each one added. A Scope that already exists, whether granted by hand or by an earlier
   * activation of this subscription, is left alone and not recorded.
   */
  private def grantScopes(subscription: ApiProductSubscriptionTrait, product: ApiProductTrait): Unit =
    requiredRoles(product).foreach { role =>
      val scopeBankId = if (role.requiresBankId) product.bankId else ""
      scopes.getScope(scopeBankId, subscription.consumerId, role.toString) match {
        case Full(_) => ()
        case _ =>
          scopes.addScope(scopeBankId, subscription.consumerId, role.toString)
            .foreach(scope => scopeRecords.addScopeRecord(subscription.apiProductSubscriptionId, scope.scopeId))
      }
    }

  /** Remove exactly the Scopes this subscription recorded, then the records. */
  private def revokeScopes(subscription: ApiProductSubscriptionTrait): Unit = {
    scopeRecords.getScopeIds(subscription.apiProductSubscriptionId).foreach { scopeId =>
      scopes.deleteScope(scopes.getScopeById(scopeId))
    }
    scopeRecords.deleteScopeRecords(subscription.apiProductSubscriptionId)
  }
}
