/** 
Open Bank Project - Transparency / Social Finance Web Application
Copyright (C) 2011, 2012, TESOBE / Music Pictures Ltd

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
package code.model.implementedTraits

import code.model.dataAccess.{OBPEnvelope,OBPTransaction,OtherAccount}
import code.model.traits.{Transaction,BankAccount,OtherBankAccount, TransactionMetadata}
import scala.math.BigDecimal
import java.util.Date
import scala.collection.immutable.List
import net.liftweb.common.Loggable
import net.liftweb.common.Box
import code.model.traits.Comment

class TransactionImpl(id_ : String, var _thisAccount : BankAccount = null, otherAccount_ : OtherBankAccount, 
  metadata_ : TransactionMetadata, transactionType_ : String, amount_ : BigDecimal, currency_ : String,
  label_ : Option[String], startDate_ : Date, finishDate_ : Date, balance_ :  BigDecimal) extends Transaction with Loggable {

  def id = id_
  def thisAccount = _thisAccount
  def thisAccount_= (newThisAccount : BankAccount) = _thisAccount = newThisAccount
  def otherAccount = otherAccount_
  def metadata = metadata_
  def transactionType = transactionType_
  def amount = amount_
  def currency = currency_
  def label = label_
  def startDate = startDate_
  def finishDate = finishDate_
  def balance = balance_
}