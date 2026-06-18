package code.api.dynamic.entity.projection

import cats.effect.IO
import code.api.dynamic.entity.helper.DynamicEntityHelper
import code.api.dynamic.entity.query.{DynamicEntityQueryBackend, QueryPlan}
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

    (ProjectionSql.predicates(plan, columnOf, sqlTypeOf), ProjectionSql.orderBy(plan, columnOf)) match {
      case (Some(preds), Some(ords)) =>
        val scope    = ProjectionStore.scope(bankId, entityName, isPersonalEntity, userId, D)
        val whereAll = if (preds == Fragment.empty) fr"WHERE" ++ scope else fr"WHERE" ++ scope ++ fr"AND" ++ preds
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
}
