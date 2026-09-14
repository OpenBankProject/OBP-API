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

package code.chat

import code.api.util.APIUtil

import scala.util.Try

/**
 * Content policy for chat messages, applied wherever message content enters
 * (create, edit, thread reply) — see also ChatLinkPolicy for the link-host
 * whitelist.
 */
object ChatContentPolicy {

  /** Maximum accepted content length in characters (prop chat.max_message_length). */
  def maxContentLength: Int =
    APIUtil.getPropsValue("chat.max_message_length")
      .flatMap(v => Try(v.trim.toInt).toOption.filter(_ > 0))
      .getOrElse(10000)

  // Character class shared with SignalContentPolicy — see
  // code.util.DangerousCharacters for the rationale and the strip-vs-reject
  // asymmetry between chat and signal.
  def stripDangerousCharacters(content: String): String =
    code.util.DangerousCharacters.strip(content)
}
