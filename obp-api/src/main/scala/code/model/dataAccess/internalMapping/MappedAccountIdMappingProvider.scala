package code.model.dataAccess.internalMapping

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.AccountId
import doobie.implicits._
import net.liftweb.common._
import net.liftweb.util.Helpers.tryo

/**
 * Doobie implementation of the account-id-mapping store, replacing the Lift AccountIdMapping
 * entity.
 *
 * Kept under its original name rather than a Doobie* one: DynamicUtil's compiled-code template
 * hands this exact import (code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider)
 * to every dynamic connector method, and connector method bodies are stored as raw Scala source
 * in the connectormethod table and compiled at request time (see DoobieConnectorMethodProvider).
 * A bank's already-deployed dynamic connector code can reference this name; renaming the object
 * would break it on the next compile, for a class that is otherwise free to rename.
 *
 * getOrCreateAccountId inserts on a cache miss with no prior existence check, then falls back to
 * re-reading the row on a write failure - the shape this took under Mapper for a concurrent
 * insert of the same accountPlainTextReference to collide and retry against. The unique index
 * that would make that collision actually happen is on mAccountId (fresh random UUID per insert,
 * so it never collides) and on (mAccountId, mAccountPlainTextReference), not on
 * mAccountPlainTextReference alone - so under both the old and new implementation, two
 * concurrent inserts for the same reference do not collide and both succeed. That gap in the
 * schema is not something this migration changes; the retry branch is kept because removing it
 * would be removing dead code under a different guise of "just migrating the table".
 */
object MappedAccountIdMappingProvider extends AccountIdMappingProvider with MdcLoggable {

  override def getOrCreateAccountId(accountPlainTextReference: String): Box[AccountId] = {
    findByReference(accountPlainTextReference) match {
      case Full(accountId) =>
        logger.debug(s"getOrCreateAccountId --> the mappedAccountIdMapping has been existing in server !")
        Full(accountId)
      case Empty =>
        val newAccountId = APIUtil.generateUUID()
        val inserted: Box[Int] = tryo {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO accountidmapping (maccountid, maccountplaintextreference, createdat, updatedat)
                  VALUES ($newAccountId, $accountPlainTextReference, NOW(), NOW())"""
              .update.run)
        }
        inserted match {
          case Full(_) =>
            logger.debug(s"getOrCreateAccountId--> create mappedAccountIdMapping : $newAccountId")
            Full(AccountId(newAccountId))
          case Failure(_, _, _) =>
            // Unique-index violation from a concurrent insert — re-fetch the committed row.
            findByReference(accountPlainTextReference)
          case Empty =>
            findByReference(accountPlainTextReference)
        }
      case failure => failure
    }
  }

  private def findByReference(accountPlainTextReference: String): Box[AccountId] =
    DoobieUtil.runQuery(
      sql"SELECT maccountid FROM accountidmapping WHERE maccountplaintextreference = $accountPlainTextReference LIMIT 1"
        .query[String].option
    ) match {
      case Some(id) => Full(AccountId(id))
      case None     => Empty
    }

  override def getAccountPlainTextReference(accountId: AccountId): Box[String] =
    DoobieUtil.runQuery(
      sql"SELECT maccountplaintextreference FROM accountidmapping WHERE maccountid = ${accountId.value} LIMIT 1"
        .query[String].option
    ) match {
      case Some(ref) => Full(ref)
      case None      => Empty
    }
}
