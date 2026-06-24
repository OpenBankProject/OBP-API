package code.api.dynamic.entity.projection

import cats.effect.IO
import code.api.dynamic.entity.helper.DynamicEntityHelper
import code.api.dynamic.entity.query.{DynamicEntityQueryBackend, JoinClause, Page, QueryPlan, Quantifier}
import doobie._
import doobie.implicits._
import org.json4s.JsonAST.JObject

/**
 * Approach A query backend (DE_indexing, Phase 3): serves a validated [[QueryPlan]] from the entity's
 * projection table, joined back to the canonical blob table for access-scoping and to return the
 * canonical record. One SQL statement does filter + sort + paginate (index-accelerated) and yields
 * the blobs already in order — no second hydration round-trip.
 *
 *   SELECT d.dataJson
 *   FROM <projection> p JOIN <blob> d ON d.id = p.data_id
 *   WHERE <scope on d> [AND <index predicates on p>]
 *   [ORDER BY <p cols>] [LIMIT ?] [OFFSET ?]
 *
 * Scalar operators only (spatial = Phase 4). The caller (backend selection) guarantees every queried
 * field is `indexed` and `ready` before routing here.
 */
object PostgresProjectionBackend extends DynamicEntityQueryBackend {

  def name: String = "postgres-projection"

  def query(entityName: String, bankId: Option[String], userId: Option[String], isPersonalEntity: Boolean, plan: QueryPlan): IO[List[JObject]] = {
    val indexed   = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).map(_.indexedFields).getOrElse(Map.empty)
    val safeTable = ProjectionNaming.tableName(bankId, entityName)
    val P = "p"; val D = "d"
    def columnOf(f: String): Option[String]  = indexed.get(f).map(_ => s"$P." + ProjectionNaming.columnName(f))
    def sqlTypeOf(f: String): Option[String] = indexed.get(f).map(s => ProjectionDDL.sqlColumnType(s.fieldType.toString))

    // Each join compiles to a correlated [NOT] EXISTS subquery; None if any of its fields can't resolve.
    // `userId` is the authenticated caller (joins are served only on the authenticated get-all path), used
    // for user-scoped ACL evaluation when the child entity is row-level.
    val joinFragsRaw = plan.joins.map(j => existsFragment(j, bankId, P, D, userId))
    val joinFragsOpt: Option[List[Fragment]] = if (joinFragsRaw.exists(_.isEmpty)) None else Some(joinFragsRaw.flatten)

    (ProjectionSql.predicates(plan, columnOf, sqlTypeOf), ProjectionSql.orderBy(plan, columnOf), joinFragsOpt) match {
      case (Some(preds), Some(ords), Some(joinFrags)) =>
        val scope     = ProjectionStore.scope(bankId, entityName, isPersonalEntity, userId, D)
        val condParts = (if (preds == Fragment.empty) Nil else List(preds)) ++ joinFrags
        val whereAll  =
          if (condParts.isEmpty) fr"WHERE" ++ scope
          else fr"WHERE" ++ scope ++ fr"AND" ++ ProjectionSql.intercalate(condParts, fr"AND")
        val q =
          fr"SELECT" ++ Fragment.const(s"$D.${ProjectionStore.jsonColumn}") ++
          fr"FROM" ++ Fragment.const(s"$safeTable $P") ++
          fr"JOIN" ++ Fragment.const(s"${ProjectionStore.blobTable} $D") ++
          fr"ON" ++ Fragment.const(s"$D.${ProjectionStore.idColumn} = $P.data_id") ++
          whereAll ++ ords ++ ProjectionSql.limitOffset(plan)
        ProjectionDb.run(q.query[String].to[List]).map(_.map(s => com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[JObject]))
      case _ =>
        IO.raiseError(new RuntimeException(s"PostgresProjectionBackend: unresolved field in query plan for $entityName"))
    }
  }

  /**
   * One-hop correlated `[NOT] EXISTS` subquery for a resolved [[JoinClause]] (see
   * ideas/DYNAMIC_ENTITY_JOIN_QUERIES.md). Correlation is on the canonical record id ([[ProjectionStore.idColumn]]),
   * since a reference value stores the target record's DynamicDataId. The child projection table `cp` is
   * joined only when needed (the link lives on the child, or there is a nested predicate on child columns);
   * otherwise only the child blob `cd` (scoped) is touched. When the child entity opts into row-level
   * access, an ACL sub-EXISTS restricts the count to rows the caller can read (user-scoped semantics).
   * Returns None if any referenced child column can't resolve (caller raises / 409 upstream guards this).
   */
  private def existsFragment(join: JoinClause, parentBankId: Option[String], parentProjAlias: String, parentBlobAlias: String, callerUserId: Option[String]): Option[Fragment] = {
    val childEntity  = join.childEntity
    val childIndexed = DynamicEntityHelper.definitionsMap.get((parentBankId, childEntity)).map(_.indexedFields).getOrElse(Map.empty)
    val childTable   = ProjectionNaming.tableName(parentBankId, childEntity)
    val cp = "cp"; val cd = "cd"
    def childColumnOf(f: String): Option[String]  = childIndexed.get(f).map(_ => s"$cp." + ProjectionNaming.columnName(f))
    def childSqlTypeOf(f: String): Option[String] = childIndexed.get(f).map(s => ProjectionDDL.sqlColumnType(s.fieldType.toString))

    val childPredsOpt = ProjectionSql.predicates(QueryPlan(join.predicate, Nil, Nil, Page.empty), childColumnOf, childSqlTypeOf)
    childPredsOpt.map { childPreds =>
      val linkCol = ProjectionNaming.columnName(join.linkField)
      // onChild: child FK (cp.link) == parent id (D.id). else: parent FK (P.link) == child id (cd.id).
      val correlate =
        if (join.onChild) Fragment.const(s"$cp.$linkCol = $parentBlobAlias.${ProjectionStore.idColumn}")
        else              Fragment.const(s"$parentProjAlias.$linkCol = $cd.${ProjectionStore.idColumn}")
      val needCp   = join.onChild || join.predicate.nonEmpty
      val from     =
        if (needCp)
          Fragment.const(s"$childTable $cp") ++ fr"JOIN" ++ Fragment.const(s"${ProjectionStore.blobTable} $cd") ++
            fr"ON" ++ Fragment.const(s"$cd.${ProjectionStore.idColumn} = $cp.data_id")
        else
          Fragment.const(s"${ProjectionStore.blobTable} $cd")
      val childScope = ProjectionStore.scope(parentBankId, childEntity, isPersonalEntity = false, None, cd)
      val predFrag   = if (childPreds == Fragment.empty) Fragment.empty else fr"AND" ++ childPreds
      val aclFrag    = childAclFragment(childEntity, parentBankId, cd, callerUserId)
      val keyword    = if (join.quantifier == Quantifier.NotExists) fr"NOT EXISTS" else fr"EXISTS"
      keyword ++ fr"(SELECT 1 FROM" ++ from ++ fr"WHERE" ++ correlate ++ fr"AND" ++ childScope ++ predFrag ++ aclFrag ++ fr")"
    }
  }

  /** ACL restriction for a row-level child: only rows the caller can read count toward EXISTS / NOT EXISTS. */
  private def childAclFragment(childEntity: String, bankId: Option[String], childBlobAlias: String, callerUserId: Option[String]): Fragment = {
    val isRowLevel = DynamicEntityHelper.definitionsMap.get((bankId, childEntity)).exists(_.useRowLevelAccess)
    (isRowLevel, callerUserId) match {
      case (true, Some(uid)) =>
        fr"AND EXISTS (SELECT 1 FROM" ++ Fragment.const(s"${ProjectionStore.aclTable} acl") ++
          fr"WHERE" ++ Fragment.const(s"acl.${ProjectionStore.aclDataIdColumn} = $childBlobAlias.${ProjectionStore.idColumn}") ++
          fr"AND" ++ Fragment.const(s"acl.${ProjectionStore.aclUserIdColumn}") ++ fr"=" ++ fr0"$uid" ++
          fr"AND" ++ Fragment.const(s"acl.${ProjectionStore.aclCanReadColumn} = true") ++ fr")"
      case _ => Fragment.empty
    }
  }
}
