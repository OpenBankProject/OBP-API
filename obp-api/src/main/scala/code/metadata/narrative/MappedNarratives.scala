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

package code.metadata.narrative

import code.util.{AccountIdString, UUIDString}
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId}
import net.liftweb.common.Full
import net.liftweb.mapper._

object MappedNarratives extends Narrative {

  private def getMappedNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId) = {
    MappedNarrative.find(By(MappedNarrative.bank, bankId.value),
      By(MappedNarrative.account, accountId.value),
      By(MappedNarrative.transaction, transactionId.value))
  }

  override def getNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(): String = {
    val found = getMappedNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)

    found.map(_.narrative.get).getOrElse("")
  }

  override def setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(narrative: String): Boolean = {

    val existing = getMappedNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)

    if(narrative.isEmpty) {
      //if the new narrative is empty, we can just delete the existing one
      existing.map(_.delete_!).getOrElse(false)
    } else {
      val mappedNarrative = existing match {
        case Full(n) => n
        case _ => MappedNarrative.create
          .bank(bankId.value)
          .account(accountId.value)
          .transaction(transactionId.value)
      }
      mappedNarrative.narrative(narrative).save
    }
  }

  override def bulkDeleteNarrativeOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean = {
    MappedNarrative.bulkDelete_!!(
      By(MappedNarrative.bank, bankId.value),
      By(MappedNarrative.account, accountId.value),
      By(MappedNarrative.transaction, transactionId.value)
    )
  }
  override def bulkDeleteNarratives(bankId: BankId, accountId: AccountId): Boolean = {
      MappedNarrative.bulkDelete_!!(
        By(MappedNarrative.bank, bankId.value),
        By(MappedNarrative.account, accountId.value))
  }

}

class MappedNarrative extends LongKeyedMapper[MappedNarrative] with IdPK with CreatedUpdated {
  def getSingleton = MappedNarrative

  object bank extends UUIDString(this)
  object account extends AccountIdString(this)
  object transaction extends UUIDString(this)

  object narrative extends MappedString(this, 2000)
}

object MappedNarrative extends MappedNarrative with LongKeyedMetaMapper[MappedNarrative] {
  override def dbIndexes = Index(bank, account, transaction) :: super.dbIndexes
}