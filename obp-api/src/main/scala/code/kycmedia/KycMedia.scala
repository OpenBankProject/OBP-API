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

package code.kycmedias

import java.util.Date

import com.openbankproject.commons.model.{KycMedia, User}
import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box


object KycMedias extends SimpleInjector {

  val kycMediaProvider = new Inject(() => buildOne) {}

  def buildOne: KycMediaProvider = MappedKycMediasProvider

}

trait KycMediaProvider {

  def getKycMedias(customerId: String) : List[KycMedia]

  def addKycMedias(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String) : Box[KycMedia]

}
