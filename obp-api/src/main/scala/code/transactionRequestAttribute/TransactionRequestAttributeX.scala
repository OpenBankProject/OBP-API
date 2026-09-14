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

package code.transactionRequestAttribute

import code.api.util.APIUtil
import com.openbankproject.commons.model.TransactionRequestAttributeTrait
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

object TransactionRequestAttributeX extends SimpleInjector {

  val transactionRequestAttributeProvider = new Inject(() => buildOne) {}

  def buildOne: TransactionRequestAttributeProvider = MappedTransactionRequestAttributeProvider

  // Helper to get the count out of an option
  def countOfTransactionRequestAttribute(listOpt: Option[List[TransactionRequestAttributeTrait]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }
}
