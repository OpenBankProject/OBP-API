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

import code.api.cache.{Caching, Redis}
import code.api.util.http4s.RequestScopeConnection
import code.setup.RedisTestTarget
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration._

/**
 * A changed dynamic entity definition must not be served from a list cached before the change.
 *
 * NewStyle.getDynamicEntities memoizes the definition list in Redis for dynamicEntity.cache.ttl.seconds
 * (default 30), and DynamicEntityHelper.buildDefinitionsMap reads that list. When forgetDefinitions only
 * dropped the definitions map, its rebuild read the memoized list from before the change and kept it for
 * dynamicEntity.definitions_map.cache.ttl.seconds (default 300): a new entity's record endpoints answered
 * 404 for up to five minutes. Both caches are off in test mode, so these tests write the memoized entry
 * themselves, under a key carrying the method name the way the real one does (CacheKeyFormatTest).
 */
class DefinitionsCacheInvalidationTest extends FlatSpec with Matchers with BeforeAndAfterEach {

  private def memoizedListKey(): String =
    s"(DefinitionsCacheInvalidationTest,getDynamicEntities,${java.util.UUID.randomUUID().toString.take(8)})"

  private def remember(key: String): Unit =
    Caching.memoizeSyncWithProvider(Some(key))(30.seconds)("definitions as they were before the change")

  private def stored(key: String): Int = Redis.countKeys(s"*$key*")

  private def requireRedis(): Unit =
    RedisTestTarget.requireReachable(Redis.isRedisReady, "the dynamic entity definition cache checks")

  override def afterEach(): Unit = {
    RequestScopeConnection.currentAfterCommit.remove()
    super.afterEach()
  }

  "forgetDefinitions" should "drop the memoized definition lists, so the rebuild reads the current rows" in {
    requireRedis()
    val key = memoizedListKey()
    remember(key)
    stored(key) shouldBe 1

    DynamicEntityHelper.forgetDefinitions()

    withClue("a list memoized before the change survived forgetDefinitions; the definitions map would be " +
             "rebuilt from it and the change would stay invisible for the map's whole time-to-live ") {
      stored(key) shouldBe 0
    }
  }

  it should "drop them again once the transaction commits" in {
    // Inside a request transaction the change is not visible to other connections until commit. A list read
    // between forgetDefinitions and the commit holds the old rows and has to be dropped after the commit.
    requireRedis()
    val pending = new ConcurrentLinkedQueue[() => Unit]()
    RequestScopeConnection.currentAfterCommit.set(pending)

    DynamicEntityHelper.forgetDefinitions()
    val readBeforeCommit = memoizedListKey()
    remember(readBeforeCommit)
    stored(readBeforeCommit) shouldBe 1

    withClue("forgetDefinitions registered nothing to run after the commit ") {
      pending.isEmpty shouldBe false
    }
    Iterator.continually(pending.poll()).takeWhile(_ != null).foreach(_.apply())

    withClue("a list read before the commit survived it ") {
      stored(readBeforeCommit) shouldBe 0
    }
  }
}
