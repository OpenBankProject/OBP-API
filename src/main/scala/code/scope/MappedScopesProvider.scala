package code.scope

import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.Box
import net.liftweb.mapper._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedScopesProvider extends ScopeProvider {
  override def getScope(bankId: String, consumerId: String, roleName: String): Box[Scope] = {
    // Return a Box so we can handle errors later.
    MappedScope.find(
      By(MappedScope.mBankId, bankId),
      By(MappedScope.mConsumerId, consumerId),
      By(MappedScope.mRoleName, roleName)
    )
  }

  override def getScopeById(scopeId: String): Box[Scope] = {
    // Return a Box so we can handle errors later.
    MappedScope.find(
      By(MappedScope.mScopeId, scopeId)
    )
  }

  override def getScopesByConsumerId(consumerId: String): Box[List[Scope]] = {
    // Return a Box so we can handle errors later.
    Some(MappedScope.findAll(
      By(MappedScope.mConsumerId, consumerId),
      OrderBy(MappedScope.updatedAt, Descending)))
  }
  override def getScopesByConsumerIdFuture(consumerId: String): Future[Box[List[Scope]]] = {
    // Return a Box so we can handle errors later.
    Future {
      getScopesByConsumerId(consumerId)
    }
  }

  override def getScopes: Box[List[Scope]] = {
    // Return a Box so we can handle errors later.
    Some(MappedScope.findAll(OrderBy(MappedScope.updatedAt, Descending)))
  }

  override def getScopesFuture(): Future[Box[List[Scope]]] = {
    Future {
      getScopes()
    }
  }

  override def deleteScope(scope: Box[Scope]): Box[Boolean] = {
    // Return a Box so we can handle errors later.
    for {
      findScope <- scope
      bankId <- Some(findScope.bankId)
      consumerId <- Some(findScope.consumerId)
      roleName <- Some(findScope.roleName)
      foundScope <-  MappedScope.find(
        By(MappedScope.mBankId, bankId),
        By(MappedScope.mConsumerId, consumerId),
        By(MappedScope.mRoleName, roleName)
      )
    }
      yield {
        MappedScope.delete_!(foundScope)
      }
  }

  override def addScope(bankId: String, consumerId: String, roleName: String): Box[Scope] = {
    // Return a Box so we can handle errors later.
    val addScope = MappedScope.create
      .mBankId(bankId)
      .mConsumerId(consumerId)
      .mRoleName(roleName)
      .saveMe()
    Some(addScope)
  }
}

class MappedScope extends Scope 
  with LongKeyedMapper[MappedScope] with IdPK with CreatedUpdated {

  def getSingleton = MappedScope

  object mScopeId extends MappedUUID(this)
  object mBankId extends UUIDString(this)
  object mConsumerId extends UUIDString(this)
  object mRoleName extends MappedString(this, 64)

  override def scopeId: String = mScopeId.get.toString
  override def bankId: String = mBankId.get
  override def consumerId: String = mConsumerId.get
  override def roleName: String = mRoleName.get
}


object MappedScope extends MappedScope with LongKeyedMetaMapper[MappedScope] {
  override def dbIndexes = UniqueIndex(mScopeId) :: super.dbIndexes
}