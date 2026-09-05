package code.apiproductsubscription

import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

/**
 * Join table recording which Scope rows a subscription created (Phase 3), so that cancelling
 * removes exactly those and never a Scope granted by hand.
 */
class ApiProductSubscriptionScope extends LongKeyedMapper[ApiProductSubscriptionScope] with IdPK with CreatedUpdated {
  def getSingleton = ApiProductSubscriptionScope

  object ApiProductSubscriptionId extends MappedString(this, 50)
  object ScopeId extends MappedString(this, 50)

  def apiProductSubscriptionId: String = ApiProductSubscriptionId.get
  def scopeId: String = ScopeId.get
}

object ApiProductSubscriptionScope extends ApiProductSubscriptionScope with LongKeyedMetaMapper[ApiProductSubscriptionScope] {
  override def dbIndexes = Index(ApiProductSubscriptionId) :: super.dbIndexes
}

object MappedApiProductSubscriptionScopesProvider {

  def addScopeRecord(apiProductSubscriptionId: String, scopeId: String): Box[ApiProductSubscriptionScope] = tryo(
    ApiProductSubscriptionScope.create
      .ApiProductSubscriptionId(apiProductSubscriptionId)
      .ScopeId(scopeId)
      .saveMe()
  )

  def getScopeIds(apiProductSubscriptionId: String): List[String] =
    ApiProductSubscriptionScope
      .findAll(By(ApiProductSubscriptionScope.ApiProductSubscriptionId, apiProductSubscriptionId))
      .map(_.scopeId)

  def deleteScopeRecords(apiProductSubscriptionId: String): Box[Boolean] = tryo {
    ApiProductSubscriptionScope
      .findAll(By(ApiProductSubscriptionScope.ApiProductSubscriptionId, apiProductSubscriptionId))
      .foreach(_.delete_!)
    true
  }
}
