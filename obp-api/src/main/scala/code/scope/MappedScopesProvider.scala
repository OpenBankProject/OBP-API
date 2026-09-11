package code.scope

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import scala.concurrent.Future

/**
 * One role granted to a consumer, optionally scoped to a bank.
 *
 * Nothing constrains (mbankid, mconsumerid, mrolename) even though every lookup and the delete key
 * off exactly that triple and addScope inserts without checking. Adding the same scope twice
 * therefore succeeds and leaves two rows, of which a lookup sees one and a delete removes one.
 * Pre-existing; the lookups below pin id ASC so which row that is stays deterministic rather than
 * being whichever the database happened to return.
 */
case class MappedScope(
  scopeId: String,
  bankId: String,
  consumerId: String,
  roleName: String
) extends Scope

object MappedScope {

  private val selectColumns =
    fr"SELECT mscopeid, mbankid, mconsumerid, mrolename FROM mappedscope"

  private type Row = (Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): MappedScope = row match {
    case (scopeId, bankId, consumerId, roleName) => MappedScope(scopeId.orNull, bankId.orNull,
        consumerId.orNull, roleName.orNull)
  }

  private def query(condition: Fragment): List[MappedScope] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[MappedScope] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def find(bankId: String, consumerId: String, roleName: String): Box[MappedScope] =
    one(fr"WHERE mbankid = $bankId AND mconsumerid = $consumerId AND mrolename = $roleName")

  def findByScopeId(scopeId: String): Box[MappedScope] = one(fr"WHERE mscopeid = $scopeId")

  def findAllByConsumerId(consumerId: String): List[MappedScope] =
    query(fr"WHERE mconsumerid = $consumerId ORDER BY updatedat DESC, id DESC")

  def findAll(): List[MappedScope] = query(fr"ORDER BY updatedat DESC, id DESC")

  /** Used by the historical role-rename migration. */
  def findAllByRoleName(roleName: String): List[MappedScope] =
    query(fr"WHERE mrolename = $roleName ORDER BY id ASC")

  def updateRoleName(scopeId: String, roleName: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedscope SET mrolename = $roleName,
              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
            WHERE mscopeid = $scopeId""".update.run)
    ()
  }

  def insert(bankId: String, consumerId: String, roleName: String): MappedScope = {
    val scopeId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedscope (mscopeid, mbankid, mconsumerid, mrolename, createdat, updatedat)
            VALUES ($scopeId, $bankId, $consumerId, $roleName, $now, $now)"""
        .update.run)
    MappedScope(scopeId, bankId, consumerId, roleName)
  }

  /** Deletes by the generated id, so a duplicated triple loses exactly one row, as before. */
  def deleteByScopeId(scopeId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM mappedscope WHERE mscopeid = $scopeId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedscope".update.run)
    ()
  }
}

object MappedScopesProvider extends ScopeProvider {

  override def getScope(bankId: String, consumerId: String, roleName: String): Box[Scope] =
    MappedScope.find(bankId, consumerId, roleName)

  override def getScopeById(scopeId: String): Box[Scope] = MappedScope.findByScopeId(scopeId)

  override def getScopesByConsumerId(consumerId: String): Box[List[Scope]] =
    Some(MappedScope.findAllByConsumerId(consumerId))

  override def getScopesByConsumerIdFuture(consumerId: String): Future[Box[List[Scope]]] =
    Future(getScopesByConsumerId(consumerId))

  override def getScopes(): Box[List[Scope]] = Some(MappedScope.findAll())

  override def getScopesFuture(): Future[Box[List[Scope]]] = Future(getScopes())

  override def deleteScope(scope: Box[Scope]): Box[Boolean] =
    for {
      findScope <- scope
      foundScope <- MappedScope.find(findScope.bankId, findScope.consumerId, findScope.roleName)
    } yield MappedScope.deleteByScopeId(foundScope.scopeId)

  override def addScope(bankId: String, consumerId: String, roleName: String): Box[Scope] =
    Some(MappedScope.insert(bankId, consumerId, roleName))
}
