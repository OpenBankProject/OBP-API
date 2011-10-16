package com.tesobe.something{
  // I am in another package
  object SimonSays {
    println("here we are in SimonSays")
    def do_something() : String = {
      println("here we are in do_something")
      "ok"
    }
  }
}





package com.tesobe.utils {

import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST._  // this has render in it.


import net.liftweb.json._  // Yep everything




import net.liftweb.common.Full

import net.liftweb.mongodb._
import net.liftweb.json.JsonAST.JString


// import com.mongodb.casbah.commons._
import com.mongodb.casbah.Imports._

//import com.rabbitmq.client._
//import net.liftweb.amqp._


/*
import com.rabbitmq.client.Address
import akka.amqp.AMQP.{ProducerParameters, ExchangeParameters}
import akka.amqp.{Topic, Message, AMQP}
*/

// used to construct type
import com.tesobe.something._



import net.liftweb.mongodb._

import _root_.java.math.MathContext
import net.liftweb.mongodb.record._
import net.liftweb.mongodb.record.field._
import net.liftweb.record.field._
import net.liftweb.record._
import org.bson.types._
import org.joda.time.{DateTime, DateTimeZone}

//import com.foursquare.rogue
import com.foursquare.rogue.Rogue._


import java.util.regex.Pattern


//import org.junit._
//import org.specs.SpecsMatchers

import com.foursquare.rogue.MongoHelpers._


class Location private () extends BsonRecord[Location] {
  def meta = Location

  object longitude extends net.liftweb.record.field.IntField(this)
  object latitude extends net.liftweb.record.field.IntField(this)

}
object Location extends Location with BsonMetaRecord[Location]



class OBPTransaction private() extends MongoRecord[OBPTransaction] with ObjectIdPk[OBPTransaction] {
  def meta = OBPTransaction


object obp_transaction_date_start extends net.liftweb.record.field.DateTimeField(this)
object obp_transaction_date_complete extends net.liftweb.record.field.DateTimeField(this)
object obp_transaction_type_en extends net.liftweb.record.field.StringField(this, 255)
object obp_transaction_type_de extends net.liftweb.record.field.StringField(this, 255)
object obp_transaction_data_blob extends net.liftweb.record.field.StringField(this, 999999)
object opb_transaction_other_account extends net.liftweb.record.field.StringField(this, 255)

object obp_transaction_currency extends net.liftweb.record.field.StringField(this, 10)
object obp_transaction_amount extends net.liftweb.record.field.DoubleField(this)
object obp_transaction_new_balance extends net.liftweb.record.field.DoubleField(this)

object obp_transaction_location extends BsonRecordField(this, Location)

}

object OBPTransaction extends OBPTransaction with MongoMetaRecord[OBPTransaction]







// Note: on mongo console db.chooseitems.ensureIndex( { location : "2d" } )




// Call like http://localhost:8080/api/balance/theaccountnumber/call.json
// See http://www.assembla.com/spaces/liftweb/wiki/REST_Web_Services


object OBPRest extends RestHelper {
    println("here we are in OBPRest")
    serve {
    case Req("html" :: "accounts" :: account_name, _, GetRequest) => <b>Hello Accounts</b>
    case Req("api" :: "static" :: _, "json", GetRequest) => JString("Static")
    case Req("api" :: "static" :: _, "xml", GetRequest) => <b>Static Hello</b>
    case Req("api" :: "balance" :: account_number, "", GetRequest) =>
      println("here i am serving a request")

      var first_account_number = account_number.head
      var question = "You requested the balance for account number: " + first_account_number
      var answer = "We don't know yet"
      var json_message = ("question" -> question) ~ ("answer" -> answer)


      println  (json_message)

      //println(pretty(render(json_message)))

      var json_message_2 = parse(""" { "numbers" : [1, 2, 3, 4] } """)

      println(json_message_2)




      import SimonSays._

      SimonSays.do_something()


      RabbitHelper.hello("world")

      json_message_2
    case Req("ping" :: Nil, _, _) => () => Full(PlainTextResponse("pong"))

    case Req("xml":: Nil, _, _) => Full(XmlResponse(
    <persons>
      <name>Simon</name>
      <name>John</name>
    </persons>
    ))

    case Req("json":: Nil, _, _) => () => Full(JsonResponse(List("simon","says")))

    case Req("mongodb":: Nil, _, _) => () =>

    // Create a location object
    val location = Location.createRecord.longitude(2).latitude(51)

    // Create a ChooseItem object and save it in the mongodb
    val choose_item_1 = OBPTransaction.createRecord
    .obp_transaction_amount(-100)
    .obp_transaction_currency("EU")
    .obp_transaction_location(location)
    .opb_transaction_other_account("Print Supplier")
    .save

    //val choose_items = ChooseItem.findAll

    // don't seem to need parse. This is a query using the DSL (domain specific language)
    var simple_query = ("description" -> "simons another item") ~ ("price" -> 123.45)
    // JObject(List(JField(description,JString(simons another item)), JField(price,JDouble(123.45))))



    println(simple_query)

    // couldn't get this to work println(Printer.pretty(simple_query))

    var string_query = """{ "description" : "simons another item" , "price" : 123.45 } """


    var location_query_string = """{ "location" : { "$near" : [2,2] } }"""

    var location_query_json = parse(location_query_string)// ("location" -> ("$near" -> (2,2)))


    println(location_query_json)




    // See http://api.mongodb.org/java/2.3/com/mongodb/QueryBuilder.html

    import com.mongodb._

    var longitude = 2 // x
    var latitude = 51 // y

    var earth_radius_km = 6378.137 // km
    var range_km = 5 // km
    var range_radians = range_km / earth_radius_km


    //    .sort("description").is(-1)


    // To get records using mongodb record
    val qry = QueryBuilder.start("description").is("simons another item")
    .put("location")
    .withinCenter(longitude, latitude, range_radians)
    .get

    val choose_items = ChooseItem.findAll(qry)

    // Note: we should be able to sort and limit the results. but don't know the syntax.
    //val choose_items = ChooseItem.findAll(qry).sortBy("don't know what to put here")
    /////////////////////////////////
    // Hmm Can't get this to work.
    import com.foursquare.rogue.Rogue._
    //val cs: List[ChooseItem] = (ChooseItem where (_.description eqs "simons another item"))
    //ChooseItem where (_.description eqs "simons another item")
    /////////////////////////////////////////////////////////////







    var some_json = """{"name":"joe","age":15}"""

    var json_string = parse(some_json)




    println("here you are:")

    ///////////////////////////////////////////////
    // Using Casbah.....................

    // Get a connection to MongoDB (note. Lift uses model/MongoConfig.scala for this config)
    val mongoConn = MongoConnection("localhost", 27017)


    // Get a mongodb database
    val mongoDB = mongoConn("test") // we are using the test database.


    // Get a collection
    val obp_transactions_collection = mongoDB.getCollection("obptransactions")

    // Print count of items in the collection
    println ("casbah thinks there are %d records in choose_items ".format(obp_transactions_collection.count()))


    // A Geo query!
    val my_query: DBObject = MongoDBObject("location" -> MongoDBObject(
                  "$within" -> MongoDBObject(
                  "$centerSphere" -> MongoDBList(
                    MongoDBList(2, 51),
                    0.1))))

    // Define sort
    val my_sort: DBObject = MongoDBObject("description" -> -1)
    // Limit
    val my_limit = 5


    // Get the items, applying the sort and limit.
    val my_cursor = obp_transactions_collection.find(my_query).sort(my_sort).limit(my_limit)


    // Print the results.
    while(my_cursor.hasNext()) {
        println(my_cursor.next());
    }

    println ("casbah thinks there are %d records in my_records ".format(my_cursor.count()))

    // To create a record with Casbah
    val newObj1 = MongoDBObject("foo" -> "bar",
                           "x" -> "y",
                           "pie" -> 3.14,
                           "spam" -> "eggs")


    println("The database names are %s".format(mongoConn.databaseNames))
    println (mongoConn.databaseNames)


    // To create
    val builder = MongoDBObject.newBuilder
    builder += "foo" -> "bar"
    builder += "x" -> "y"
    builder += ("pie" -> 3.14)
    builder += ("spam" -> "eggs", "mmm" -> "bacon")
    val newObj2 = builder.result

    // and update a record
    newObj2 += "OMG" -> "Ponies!"


    val mongoColl = MongoConnection()("chooseitems")


    val q = MongoDBObject("user" -> "someOtherUser")


    val items_count = choose_items.size

    println("There are %d items in the list".format(items_count))

    if (items_count > 0) {
      println(choose_items.first.description)
      println("after--------")
    }


    //Full(XhtmlResponse(choose_item_1.toXHtml))
    //Full(JsonResponse(List("Count of items found", items_count)))

    val json_message = ("items_count" -> items_count)

    // The last result of the function is returned.
    Full(JsonResponse(json_message))
    }
}






//import com.rabbitmq.client.{Channel, Connection, ConnectionFactory, QueueingConsumer}




object RabbitHelper
{
    println("start")

    // see http://akka.io/docs/akka-modules/1.1.2/modules/amqp.html
   // ( or http://olim7t.github.com/liftweb-2.0-release-sxr/net/liftweb/amqp/AMQPSender.scala.html )
  // https://github.com/jboner/akka-modules/blob/master/akka-amqp/src/test/scala/akka/amqp/test/AMQPProducerMessageTestIntegration.scala

    println("Before connection")




  /*

    val myAddresses = Array(new Address("myrabbitmq", 5672))  // This can be a list , new Address("someotherhostbla", 5672)
    val connectionParameters = AMQP.ConnectionParameters(myAddresses, "test", "test", "test")// host(s), user, pass, vhost
    val connection = AMQP.newConnection(connectionParameters)




    val exchangeParameters = ExchangeParameters("my_topic_exchange", Topic)
    val producer = AMQP.newProducer(connection, ProducerParameters(Some(exchangeParameters), producerId = Some("my_producer"))
    */



    //producer.sendOneWay("Some simple sting data".getBytes, "some.routing.key")


    //producer ! Message("Some simple sting data".getBytes, "some.routing.key")




    // println("After connection")

    var x = hello("world")

    def hello (input: String): String = {
      "hello"
    }




    /*




    var channel = getAMQPChannel("feed", "test")
    var consumer: QueueingConsumer = null
    while (true) {
      var task: QueueingConsumer.Delivery = null
      try { task = consumer.nextDelivery() }
      catch {
        case ex: Exception => {
          println("XX Error in AMQP connection: reconnecting.", ex)
		  Thread.sleep(5000)
          channel = getAMQPChannel("feed", "test")
        }
      }

	  if (task != null && task.getBody() != null) {
        println(new String(task.getBody()))
        try { channel.basicAck(task.getEnvelope().getDeliveryTag(), false) }
        catch {
          case ex: Exception => { println("Error ack'ing message.", ex) }
        }
      }
	}




    // Opens up a connection to RabbitMQ, retrying every five seconds
  // if the queue server is unavailable.
  def getAMQPChannel(queue: String, vhost: String) : Channel = {
    var attempts = 0
    var channel: Channel = null
    var connection: Connection = null

    println("Opening connection to AMQP " + vhost + " "  + queue + "...")
      try {
        connection = getConnection(queue, "myrabbitmq", 5672, "guest", "guest",vhost)
        // queue - host - port - username - password - vhost
        connection = getConnection(queue // queue
                                  , "myrabbitmq"  // host
                                  , 5672          // port
                                  , "test"        // username
                                  , "test"        // password
                                  , vhost        // vhost
                                  )
        channel = connection.createChannel()
        consumer = new QueueingConsumer(channel)
        channel.exchangeDeclare(queue, "direct", true)
        channel.queueDeclare(queue, true, false, false, null)
        channel.queueBind(queue, queue, queue)
        channel.basicConsume(queue, false, consumer)
        println("Connected to RabbitMQ")
      } catch {
        case ex: Exception => {
          println(".....cannot connect to AMQP. ")//, ex)
       }
    }
    channel
  }

  // Returns a new connection to an AMQP queue.
  def getConnection(queue: String, host: String, port: Int, username: String, password: String, vhost: String): Connection = {
    val factory = new ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername(username)
    factory.setPassword(password)
    factory.setVirtualHost(vhost)
    factory.newConnection()
  }

  */

}







} // end of package


  /*
package com.tesobe.rabbit {

  import com.rabbitmq.client.{Channel, Connection, ConnectionFactory, QueueingConsumer}
  //import com.rabbitmq.client._

  //import net.liftweb._


object Sender
{
​


  def send_it () : String = {



  "OK"
    }
}

*/

