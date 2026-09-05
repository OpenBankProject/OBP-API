package code.etag

import code.api.util.DoobieUtil
import doobie.implicits._

/**
 * One stored ETag: the cache key, the hash last seen for it, and when that was written.
 */
case class ETagRow(eTagResource: String, eTagValue: String, lastUpdatedMSSinceEpoch: Long)

/**
 * Doobie implementation of the ETag store, replacing the Lift MappedETag entity.
 *
 * Only APIUtil.checkIfModifiedSinceHeader uses this, and only in one shape: look the cache key
 * up, then either rewrite the hash or insert a first row for it. Both writes happen inside a
 * Future the request does not wait for, which is why they go through runUpdate - runQuery's
 * out-of-request fallback transactor is Strategy.void over a pool with autoCommit off, so the
 * write would be rolled back the moment it returned.
 *
 * The table is named ETag rather than MappedETag: the entity overrode dbTableName. It is written
 * unquoted here on purpose. Quoted identifiers are case-sensitive, and the table as created is
 * ETAG; a quoted "ETag" does not find it. That failure is worse than it sounds - the reset in
 * ServerSetup runs while ScalaTest is still discovering suites, so a statement that throws there
 * makes every suite fail to instantiate and the run reports zero tests instead of a red one.
 *
 * update is scoped by the cache key rather than by row id. The Mapper version held the row it
 * had just read and saved that object back; keying the UPDATE on the same unique column the
 * read used is the equivalent, and it does not need the row's identity to survive the trip
 * through the Future.
 */
object ETagStore {

  def find(eTagResource: String): Option[ETagRow] =
    DoobieUtil.runQuery(
      sql"""SELECT etagresource, etagvalue, lastupdatedmssinceepoch FROM etag
            WHERE etagresource = $eTagResource LIMIT 1"""
        .query[(String, String, Long)].option
    ).map { case (r, v, t) => ETagRow(r, v, t) }

  def updateValue(eTagResource: String, eTagValue: String, nowMs: Long): Boolean =
    DoobieUtil.runUpdate(
      sql"""UPDATE etag SET etagvalue = $eTagValue, lastupdatedmssinceepoch = $nowMs
            WHERE etagresource = $eTagResource"""
        .update.run) > 0

  def create(eTagResource: String, eTagValue: String, nowMs: Long): Boolean =
    DoobieUtil.runUpdate(
      sql"""INSERT INTO etag (etagresource, etagvalue, lastupdatedmssinceepoch)
            VALUES ($eTagResource, $eTagValue, $nowMs)"""
        .update.run) > 0
}
