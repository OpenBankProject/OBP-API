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
package code.actors

import net.liftweb.actor.LiftActor
import code.model.dataAccess.OBPEnvelope
import net.liftweb.json.JObject
import com.mongodb.QueryBuilder
import net.liftweb.common.Loggable
import net.liftweb.util.Helpers

object EnvelopeInserter extends LiftActor with Loggable{
  
  /**
   * Determines whether two obp envelopes are considered "identical"
   * 
   * Currently this is considered true if the date cleared, the transaction amount, 
   * and the name of the other party are the same.
   */
  def isIdentical(e1: OBPEnvelope, e2: OBPEnvelope) : Boolean = {
    val t1 = e1.obp_transaction.get
    val t2 = e2.obp_transaction.get
    
    t1.details.get.completed.get.equals(t2.details.get.completed.get) &&
    t1.details.get.value.get.amount.get.equals(t2.details.get.value.get.amount.get) &&
    t1.other_account.get.holder.get.equals(t2.other_account.get.holder.get)
  
  }
  
  /**
   * Inserts a list of identical envelopes, ensuring that no duplicates are made.
   * 
   * This is done by querying for all existing copies of this identical envelope,
   * and comparing the number of results to the size of the envelopes list.
   * 
   * E.g. If this method receives 3 identical envelopes, and only 1 copy exists
   *  in the database, then 2 more should be added.
   *  
   *  If this method receives 3 identical envelopes, and 3 copies exist in the
   *  database, then 0 more should be added.
   */
  def insert(identicalEnvelopes : List[OBPEnvelope]) : List[JObject] = {
    if(identicalEnvelopes.size == 0){
      Nil
    }else{
      //we don't want to be putting people's transaction info in the logs, so we use an id
      val insertID = Helpers.randomString(10)
      logger.info("Starting insert operation, id: " + insertID)
      
      val toMatch = identicalEnvelopes(0)
      val qry = QueryBuilder.start("obp_transaction.details.value.amount").is(toMatch.obp_transaction.get.details.get.value.get.amount.get.toString)
      	.put("obp_transaction.other_account.holder").is(toMatch.obp_transaction.get.other_account.get.holder.get).get
    
      //TODO: Figure out how to add date queries to the query builder, OR, preferably, use Rogue
      val partialMatches = OBPEnvelope.findAll(qry)
      logger.info("Insert operation id " + insertID + " # of partial matches: " + partialMatches.size)
      
      val matches = partialMatches.filter(e =>{
       e.obp_transaction.get.details.get.completed.get.equals(toMatch.obp_transaction.get.details.get.completed.get)
      })
      logger.info("Insert operation id " + insertID + " # of full matches: " + matches.size)
      
      val copiesToInsert = identicalEnvelopes drop matches.size
      logger.info("Insert operation id " + insertID + " copies being inserted: " + copiesToInsert.size)
      
      copiesToInsert.map(e => {
        val record = e.saveTheRecord()
        record.get.whenAddedJson //TODO: Standardise this format with API "get" format?
      })
    }
  }
  
  def messageHandler = {
    case envelopes : List[OBPEnvelope] => {
      
      /**
       * Example:
       * 	input : List(A,B,C,C,D,E,E,E,F)
       * 	output: List(List(A), List(B), List(C,C), List(D), List(E,E,E), List(F))
       * 
       *  This lets us run an insert function on each list of identical transactions that will
       *  avoid inserting duplicates.
       */

      def groupIdenticals(list : List[OBPEnvelope]) : List[List[OBPEnvelope]] = {
        list match{
          case Nil => Nil
          case h::Nil => List(list)
          case h::t => {
            //transactions that are identical to the head of the list
            val matches = list.filter(isIdentical(h, _))
            List(matches) ++ groupIdenticals(list diff matches)
          }
        }
      }
      
      val grouped = groupIdenticals(envelopes)
      
      reply(grouped.map(identicals => {
        insert(identicals)
      }).flatten)
    }
  }
  
}