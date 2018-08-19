/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
 */

package code.api.APIBuilder

import code.api.util.APIUtil

case class Books(
  author: String = """Chinua Achebe""",
  pages: Int = 209,
  points: Double = 1.3
)

case class RootInterface(books: List[Books] = List(Books()))

object JsonFactory_APIBuilder
{
  
  val books = Books()
  val rootInterface = RootInterface(List(books))
  
  val allFields = for (
    v <- this.getClass.getDeclaredFields; 
    if APIUtil.notExstingBaseClass(v.getName())
  ) yield
    {
      v.setAccessible(true)
      v.get(this)
    }
}