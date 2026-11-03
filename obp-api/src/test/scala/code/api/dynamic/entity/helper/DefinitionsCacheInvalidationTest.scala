/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.dynamic.entity.helper

import code.api.Constant
import code.api.cache.Redis
import code.api.util.http4s.RequestScopeConnection
import code.setup.RedisTestTarget
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A changed dynamic entity definition must reach every instance's definition caches.
 *
 * Two caches hold definitions: the definition lists NewStyle.getDynamicEntities memoizes in Redis
 * (dynamicEntity.cache.ttl.seconds, default 30) and the definitions map each instance builds from them
 * (dynamicEntity.definitions_map.cache.ttl.seconds, default 300). Both follow the dynamic_entity cache
 * namespace version, which a definition change bumps once its transaction has committed. When a change
 * only dropped this instance's map, the rebuild read the list memoized before the change and kept it for
 * the map's whole time-to-live, and other instances never heard of the change at all: a new entity's
 * record endpoints answered 404 for up to five minutes.
 */
class DefinitionsCacheInvalidationTest extends FlatSpec with Matchers with BeforeAndAfterEach {

  private val namespace = "dynamic_entity"

  private def version(): Long = Constant.getCacheNamespaceVersion(namespace)

  private def requireRedis(): Unit =
    RedisTestTarget.requireReachable(Redis.isRedisReady, "the dynamic entity definition cache checks")

  private def runPending(pending: ConcurrentLinkedQueue[() => Unit]): Unit =
    Iterator.continually(pending.poll()).takeWhile(_ != null).foreach(_.apply())

  override def afterEach(): Unit = {
    RequestScopeConnection.currentAfterCommit.remove()
    super.afterEach()
  }

  "The dynamic_entity namespace" should "be listed with the other cache namespaces" in {
    // Listed, it shows on the cache info page and an administrator can bump it by hand.
    Constant.ALL_CACHE_NAMESPACES should contain (namespace)
  }

  "forgetDefinitions" should "bump the dynamic_entity version once the transaction commits" in {
    // Until the commit the change is not visible to other connections: an instance that saw the bump
    // earlier could read the old rows and cache them under the new version.
    requireRedis()
    val pending = new ConcurrentLinkedQueue[() => Unit]()
    RequestScopeConnection.currentAfterCommit.set(pending)
    val before = version()

    DynamicEntityHelper.forgetDefinitions()

    withClue("the version was bumped before the commit ") {
      version() shouldBe before
    }
    runPending(pending)
    withClue("the commit did not bump the dynamic_entity version, so no instance learns of the change ") {
      version() shouldBe before + 1
    }
  }

  it should "bump the dynamic resource-doc caches only once the transaction commits" in {
    // Dynamic entities generate resource docs. Bumped before the commit, another instance could rebuild
    // /resource-docs from the old definitions and keep that for the resource-doc time-to-live (an hour).
    requireRedis()
    val pending = new ConcurrentLinkedQueue[() => Unit]()
    RequestScopeConnection.currentAfterCommit.set(pending)
    val dynamicBefore = Constant.getCacheNamespaceVersion(Constant.RD_DYNAMIC_NAMESPACE)
    val allBefore = Constant.getCacheNamespaceVersion(Constant.RD_ALL_NAMESPACE)

    DynamicEntityHelper.forgetDefinitions()

    withClue("the resource-doc caches were bumped before the commit ") {
      Constant.getCacheNamespaceVersion(Constant.RD_DYNAMIC_NAMESPACE) shouldBe dynamicBefore
      Constant.getCacheNamespaceVersion(Constant.RD_ALL_NAMESPACE) shouldBe allBefore
    }
    runPending(pending)
    withClue("the commit did not bump the resource-doc caches, so /resource-docs keeps the old entities ") {
      Constant.getCacheNamespaceVersion(Constant.RD_DYNAMIC_NAMESPACE) shouldBe dynamicBefore + 1
      Constant.getCacheNamespaceVersion(Constant.RD_ALL_NAMESPACE) shouldBe allBefore + 1
    }
  }

  it should "bump it at once outside a transaction" in {
    requireRedis()
    val before = version()
    DynamicEntityHelper.forgetDefinitions()
    version() shouldBe before + 1
  }

  it should "leave it alone when the transaction rolls back" in {
    requireRedis()
    val pending = new ConcurrentLinkedQueue[() => Unit]()
    RequestScopeConnection.currentAfterCommit.set(pending)
    val before = version()

    DynamicEntityHelper.forgetDefinitions()
    pending.clear() // what a rollback does with the after-commit actions

    version() shouldBe before
  }

  "The definitions map cache" should "keep a built map while the version is unchanged" in {
    val probe = new Probe
    probe.cache.get shouldBe 1
    probe.cache.get shouldBe 1
    probe.builds shouldBe 1
  }

  it should "build it again once the version changes, as when another instance bumps it" in {
    val probe = new Probe
    probe.cache.get
    probe.version += 1
    withClue("a map built under the previous version was served after the bump ") {
      probe.cache.get shouldBe 2
    }
  }

  it should "not keep a map built while the version changed under it" in {
    // The build read the definitions before the change committed; it is kept under the version read
    // before the build, which is already behind, so the next read builds again.
    val probe = new Probe
    probe.duringBuild = () => probe.version += 1
    probe.cache.get shouldBe 1
    probe.duringBuild = () => ()
    probe.cache.get shouldBe 2
  }

  it should "drop the map on forget even though the version did not change (Redis unavailable)" in {
    val probe = new Probe
    probe.cache.get
    probe.cache.forget()
    probe.cache.get shouldBe 2
  }

  it should "not keep a map whose build started before forget" in {
    val probe = new Probe
    probe.duringBuild = () => probe.cache.forget()
    probe.cache.get shouldBe 1
    probe.duringBuild = () => ()
    probe.cache.get shouldBe 2
  }

  it should "keep nothing, and not read the version, when its time-to-live is zero" in {
    val probe = new Probe(ttlMillis = 0L)
    probe.cache.get shouldBe 1
    probe.cache.get shouldBe 2
    probe.versionReads shouldBe 0
  }

  it should "build it again once its time-to-live has passed" in {
    val probe = new Probe(ttlMillis = 50L)
    probe.cache.get
    Thread.sleep(80)
    probe.cache.get shouldBe 2
  }

  /** A map cache whose version and builds the test controls; each build returns its own number. */
  private class Probe(ttlMillis: Long = 60000L) {
    @volatile var version: Long = 1L
    @volatile var builds: Int = 0
    @volatile var versionReads: Int = 0
    @volatile var duringBuild: () => Unit = () => ()
    val cache = new DefinitionsMapCache[Int](
      ttlMillis,
      () => { versionReads += 1; version },
      () => { builds += 1; duringBuild(); builds })
  }
}
