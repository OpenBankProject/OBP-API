/**
Open Bank Project - API
Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.api.v2_0_0

import java.util.Date
import net.liftweb.common.{Box, Full}
import code.model._





// New in 2.0.0

class ViewBasicJSON(
  val id: String,
  val short_name: String,
  val is_public: Boolean
)

case class AccountsBasicJSON(
  accounts : List[AccountBasicJSON]
)

case class AccountBasicJSON(
  id : String,
  label : String,
  views_available : List[ViewBasicJSON],
  bank_id : String
)


object JSONFactory{


  // New in 2.0.0

  def createViewBasicJSON(view : View) : ViewBasicJSON = {
    val alias =
      if(view.usePublicAliasIfOneExists)
        "public"
      else if(view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

    new ViewBasicJSON(
      id = view.viewId.value,
      short_name = stringOrNull(view.name),
      is_public = view.isPublic
    )
  }


  def createAccountBasicJSON(account : BankAccount, viewsBasicAvailable : List[ViewBasicJSON] ) : AccountBasicJSON = {
    new AccountBasicJSON(
      account.accountId.value,
      stringOrNull(account.label),
      viewsBasicAvailable,
      account.bankId.value
    )
  }
  
  // From 1.2.1

  def stringOrNull(text : String) =
    if(text == null || text.isEmpty)
      null
    else
      text



}