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

package code.model.traits
import scala.math.BigDecimal
import java.util.Date

trait Transaction {

  def id : String
 
  var thisAccount : BankAccount

  def otherAccount : OtherBankAccount
  
  def metadata : TransactionMetadata
  
  //E.g. cash withdrawal, electronic payment, etc.
  def transactionType : String
  
  //TODO: Check if BigDecimal is an appropriate data type
  def amount : BigDecimal
  
  //ISO 4217, e.g. EUR, GBP, USD, etc.
  def currency : String
  
  // Bank provided comment
  def label : Option[String]
  
  // The date the transaction was initiated
  def startDate : Date
  
  // The date when the money finished changing hands
  def finishDate : Date
  
  //the new balance for the bank account
  //TODO : Rethink this
  def balance : BigDecimal
  
}