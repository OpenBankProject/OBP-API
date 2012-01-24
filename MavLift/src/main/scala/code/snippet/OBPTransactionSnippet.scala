package code.snippet

import net.liftweb.http.{PaginatorSnippet, StatefulSnippet}
import java.text.SimpleDateFormat
import net.liftweb.http._
import java.util.Calendar
import code.model.OBPTransaction
import code.model.OBPEnvelope
import xml.NodeSeq
import com.mongodb.QueryBuilder
import net.liftweb.mongodb.Limit._
import net.liftweb.mongodb.Skip._
import net.liftweb.util.Helpers._
import net.liftweb.util._
import scala.xml.Text

/**
 * A default implementation of DateTimeConverter that uses (Time)Helpers

object DefaultDateTimeConverter extends DateTimeConverter {
  def formatDateTime(d: Date) = internetDateFormat.format(d)
  def formatDate(d: Date) = dateFormat.format(d)
  /**  Uses Helpers.hourFormat which includes seconds but not time zone */
  def formatTime(d: Date) = hourFormat.format(d)

  def parseDateTime(s: String) = tryo { internetDateFormat.parse(s) }
  def parseDate(s: String) = tryo { dateFormat.parse(s) }
  /** Tries Helpers.hourFormat and Helpers.timeFormat */
  def parseTime(s: String) = tryo{hourFormat.parse(s)} or
tryo{timeFormat.parse(s)}

}
*/

class OBPTransactionSnippet extends StatefulSnippet with PaginatorSnippet[OBPEnvelope] {

  override def count = OBPTransaction.count


  override def itemsPerPage = 5
  //override def page = OBPTransaction.findAll(QueryBuilder.start().get(), Limit(itemsPerPage), Skip(curPage*itemsPerPage))
  override def page = {
      // TODO we need to get Rogue going otherwise its possible to write queries that don't make sense!
      // val qry = QueryBuilder.start("obp_transaction_data_blob").notEquals("simon-says").get

      val qry = QueryBuilder.start().get
      var obp_envelopes = OBPEnvelope.findAll(qry)


      println("before eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee")
        for (envelope <- obp_envelopes) {
      println("here is an envelope")
      println(envelope.id)
      //println(envelope.obp_transaction.get.obp_transaction_date_complete)
      println("nope")
    }


    obp_envelopes
  }

  var dispatch: DispatchIt = {
    case "showAll" => showAll _
    case "paginate" => paginate _
    //case "top" => top _
  }

  def showAll(xhtml: NodeSeq): NodeSeq = {

    // To get records using mongodb record
    //val qry = QueryBuilder.start("obp_transaction_currency").is("EU").get

    //val obp_transactions = OBPTransaction.findAll(qry)

    def present_obp_transaction_new_balance(value: String, consumer: String): String = {
      val show: String =
      if(consumer == "team")
        value
      else if(consumer == "board")
        value
      else if(consumer == "our_network")
        (if (value.startsWith("-") ) "-" else "+")
      else if(consumer == "authorities")
        value
      else if(consumer == "anonymous")
        (if (value.startsWith("-") ) "-" else "+")
      else
        "---"
      show
    }

    def present_obp_transaction_amount(value: String, consumer: String): String = {
      // How the other account is presented to others
      // Use an alias if shy wins
      val show: String =
      if(consumer == "team")
        value
      else if(consumer == "board")
        value
      else if(consumer == "our_network")
        value
      else if(consumer == "authorities")
        value
      else if(consumer == "anonymous")
        (if (value.startsWith("-") ) "-" else "+")
      else
        "---"
      show
    }

    def present_obp_transaction_other_account(value: String, consumer: String): String = {
      // How the other account is presented to others
      // Use an alias if shy wins

      //val result: String

      val outsiders: List[String]	= List("anonymous")

      if (outsiders.contains(value)) {

      }


      if (other_account_is_shy(value, consumer)) other_account_alias(value) else value
    }



    def other_account_is_a_client(value: String): Boolean = {
      // A list of clients
      val clients: List[String]	= List("TXTR GMBH")
      clients.contains(value)
    }

    def other_account_is_a_team_member(value: String): Boolean = {
      // A list of team members
      val team: List[String]	= List("Simon Redfern", "Stefan Bethge", "Eveline M", "Ismail Chaib", "Tim Kleinschmidt", "Niels Hapke", "Yoav Aner")
      team.contains(value)
    }

    def other_account_is_a_supplier(value: String): Boolean = {
      // A list of suppliers
      val suppliers: List[String]	= List("HETZNER ONLINE AG", "Cyberport GmbH", "S-BAHN BERLIN GMBH")
      suppliers.contains(value)
    }

    def other_account_is_shy(value: String, consumer: String): Boolean = {
       // A list of the financially shy (or just plain private)
       val the_shy: List[String]	= List("Tim Kleinschmidt", "Jan Slabiak")
      // A list of those that can look anyway
       val the_gods: List[String]	= List("team", "board", "authorities")
       // No one can be shy in front of the gods
       (the_shy.contains(value) && !(the_gods.contains(value)))
    }

    def other_account_alias(value: String): String = {
      // A map of aliases (used if shyness wins)
      val aliases = Map("Neils Hapke" -> "The Chicken", "Yoav Aner" -> "Software Developer 1", "Jan Slabiak" -> "Alex")
      aliases.getOrElse(value, "Anon")
    }


    def other_account_is_known(value: String): Boolean = {
      // Do we know this other account?
      (other_account_is_a_team_member(value) ||
        other_account_is_a_client(value) ||
        other_account_is_a_supplier(value)
        )
    }


    // xhtml
    val consumer = S.attr("consumer") openOr "no param consumer passed"

    if (consumer == "anonymous") {
    }

    // call anonymous function on every transaction in obp_transactions (i.e. what a map does)
    // the lambda function here replaces some stuff with the data

    import java.text.SimpleDateFormat
    val formatter = new SimpleDateFormat ( "yyyy-MM-dd HH:mm" )
    val date = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2008-05-06 13:29")


    // Given a date in a string like this:
    val string_date = "30.09.11"
    // We can use a date formatter like this:
    val date_formatter = new SimpleDateFormat ( "dd.MM.yy" )

    // Or in place like this.
    val date_x = new SimpleDateFormat("dd.MM.yy").parse(string_date)



    println ("the date is")
    println (formatter.format(date.getTime))
    println ("here is a loop")


    val long_date_formatter = new SimpleDateFormat ( "yyyy-MM-dd HH:mm" )

    val short_date_formatter = new SimpleDateFormat ( "yyyy-MM-dd" )


    // note: blob contains other account right now.

    // page.foreach(p => println(formatter.format(p.obp_transaction_date_complete.value)))


    // right now, dates are stored like this: 30.09.11

    //page.foreach(p => println(p.obp_transaction_date_complete))


    //net.liftweb.record.field.DateTimeField

    println("before env ****************")
/*    for (envelope <- page) {
      println("here is an envelope")
      println(envelope.id)
      println(envelope.obp_transaction.get.obp_transaction_date_complete)
      println(envelope.obp_transaction.get.opb_transaction_other_account)
      println(envelope.obp_transaction.get.obp_transaction_data_blob)
      println("nope")
    }
*/

 /*
date_x match {
  case g2: Graphics2D => g2
  case _ => throw new ClassCastException
}
*/

    val cal = Calendar.getInstance
    cal.set(2009, 10, 2)





/*    val tran = OBPTransaction.createRecord
  .obp_transaction_data_blob("simon-says")
  .obp_transaction_amount("2222222")
  .obp_transaction_date_complete(cal)


    val env = OBPEnvelope.createRecord
    .obp_transaction(tran)
    .save*/


  println( "xxxxx")
  println(formatter.format(cal.getTime))
  print("yyyyyyyy")


    //page.foreach(p => println(date_formatter.format(p.obp_transaction_date_complete)))


    println ("end of loop")


    // "EEE, d MMM yyyy HH:mm:ss Z"





    Text("foo")
    
   /* page.flatMap(obp_envelope => {
      (
        ".obp_transaction_type_en *" #> obp_envelope.obp_transaction.get.obp_transaction_type_en &
        ".obp_transaction_type_de *" #> obp_envelope.obp_transaction.get.obp_transaction_type_de &
        ".obp_transaction_data_blob *" #> present_obp_transaction_other_account(obp_envelope.obp_transaction.get.obp_transaction_data_blob.value, consumer) &
        ".obp_transaction_new_balance *" #> present_obp_transaction_new_balance(obp_envelope.obp_transaction.get.obp_transaction_new_balance.value, consumer) &
        ".obp_transaction_amount *" #> present_obp_transaction_amount(obp_envelope.obp_transaction.get.obp_transaction_amount.value, consumer) &
        //".obp_transaction_currency *" #> obp_transaction.obp_transaction_currency &
        ".obp_transaction_currency *" #> "EUR" &
        ".obp_transaction_date_start *" #> (short_date_formatter.format(obp_envelope.obp_transaction.get.obp_transaction_date_start.value.getTime())) &
        //".obp_transaction_date_complete *" #> OBPTransaction.formats.dateFormat.format(obp_transaction.obp_transaction_date_complete.value.getTime()) &
          ".obp_transaction_date_complete *" #> short_date_formatter.format(obp_envelope.obp_transaction.get.obp_transaction_date_complete.value.getTime()) &
          ".opb_transaction_other_account *" #> present_obp_transaction_other_account(obp_envelope.obp_transaction.get.opb_transaction_other_account.value, consumer)).apply(xhtml)
      }
    )*/
  }



   /*

  def showAll(xhtml: NodeSeq): NodeSeq = {
  page.flatMap(OBPTransaction => {
    (".opb_transaction_other_account *" #> OBPTransaction.opb_transaction_other_account).apply(xhtml)
  })

  */




}

