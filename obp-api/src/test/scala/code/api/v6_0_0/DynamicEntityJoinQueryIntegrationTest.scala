package code.api.v6_0_0

import code.api.dynamic.entity.projection.{IndexingCapabilities, ProjectionProvisioner}
import code.api.dynamic.entity.projection.PostgresProjectionBackend
import code.api.dynamic.entity.query._
import code.api.util.APIUtil
import code.DynamicData.{DynamicDataAccessProvider, DynamicDataProvider}
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import cats.effect.unsafe.implicits.global
import net.liftweb.util.StringHelpers
import org.json4s.JsonAST._

/**
 * Phase 3 integration proof for one-hop join queries (obp_exists / obp_not_exists): exercises the actual
 * EXISTS / NOT EXISTS SQL that [[PostgresProjectionBackend.query]] generates, against the real Postgres
 * test DB. Validates the three meanings of (non-)existence, has-any / has-none, NULL-safety (correlated
 * NOT EXISTS, never NOT IN), and user-scoped ACL evaluation for a row-level child.
 *
 * Real definitions are registered (so the planner-resolved [[JoinClause]] resolves child columns via
 * `definitionsMap`), projections provisioned, and rows written through the real provider (dual-write
 * populates the projection). Entity names carry a per-run suffix so reruns never see stale data.
 *
 * Gated OFF by default (set `test.projection.postgres=true` with a Postgres `db.url`). The unit-level
 * parser / planner / in-memory behaviour is covered separately by
 * `code.api.dynamic.entity.query.JoinQuerySpec`.
 */
class DynamicEntityJoinQueryIntegrationTest extends V600ServerSetup {

  private def run[A](io: cats.effect.IO[A]): A = io.unsafeRunSync()
  private val owner  = "itest-owner"
  private val userA  = "itest-user-a"
  private val userB  = "itest-user-b"
  private val sfx    = java.util.UUID.randomUUID().toString.take(8)
  private val Partner  = s"Partner$sfx"
  private val Contract = s"Contract$sfx"
  private val Deal     = s"Deal$sfx"
  private def idField(entity: String): String = StringHelpers.snakify(entity) + "_id"

  private def createDef(entity: String, propsJson: String, rowLevel: Boolean = false): Unit = {
    val metadata = s"""{"$entity":{"properties":$propsJson}}"""
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(
      DynamicEntityCommons(entity, metadata, None, owner, None, hasPersonalEntity = false,
        hasCommunityAccess = true, useRowLevelAccess = rowLevel)
    ).openOrThrowException(s"failed to create definition for $entity")
  }

  /** Save a record (explicit id so references are controllable). Returns the record's id (= DynamicDataId). */
  private def saveRec(entity: String, fields: (String, JValue)*): String = {
    val id = java.util.UUID.randomUUID().toString
    val body = JObject(JField(idField(entity), JString(id)) :: fields.toList.map { case (k, v) => JField(k, v) })
    DynamicDataProvider.connectorMethodProvider.vend.save(None, entity, body, Some(owner), false)
      .openOrThrowException(s"failed to save $entity record")
    id
  }

  private def queryPartnerIds(plan: QueryPlan, asUser: String): Set[String] = {
    val pidField = idField(Partner)
    PostgresProjectionBackend.query(Partner, None, Some(asUser), isPersonalEntity = false, plan)
      .map(_.flatMap(o => (o \ pidField) match { case JString(s) => Some(s); case _ => None }).toSet)
      .unsafeRunSync()
  }

  private def joinPlan(quantifier: Quantifier, child: String, predicate: List[Filter]): QueryPlan =
    QueryPlan(Nil, List(JoinClause(quantifier, child, "partner_ref", onChild = true, predicate)), Nil, Page.empty)

  private val activeTrue = List(Filter("active", FilterOp.Eq, List("true")))
  private val activeNotTrue = List(Filter("active", FilterOp.Ne, List("true")))

  feature("DE one-hop EXISTS / NOT EXISTS join queries on Postgres") {
    scenario("three meanings of (non-)existence, has-any/none, NULL-safety, and user-scoped ACL") {
      if (!APIUtil.getPropsAsBoolValue("test.projection.postgres", false) || IndexingCapabilities.vendor != IndexingCapabilities.Postgres)
        cancel("Postgres projection integration tests disabled (set test.projection.postgres=true with a Postgres db.url).")

      // --- definitions (Partner must exist before Contract/Deal reference it) ---
      createDef(Partner, s"""{"${idField(Partner)}":{"type":"string"},"tier":{"type":"string","indexed":true}}""")
      createDef(Contract, s"""{"${idField(Contract)}":{"type":"string"},"partner_ref":{"type":"reference:$Partner","indexed":true},"active":{"type":"boolean","indexed":true}}""")
      createDef(Deal, s"""{"${idField(Deal)}":{"type":"string"},"partner_ref":{"type":"reference:$Partner","indexed":true}}""", rowLevel = true)

      // --- write all rows first ---
      val p1 = saveRec(Partner, "tier" -> JString("gold"))
      val p2 = saveRec(Partner, "tier" -> JString("silver"))
      val p3 = saveRec(Partner, "tier" -> JString("bronze")) // no contract at all

      saveRec(Contract, "partner_ref" -> JString(p1), "active" -> JBool(true))   // P1 has an ACTIVE contract
      saveRec(Contract, "partner_ref" -> JString(p1), "active" -> JBool(false))  // P1 also has an inactive one
      saveRec(Contract, "partner_ref" -> JString(p2), "active" -> JBool(false))  // P2 only inactive
      saveRec(Contract, "active" -> JBool(false))                                // orphan: partner_ref absent (NULL)

      val d1 = saveRec(Deal, "partner_ref" -> JString(p1))
      saveRec(Deal, "partner_ref" -> JString(p2)) // d2: granted to nobody
      DynamicDataAccessProvider.provider.vend.grant(d1, userA, canRead = true, canUpdate = false,
        canDelete = false, canGrant = false, entityName = Deal, bankId = None, grantedBy = owner)

      // --- provision AFTER writing, so the backfill populates projections from the blobs.
      //     (This makes the test independent of dynamic_entity.indexing.backend; provisioning's backfill +
      //     PostgresProjectionBackend.query do not consult that prop — only test.projection.postgres gates us.)
      List(Partner, Contract, Deal).foreach(e => run(ProjectionProvisioner.ensureProvisioned(None, e)))

      // 1. EXISTS, predicate active=true  -> partners WITH an active contract
      queryPartnerIds(joinPlan(Quantifier.Exists, Contract, activeTrue), owner) shouldBe Set(p1)

      // 2. NOT EXISTS, predicate active=true -> partners with NO active contract (incl. zero-contract P3)
      queryPartnerIds(joinPlan(Quantifier.NotExists, Contract, activeTrue), owner) shouldBe Set(p2, p3)

      // 3. EXISTS, predicate active!=true -> partners that HAVE a non-active contract (excludes P3)
      queryPartnerIds(joinPlan(Quantifier.Exists, Contract, activeNotTrue), owner) shouldBe Set(p1, p2)

      // 4. EXISTS, no predicate -> partners with ANY contract
      queryPartnerIds(joinPlan(Quantifier.Exists, Contract, Nil), owner) shouldBe Set(p1, p2)

      // 5. NOT EXISTS, no predicate -> partners with NO contract at all.
      //    The orphan contract (NULL partner_ref) must not corrupt this (correlated NOT EXISTS, not NOT IN).
      queryPartnerIds(joinPlan(Quantifier.NotExists, Contract, Nil), owner) shouldBe Set(p3)

      val dealExists = joinPlan(Quantifier.Exists, Deal, Nil)
      // userA can read D1 -> P1 counts; D2 invisible -> P2 excluded.
      queryPartnerIds(dealExists, userA) shouldBe Set(p1)
      // userB has no grants -> no readable deals -> no partner matches.
      queryPartnerIds(dealExists, userB) shouldBe Set.empty[String]
    }
  }
}
