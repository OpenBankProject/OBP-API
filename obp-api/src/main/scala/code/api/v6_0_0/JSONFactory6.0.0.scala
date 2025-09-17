/**
  * Open Bank Project - API
  * Copyright (C) 2011-2019, TESOBE GmbH
  * *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Affero General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  * *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Affero General Public License for more details.
  * *
  * You should have received a copy of the GNU Affero General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  * *
  * Email: contact@tesobe.com
  * TESOBE GmbH
  * Osloerstrasse 16/17
  * Berlin 13359, Germany
  * *
  * This product includes software developed at
  * TESOBE (http://www.tesobe.com/)
  *
  */
package code.api.v6_0_0

import code.api.util.APIUtil.stringOrNull
import code.api.util._
import code.api.v2_0_0.{EntitlementJSONs, JSONFactory200}
import code.api.v3_0_0.{UserJsonV300, ViewJSON300, ViewsJSON300}
import code.entitlement.Entitlement
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._

case class CardanoPaymentJsonV600(
  address: String,
  amount: CardanoAmountJsonV600,
  assets: Option[List[CardanoAssetJsonV600]] = None
)

case class CardanoAmountJsonV600(
  quantity: Long,
  unit: String // "lovelace"
)

case class CardanoAssetJsonV600(
  policy_id: String,
  asset_name: String,
  quantity: Long
)

case class CardanoMetadataStringJsonV600(
  string: String
)

case class TransactionRequestBodyCardanoJsonV600(
  to: CardanoPaymentJsonV600,
  value: AmountOfMoneyJsonV121,
  passphrase: String, 
  description: String,
  metadata: Option[Map[String, CardanoMetadataStringJsonV600]] = None
) extends TransactionRequestCommonBodyJSON

case class UserJsonV600(
                         user_id: String,
                         email : String,
                         provider_id: String,
                         provider : String,
                         username : String,
                         entitlements : EntitlementJSONs,
                         views: Option[ViewsJSON300],
                         on_behalf_of: Option[UserJsonV300]
                       )

case class UserV600(user: User, entitlements: List[Entitlement], views: Option[Permission])
case class UsersJsonV600(current_user: UserV600, on_behalf_of_user: UserV600)

object JSONFactory600 extends CustomJsonFormats with MdcLoggable{
  def createUserInfoJSON(current_user: UserV600, onBehalfOfUser: Option[UserV600]): UserJsonV600 = {
    UserJsonV600(
      user_id = current_user.user.userId,
      email = current_user.user.emailAddress,
      username = stringOrNull(current_user.user.name),
      provider_id = current_user.user.idGivenByProvider,
      provider = stringOrNull(current_user.user.provider),
      entitlements = JSONFactory200.createEntitlementJSONs(current_user.entitlements),
      views = current_user.views.map(y => ViewsJSON300(y.views.map((v => ViewJSON300(v.bankId.value, v.accountId.value, v.viewId.value))))),
      on_behalf_of = onBehalfOfUser.map { obu =>
        UserJsonV300(
          user_id = obu.user.userId,
          email = obu.user.emailAddress,
          username = stringOrNull(obu.user.name),
          provider_id = obu.user.idGivenByProvider,
          provider = stringOrNull(obu.user.provider),
          entitlements = JSONFactory200.createEntitlementJSONs(obu.entitlements),
          views = obu.views.map(y => ViewsJSON300(y.views.map((v => ViewJSON300(v.bankId.value, v.accountId.value, v.viewId.value)))))
        )
      }
    )
  }
}