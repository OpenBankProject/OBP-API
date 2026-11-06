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

package code.api.ResourceDocs1_4_0

import code.setup.ServerSetupWithTestData

trait ResourceDocsV140ServerSetup extends ServerSetupWithTestData {

  def ResourceDocsV1_4Request = baseRequest / "obp" / "v1.4.0"
  def ResourceDocsV2_0Request = baseRequest / "obp" / "v2.0.0"
  def ResourceDocsV2_1Request = baseRequest / "obp" / "v2.1.0"
  def ResourceDocsV2_2Request = baseRequest / "obp" / "v2.2.0"
  def ResourceDocsV3_0Request = baseRequest / "obp" / "v3.0.0"
  def ResourceDocsV3_1Request = baseRequest / "obp" / "v3.1.0"
  def ResourceDocsV4_0Request = baseRequest / "obp" / "v4.0.0"
  def ResourceDocsV5_0Request = baseRequest / "obp" / "v5.0.0"
  def ResourceDocsV5_1Request = baseRequest / "obp" / "v5.1.0"
  def ResourceDocsV6_0Request = baseRequest / "obp" / "v6.0.0"

}
