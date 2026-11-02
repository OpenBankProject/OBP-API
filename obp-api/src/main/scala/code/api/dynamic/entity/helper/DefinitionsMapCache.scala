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

/**
 * Keeps one built value (the dynamic entity definitions map) for up to `ttlMillis`, and only while
 * `currentVersion` still returns the version it was built under.
 *
 * The version is read before building, so a value built while the version changed is kept under the
 * old version and dropped on the next read. [[forget]] drops the value on this instance alone, for when
 * the version cannot change (Redis unavailable); a build that started before it is not kept after it.
 * Nothing is kept, and the version is not read, when `ttlMillis` is not positive.
 */
private[helper] final class DefinitionsMapCache[M](ttlMillis: Long, currentVersion: () => Long, build: () => M) {

  // (built at, version it was built under, value)
  @volatile private var entry: Option[(Long, Long, M)] = None
  // Replaced by forget; a build keeps its value only if the token it started under is still current.
  @volatile private var token: AnyRef = new Object

  def get: M =
    if (ttlMillis <= 0) build()
    else {
      val now = System.currentTimeMillis()
      val version = currentVersion()
      entry match {
        case Some((builtAt, builtUnder, value)) if builtUnder == version && now - builtAt < ttlMillis => value
        case _ =>
          val startedUnder = token
          val value = build()
          if (token eq startedUnder) entry = Some((now, version, value))
          value
      }
    }

  def forget(): Unit = {
    token = new Object
    entry = None
  }

  /** The value kept now, if any, whatever its version. */
  def kept: Option[M] = entry.map(_._3)
}
