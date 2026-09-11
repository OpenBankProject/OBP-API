package code.api.util

import code.api.util.http4s.RequestScopeConnection
import code.setup.ServerSetup
import doobie.implicits._

/**
 * A request-scoped connection proxy that has outlived its request must not be handed to Doobie.
 *
 * RequestScopeConnection publishes the current request's connection through a
 * TransmittableThreadLocal so work submitted to a Future can keep using the request's
 * transaction. The proxy can outlive the request: a task submitted late in request A runs after
 * A's withBusinessDBTransaction has committed and closed the real connection, and the thread it
 * lands on still carries A's proxy.
 *
 * Lift's side of this already handles it - RequestAwareConnectionManager.newConnection asks the
 * proxy whether it is closed and falls back to a fresh vendor connection when it is. Doobie's
 * side did not: DoobieUtil took the proxy from the thread-local and used it unconditionally, so
 * the query failed with "Connection is closed" from inside HikariCP's closed-connection stub.
 *
 * That surfaced as a 500 on a request that had nothing wrong with it, and only under load, since
 * it needs one request's async tail to overlap the next. In a full parallel test run it produced
 * a handful of failures in a different suite each time, which reads as flakiness rather than as
 * the single missing guard it is.
 *
 * The test builds that state directly instead of waiting for the race: take a pooled connection,
 * wrap it the way a request does, close the underlying connection, publish the proxy, and run a
 * query. Before the fix this throws; after it, DoobieUtil sees a dead proxy and uses the pool.
 */
class DoobieStaleProxyTest extends ServerSetup {

  Feature("Doobie against a request proxy whose connection is gone") {

    Scenario("a stale proxy falls back to the pool instead of throwing") {
      val real = APIUtil.vendor.HikariDatasource.ds.getConnection()
      val proxy = RequestScopeConnection.makeProxy(real)

      // Exactly what withBusinessDBTransaction does at the end of a request. The proxy no-ops
      // close(), so it has to be closed through the real connection - closing the proxy would
      // leave it usable and prove nothing.
      real.close()

      RequestScopeConnection.currentProxy.set(proxy)
      try {
        val answer = DoobieUtil.runQuery(sql"SELECT 1".query[Int].unique)
        answer should equal(1)
      } finally {
        RequestScopeConnection.currentProxy.remove()
      }
    }

    Scenario("a live proxy is still used, so the guard has not disabled request scoping") {
      // The fix must not turn into "always use the pool": queries inside a request have to keep
      // running on the request's connection, or they stop seeing that request's uncommitted
      // writes. Checked by writing on the connection and reading it back through Doobie without
      // committing - only possible if both share one database session.
      val real = APIUtil.vendor.HikariDatasource.ds.getConnection()
      val previousAutoCommit = real.getAutoCommit
      real.setAutoCommit(false)
      val proxy = RequestScopeConnection.makeProxy(real)

      RequestScopeConnection.currentProxy.set(proxy)
      try {
        val st = real.createStatement()
        st.execute("CREATE TABLE IF NOT EXISTS doobie_scope_probe (v INT)")
        st.execute("DELETE FROM doobie_scope_probe")
        st.execute("INSERT INTO doobie_scope_probe VALUES (42)")
        st.close()

        DoobieUtil.runQuery(sql"SELECT v FROM doobie_scope_probe".query[Int].unique) should equal(42)
      } finally {
        RequestScopeConnection.currentProxy.remove()
        real.rollback()
        real.setAutoCommit(previousAutoCommit)
        real.close()
      }
    }
  }
}
