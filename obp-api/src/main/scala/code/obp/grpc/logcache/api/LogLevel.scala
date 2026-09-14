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

package code.obp.grpc.logcache.api

import code.api.cache.RedisLogger

/**
 * Constants matching the proto LogLevel enum. The wire field is an int32
 * varint; these are the values clients will see.
 *
 * See `log_cache.proto` for the canonical definition. Kept in a separate
 * file rather than a scalapb-generated enum class to minimise hand-written
 * boilerplate.
 */
object LogLevel {
  val UNSPECIFIED: Int = 0
  val TRACE: Int = 1
  val DEBUG: Int = 2
  val INFO: Int = 3
  val WARNING: Int = 4
  val ERROR: Int = 5
  val ALL: Int = 6

  def fromRedis(level: RedisLogger.LogLevel.LogLevel): Int = level match {
    case RedisLogger.LogLevel.TRACE   => TRACE
    case RedisLogger.LogLevel.DEBUG   => DEBUG
    case RedisLogger.LogLevel.INFO    => INFO
    case RedisLogger.LogLevel.WARNING => WARNING
    case RedisLogger.LogLevel.ERROR   => ERROR
    case RedisLogger.LogLevel.ALL     => ALL
  }

  def toRedis(level: Int): Option[RedisLogger.LogLevel.LogLevel] = level match {
    case TRACE   => Some(RedisLogger.LogLevel.TRACE)
    case DEBUG   => Some(RedisLogger.LogLevel.DEBUG)
    case INFO    => Some(RedisLogger.LogLevel.INFO)
    case WARNING => Some(RedisLogger.LogLevel.WARNING)
    case ERROR   => Some(RedisLogger.LogLevel.ERROR)
    case ALL     => Some(RedisLogger.LogLevel.ALL)
    case _       => None
  }
}
