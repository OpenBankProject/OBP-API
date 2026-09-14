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

package code.metadata.comments

import java.util.Date

import code.api.util.APIUtil
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object Comments extends SimpleInjector {

  val comments = new Inject(() => buildOne) {}

  def buildOne: Comments = MappedComments
  
}

trait Comments {
  
  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment]
  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, text : String, datePosted : Date) : Box[Comment]
  //TODO: should commentId be unique among all comments, removing the need for the other parameters?
  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Boolean]
  def bulkDeleteComments(bankId: BankId, accountId: AccountId) : Boolean
  def bulkDeleteCommentsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId) : Boolean
  
}

