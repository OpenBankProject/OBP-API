package code.management

import code.bankconnectors.Connector
import code.model.Transaction
import code.management.ImporterAPI._
import net.liftweb.actor.LiftActor
import net.liftweb.common._
import net.liftweb.util.Helpers
import code.util.Helper.MdcLoggable
import code.api.util.ErrorMessages._

object TransactionInserter extends LiftActor with MdcLoggable {

  /**
   * Determines whether two transactions to be imported are considered "identical"
   *
   * Currently this is considered true if the date cleared, the transaction amount,
   * and the name of the other party are the same.
   */
  def isIdentical(t1: ImporterTransaction, t2: ImporterTransaction) : Boolean = {

    val t1Details = t1.obp_transaction.details
    val t1Completed = t1Details.completed
    val t1Amount = t1Details.value.amount
    val t1OtherAccHolder = t1.obp_transaction.other_account.holder

    val t2Details = t2.obp_transaction.details
    val t2Completed = t2Details.completed
    val t2Amount = t2Details.value.amount
    val t2OtherAccHolder = t2.obp_transaction.other_account.holder

    t1Completed == t2Completed &&
    t1Amount == t2Amount &&
    t1OtherAccHolder == t2OtherAccHolder
  }

  /**
   * Inserts a list of identical transactions, ensuring that no duplicates are made.
   *
   * This is done by querying for all existing copies of this transaction,
   * and comparing the number of results to the size of the list of transactions to insert.
   *
   * E.g. If this method receives 3 identical transactions, and only 1 copy exists
   *  in the database, then 2 more should be added.
   *
   *  If this method receives 3 identical transactions, and 3 copies exist in the
   *  database, then 0 more should be added.
   */
  def insertIdenticalTransactions(identicalTransactions : List[ImporterTransaction]) : List[Transaction] = {
    if(identicalTransactions.size == 0){
      Nil
    }else{
      //we don't want to be putting people's transaction info in the logs, so we use an id
      val insertID = Helpers.randomString(10)
      //logger.info("Starting insert operation, id: " + insertID)

      val toMatch = identicalTransactions(0)

      val existingMatches = Connector.connector.vend.getMatchingTransactionCount(
        toMatch.obp_transaction.this_account.bank.national_identifier,
        toMatch.obp_transaction.this_account.number,
        toMatch.obp_transaction.details.value.amount,
        toMatch.obp_transaction.details.completed.`$dt`,
        toMatch.obp_transaction.other_account.holder).openOrThrowException(attemptedToOpenAnEmptyBox)

      //logger.info("Insert operation id " + insertID + " # of existing matches: " + existingMatches)
      val numberToInsert = identicalTransactions.size - existingMatches
      //if(numberToInsert > 0)
        //logger.info("Insert operation id " + insertID + " copies being inserted: " + numberToInsert)

      val results = (1 to numberToInsert).map(_ => Connector.connector.vend.createImportedTransaction(toMatch)).toList

      results.foreach{
        case Failure(msg, _, _) => logger.warn(s"create transaction failed: $msg")
        case Empty => logger.warn("create transaction failed")
        case _ => Unit //do nothing
      }

      results.flatten
    }
  }

  def messageHandler = {
    case TransactionsToInsert(ts : List[ImporterTransaction]) => {

      /**
       * Example:
       *  input : List(A,B,C,C,D,E,E,E,F)
       *  output: List(List(A), List(B), List(C,C), List(D), List(E,E,E), List(F))
       *
       *  This lets us run an insert function on each list of identical transactions that will
       *  avoid inserting duplicates.
       */

      def groupIdenticals(list : List[ImporterTransaction]) : List[List[ImporterTransaction]] = {
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

      val grouped = groupIdenticals(ts)

      val insertedTransactions =
        grouped
          .map(identicals => insertIdenticalTransactions(identicals))
          .flatten

      reply(
        InsertedTransactions(insertedTransactions)
      )
    }
  }

}
