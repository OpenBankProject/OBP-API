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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)

 */
package code.api.v3_0_0.custom

import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ReflectUtils

import scala.collection.immutable.List

object JSONFactoryCustom300 {

  lazy val allFields: Seq[AnyRef] =
    ReflectUtils.getValues(this, List(nameOf(allFields)))
    .filter(it => it != null && it.isInstanceOf[AnyRef])
    .map(_.asInstanceOf[AnyRef])
}