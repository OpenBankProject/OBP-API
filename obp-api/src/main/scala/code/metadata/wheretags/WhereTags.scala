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

package code.metadata.wheretags

import java.util.Date

import code.api.util.APIUtil
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object WhereTags  extends SimpleInjector {

  val whereTags = new Inject(() => buildOne) {}

  def buildOne: WhereTags = MapperWhereTags

}

trait WhereTags {

  //TODO: it probably makes more sense for this to return Box[GeoTag]. Leaving it as a Boolean for now...
  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                 (userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) : Boolean

  //TODO: would be nicer to change this to return Box[Unit] like in e.g. comments. Or perhaps change the way the other ones work
  //instead, with the end effect of keeping them consistent. Leaving it as a Boolean for now...
  def deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Boolean

  def getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Box[GeoTag]

  def bulkDeleteWhereTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId) : Boolean
  
  def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId) : Boolean

}
