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

package code.api.util

import java.util

// Introduced in order to replace library: https://mvnrepository.com/artifact/org.apache.commons/commons-collections4/4.4
// which contains vulnerabilities from dependencies:
// CVE-2020-15250
object CommonUtil {
  object Collections {
    def isEmpty(coll: util.Collection[_]): Boolean = coll == null || coll.isEmpty
    def isNotEmpty(coll: util.Collection[_]): Boolean = !isEmpty(coll)
  }
  object Map {
    def isEmpty(map: java.util.Map[_, _]): Boolean = map == null || map.isEmpty
    def isNotEmpty(map: java.util.Map[_, _]): Boolean = !isEmpty(map)
  }
}
