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

package code.api.v6_0_0

import code.setup.{DefaultUsers, ServerSetupWithTestData}
import com.openbankproject.commons.util.ApiShortVersions
import code.setup.OBPReq

trait V600ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v4_0_0_Request: OBPReq = baseRequest / "obp" / "v4.0.0"
  def v5_0_0_Request: OBPReq = baseRequest / "obp" / "v5.0.0"
  def v5_1_0_Request: OBPReq = baseRequest / "obp" / "v5.1.0"
  def v6_0_0_Request: OBPReq = baseRequest / "obp" / "v6.0.0"
  def dynamicEntity_Request: OBPReq = baseRequest / "obp" / ApiShortVersions.`dynamic-entity`.toString

}