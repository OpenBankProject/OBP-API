/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
package code.api

import code.util.Helper.MdcLoggable
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction


object aliveCheck extends RestHelper with MdcLoggable {
  serve {
    case Req("alive" :: Nil, _, GetRequest) =>
      JsonResponse(Extraction.decompose(true), Nil, Nil, 200)   
  }
}
