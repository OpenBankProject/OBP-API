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

// used to construct type
//import com.tesobe.something._



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

////////////

import _root_.net.liftweb.common._
import _root_.net.liftweb.util._
import _root_.net.liftweb.http._
import _root_.net.liftweb.mapper._
import _root_.net.liftweb.util.Helpers._
import _root_.net.liftweb.sitemap._
import _root_.scala.xml._
import _root_.net.liftweb.http.S._
import _root_.net.liftweb.http.RequestVar
import _root_.net.liftweb.util.Helpers._
import _root_.net.liftweb.common.Full
import net.liftweb.mongodb.{Skip, Limit}
import _root_.net.liftweb.http.S._
import _root_.net.liftweb.mapper.view._
import com.mongodb._

import code.model._
//import code.model.Location

// Note: on mongo console db.chooseitems.ensureIndex( { location : "2d" } )

// Call like http://localhost:8080/api/balance/theaccountnumber/call.json
// See http://www.assembla.com/spaces/liftweb/wiki/REST_Web_Services


object OBPRest extends RestHelper {
    println("here we are in OBPRest")
    serve {

    //case Req("test" , "ping", _, _) => () => Full(PlainTextResponse("pong"))
    //case Req("xml" :: Nil, _, _) => Full(XmlResponse(<persons><name>Simon</name><name>John</name></persons>))
    //case Req("test" :: "static" :: _, "json", GetRequest) => JString("Static")
    //case Req("test" :: "static" :: _, "xml", GetRequest) => <b>Static Hello</b>
    //case Req("test":: "json", _, _) => () => Full(JsonResponse(List("simon","says")))
    case Req("api" :: "transactions" :: account_number, "", GetRequest) =>

     // var transactions = OBPTransaction.findAll(QueryBuilder.start().get())



      // just getting rid of simon test stuff.


      val qry = QueryBuilder.start("obp_data_blob").notEquals("simon-says").get
      var transactions = OBPTransaction.findAll(qry)


      JsonResponse(transactions.map(t => { t.asJValue }))

    case Req("mongodb":: Nil, _, _) => () =>

    // Create a location object
    val location = Location.createRecord.longitude(2).latitude(51)


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

    val choose_items = OBPTransaction.findAll(qry)


    var some_json = """{"name":"joe","age":15}"""

    var json_string = parse(some_json)




    println("here you are:")

    ///////////////////////////////////////////////
    // Using Casbah.....................

    // Get a connection to MongoDB (note. Lift uses model/MongoConfig.scala for this config)
    val mongoConn = MongoConnection("obp_mongodb", 27017)


    // Get a mongodb database
    val mongoDB = mongoConn("OBP003")


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


    val mongoColl = MongoConnection()("chooseitems")


    val q = MongoDBObject("user" -> "someOtherUser")


    val items_count = choose_items.size

    println("There are %d items in the list".format(items_count))

    if (items_count > 0) {
      //println(choose_items.first.description)
      println("after--------")
    }


    //Full(XhtmlResponse(choose_item_1.toXHtml))
    //Full(JsonResponse(List("Count of items found", items_count)))

    val json_message = ("items_count" -> items_count)

    // The last result of the function is returned.
    Full(JsonResponse(json_message))
    }
}


} // end of package