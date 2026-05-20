package code.api.v3_0_0

import net.liftweb.http.rest.RestHelper

/*
 * All v3.0.0 endpoints have been migrated to Http4s300. This trait is retained
 * because `with APIMethods300` may still appear in legacy mixin chains.
 * The body is intentionally empty — no Lift Dispatch routes, no ResourceDocs,
 * no `Implementations3_0_0` class.
 *
 * Use `Http4s300.Implementations3_0_0` directly (or `OBPAPI3_0_0.Implementations3_0_0`,
 * which is a re-export) for ResourceDoc / route access in tests.
 */
trait APIMethods300 { self: RestHelper => }

object APIMethods300 extends RestHelper with APIMethods300 {
  // Re-export so callers using APIMethods300.Implementations3_0_0
  // (e.g. v3_1_0/ConsentTest, v5_1_0/ConsentObpTest) continue to compile.
  val Implementations3_0_0 = Http4s300.Implementations3_0_0
}

// ─── Original Lift implementation (commented out) ────────────────────────────
// The original 2526-line Lift implementation is preserved below as comments.
// To view the full implementation history, use: git show HEAD~1:obp-api/src/main/scala/code/api/v3_0_0/APIMethods300.scala
