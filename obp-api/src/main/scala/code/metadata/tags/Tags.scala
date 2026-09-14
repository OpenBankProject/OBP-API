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

package code.metadata.tags

import java.util.Date

import code.api.util.APIUtil
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object Tags  extends SimpleInjector {

  val tags = new Inject(() => buildOne) {}

  def buildOne: Tags = MappedTags
  
}

trait Tags {

  def getTags(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionTag]
  def getTagsOnAccount(bankId : BankId, accountId : AccountId)(viewId : ViewId) : List[TransactionTag]
  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag]
  def addTagOnAccount(bankId : BankId, accountId : AccountId)(userId: UserPrimaryKey, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag]
  //TODO: viewId? should tagId always be unique -> in that case bankId, accountId, and transactionId would not be required
  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Boolean]
  def deleteTagOnAccount(bankId : BankId, accountId : AccountId)(tagId : String) : Box[Boolean]
  def bulkDeleteTags(bankId: BankId, accountId: AccountId) : Boolean
  def bulkDeleteTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId) : Boolean
  
}

