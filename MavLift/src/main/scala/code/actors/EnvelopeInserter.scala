package code.actors

import net.liftweb.actor.LiftActor
import code.model.OBPEnvelope
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
        record.get.asMediatedJValue("authorities",e.id.toString) //authorities view gives the most information. TODO: This obviously needs to be reworked
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