package code.api.dynamic.entity.query

import cats.effect.IO
import org.json4s.JsonAST.JObject

/**
 * The Shape B seam: one query contract, swappable implementations.
 *
 * Implementations (selected once at startup by capability detection):
 *   - InMemoryQueryBackend     — portable floor for deployments with no projection backend
 *   - PostgresProjectionBackend — Approach A: per-entity typed projection tables + indexes (Phase 3+)
 *   - SqlServerProjectionBackend — deferred (Phase 5.3)
 *
 * The public DE endpoint and the [[QueryPlan]] it parses are identical on every backend; only
 * `query`/`provision` differ. Unservable queries return an error — never a silent fallback.
 */
trait DynamicEntityQueryBackend {

  /** Short name for logging / capability reporting (e.g. "in-memory", "postgres"). */
  def name: String

  /**
   * Execute a validated plan and return the matching records (already filtered, sorted, paged).
   * Records are canonical JObjects hydrated from the blob store.
   */
  def query(
    entityName: String,
    bankId: Option[String],
    userId: Option[String],
    isPersonalEntity: Boolean,
    plan: QueryPlan
  ): IO[List[JObject]]

  /**
   * Provision (or reconcile) whatever storage this backend needs for the entity's declared
   * `indexed` fields — DDL, backfill, index build. Default no-op (in-memory needs nothing).
   */
  def provision(entityName: String, bankId: Option[String]): IO[Unit] = IO.unit
}
