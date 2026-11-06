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

package code.api.berlin.group

import code.api.util.APIUtil
import com.openbankproject.commons.util.ApiVersion.berlinGroupV13
import com.openbankproject.commons.util.ScannedApiVersion
import net.liftweb.common.Full

object ConstantsBG {
  val berlinGroupVersion1: ScannedApiVersion = APIUtil.getPropsValue("berlin_group_version_1_canonical_path") match {
    case Full(props) => berlinGroupV13.copy(apiShortVersion = props)
    case _ => berlinGroupV13
  }
  val berlinGroupVersion2: ScannedApiVersion = ScannedApiVersion("berlin-group", "BG", "v2")
  object SigningBasketsStatus extends Enumeration {
    type SigningBasketsStatus = Value
    // Only the codes
    // 1) RCVD (Received),
    // 2) PATC (PartiallyAcceptedTechnical Correct) The payment initiation needs multiple authentications, where some but not yet all have been performed. Syntactical and semantical validations are successful.,
    // 3) ACTC (AcceptedTechnicalValidation) ,
    // 4) CANC (Cancelled) and
    // 5) RJCT (Rejected) are supported for signing baskets.
    val RCVD, PATC, ACTC, CANC, RJCT = Value
  }
}
