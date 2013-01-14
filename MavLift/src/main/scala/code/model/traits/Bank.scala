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
import net.liftweb.common.Box
import code.model.dataAccess.LocalStorage
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST.JArray

trait Bank 
{
	def id : String
	def name : String
	def permalink : String
	def accounts : Set[BankAccount]
	
	def detailedJson : JObject = {
	  ("name" -> name) ~
	  ("website" -> "") ~
	  ("email" -> "")
	}
	
	def toJson : JObject = {
	  ("alias" -> permalink) ~
      ("name" -> name) ~
      ("logo" -> "") ~
      ("links" -> linkJson)
	}
	
	def linkJson : JObject = {
      ("rel" -> "bank") ~
      ("href" -> {"/" + permalink + "/bank"}) ~
      ("method" -> "GET") ~
      ("title" -> {"Get information about the bank identified by " + permalink})
    }
}

object Bank {
  def apply(bankPermalink: String) : Box[Bank] = LocalStorage.getBank(bankPermalink)
  
  def all : List[Bank] = LocalStorage.allBanks
  
  def toJson(banks: Seq[Bank]) : JArray = 
    banks.map(bank => bank.toJson)
  
}