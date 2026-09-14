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

package code.api.v2_0_0

import code.setup.{APIResponse, ServerSetupWithTestData}

trait V200ServerSetup extends ServerSetupWithTestData {

  def v1_4Request = baseRequest / "obp" / "v1.4.0"
  def v2_0Request = baseRequest / "obp" / "v2.0.0"

  def getPublicAccountsForAllBanks() : APIResponse= {
    val request = v2_0Request / "accounts" / "public"
    makeGetRequest(request)
  }

}