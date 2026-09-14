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

package code.model.dataAccess.internalMapping

import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{BankId, AccountId}
import net.liftweb.common._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo


object MappedAccountIdMappingProvider extends AccountIdMappingProvider with MdcLoggable
{

  override def getOrCreateAccountId(
    accountPlainTextReference: String
  ): Box[AccountId] =
  {

    val mappedAccountIdMapping = AccountIdMapping.find(
      By(AccountIdMapping.mAccountPlainTextReference, accountPlainTextReference)
    )

    mappedAccountIdMapping match
    {
      case Full(vImpl) =>
      {
        logger.debug(s"getOrCreateAccountId --> the mappedAccountIdMapping has been existing in server !")
        mappedAccountIdMapping.map(_.accountId)
      }
      case Empty =>
        tryo {
          AccountIdMapping
            .create
            .mAccountPlainTextReference(accountPlainTextReference)
            .saveMe
        } match {
          case Full(m) =>
            logger.debug(s"getOrCreateAccountId--> create mappedAccountIdMapping : $m")
            Full(m.accountId)
          case Failure(_, _, _) =>
            // UniqueIndex violation from concurrent insert — re-fetch the committed row
            AccountIdMapping.find(
              By(AccountIdMapping.mAccountPlainTextReference, accountPlainTextReference)
            ).map(_.accountId)
          case other => other.map(_.accountId)
        }
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }


  override def getAccountPlainTextReference(accountId: AccountId) = {
    AccountIdMapping.find(
      By(AccountIdMapping.mAccountId, accountId.value),
    ).map(_.accountPlainTextReference)
  }
}

