/** 
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License.      

Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
    by 
    Simon Redfern : simon AT tesobe DOT com
    Everett Sochowski: everett AT tesobe DOT com
    Benali Ayoub : ayoub AT tesobe DOT com

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