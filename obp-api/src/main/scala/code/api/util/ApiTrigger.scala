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

package code.api.util

sealed trait ApiTrigger{
  override def toString() = getClass().getSimpleName
}

object ApiTrigger {

  case class OnCreateTransaction() extends ApiTrigger
  lazy val onCreateTransaction = OnCreateTransaction()
  
  case class OnBalanceChange() extends ApiTrigger
  lazy val onBalanceChange = OnBalanceChange()

  case class OnCreditTransaction() extends ApiTrigger
  lazy val onCreditTransaction = OnCreditTransaction()

  case class OnDebitTransaction() extends ApiTrigger
  lazy val onDebitTransaction = OnDebitTransaction()

  private val triggers = onBalanceChange :: onCreditTransaction :: onDebitTransaction :: Nil

  lazy val triggersMappedToClasses = triggers.map(_.getClass)

  def valueOf(value: String): ApiTrigger = {
    triggers.filter(_.toString == value) match {
      case x :: Nil => x // We find exactly one Trigger
      case x :: _ => throw new Exception("Duplicated trigger: " + x) // We find more than one Trigger
      case _ => throw new IllegalArgumentException("Incorrect ApiTrigger value: " + value) // There is no Trigger
    }
  }

  def availableTriggers: List[String] = triggers.map(_.toString)

}

