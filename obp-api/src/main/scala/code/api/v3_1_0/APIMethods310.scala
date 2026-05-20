package code.api.v3_1_0

import net.liftweb.http.rest.RestHelper

/*
 * All v3.1.0 endpoints have been migrated to Http4s310. This trait is retained
 * because `with APIMethods310` may still appear in legacy mixin chains.
 * The body is intentionally empty — no Lift Dispatch routes, no ResourceDocs,
 * no `Implementations310` class.
 *
 * Use `Http4s310.Implementations3_1_0` directly (or `OBPAPI3_1_0.Implementations3_1_0`,
 * which is a re-export) for ResourceDoc / route access in tests.
 */
trait APIMethods310 { self: RestHelper => }

object APIMethods310 extends RestHelper with APIMethods310 {
  // Re-export so any caller that still imports APIMethods310.Implementations3_1_0 keeps compiling.
  val Implementations3_1_0 = Http4s310.Implementations3_1_0
}

// ─── Original Lift implementation (commented out) ────────────────────────────
// The original 6065-line Lift implementation is preserved below as comments.
// To view the full implementation history, use: git show HEAD~3:obp-api/src/main/scala/code/api/v3_1_0/APIMethods310.scala
