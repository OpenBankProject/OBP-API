/*
 * Copyright 2010-2011 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
package net.liftweb
package mongodb
package record

import java.util.{Calendar, Date, UUID}
import java.util.regex.Pattern

import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.json.DefaultFormats
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST.JObject
import net.liftweb.record.field._
import net.liftweb.util.TimeHelpers._

import org.specs.Specification

import com.mongodb._
import org.bson.types.ObjectId


package mongotestrecords {

  import field._

  class TstRecord private () extends MongoRecord[TstRecord] {

    def meta = TstRecord

    def id = _id.value

    object _id extends UUIDField(this)

    //object binaryfield extends BinaryField(this)
    object booleanfield	extends BooleanField(this)
    //object countryfield extends CountryField(this)
    object datetimefield extends DateTimeField(this)
    //object decimalfield extends DecimalField(this)
    object doublefield extends DoubleField(this)
    object emailfield extends EmailField(this, 220)
    //object enumfield extends EnumField(this)
    object intfield extends IntField(this)
    object localefield extends LocaleField(this)
    object longfield extends LongField(this)
    object passwordfield extends MongoPasswordField(this)
    //object postalcodefield extends PostalCodeField(this, countryfield)
    object stringfield extends StringField(this, 32)
    //object textareafield extends TextareaField(this, 200)
    object timezonefield extends TimeZoneField(this)
    object patternfield extends PatternField(this)
    object datefield extends DateField(this)

    // JsonObjectField (requires a definition for defaultValue)
    object person extends JsonObjectField[TstRecord, Person](this, Person) {
      def defaultValue = Person("", 0, Address("", ""), Nil)
    }
  }

  object TstRecord extends TstRecord with MongoMetaRecord[TstRecord]

  case class Address(street: String, city: String)
  case class Child(name: String, age: Int, birthdate: Option[Date])

  case class Person(name: String, age: Int, address: Address, children: List[Child])
    extends JsonObject[Person] {
    def meta = Person
  }

  object Person extends JsonObjectMeta[Person]

  class MainDoc private () extends MongoRecord[MainDoc] with MongoId[MainDoc] {

    def meta = MainDoc

    object name extends StringField(this, 12)
    object cnt extends IntField(this)

    object refdoc extends DBRefField[MainDoc, RefDoc](this, RefDoc)
    object refstringdoc extends DBRefField[MainDoc, RefStringDoc](this, RefStringDoc)

    object refdocId extends ObjectIdField(this) {
      def obj = RefDoc.find(value)
    }

    object refuuid extends UUIDField(this) {
      def obj = RefUuidDoc.find(value)
    }
  }
  object MainDoc extends MainDoc with MongoMetaRecord[MainDoc]

  class RefDoc private () extends MongoRecord[RefDoc] with MongoId[RefDoc] {
    def meta = RefDoc
  }
  object RefDoc extends RefDoc with MongoMetaRecord[RefDoc]

  // string as id
  class RefStringDoc private () extends MongoRecord[RefStringDoc] {
    def meta = RefStringDoc

    def id = _id.value

    object _id extends StringField(this, 36) {
      override def defaultValue = UUID.randomUUID.toString
    }

    def getRef: DBRef =
      MongoDB.use(meta.mongoIdentifier) ( db =>
        new DBRef(db, meta.collectionName, _id.value)
      )
  }
  object RefStringDoc extends RefStringDoc with MongoMetaRecord[RefStringDoc]

  // uuid as id
  class RefUuidDoc extends MongoRecord[RefUuidDoc] {
    def meta = RefUuidDoc

    def id = _id.value

    object _id extends UUIDField(this)

    def getRef: DBRef =
      MongoDB.use(meta.mongoIdentifier) ( db =>
        new DBRef(db, meta.collectionName, _id.value)
      )
  }
  object RefUuidDoc extends RefUuidDoc with MongoMetaRecord[RefUuidDoc]

  //class ListDoc private () extends MongoRecord[ListDoc] with MongoId[ListDoc] {
  class ListDoc extends MongoRecord[ListDoc] with MongoId[ListDoc] {
    def meta = ListDoc

    import scala.collection.JavaConversions._

    // standard list types
    object name extends StringField(this, 10)
    object stringlist extends MongoListField[ListDoc, String](this)
    object intlist extends MongoListField[ListDoc, Int](this)
    object doublelist extends MongoListField[ListDoc, Double](this)
    object boollist extends MongoListField[ListDoc, Boolean](this)
    object objidlist extends MongoListField[ListDoc, ObjectId](this)
    object dtlist extends MongoListField[ListDoc, Date](this)
    object patternlist extends MongoListField[ListDoc, Pattern](this)

    // specialized list types
    object jsonobjlist extends MongoJsonObjectListField(this, JsonDoc)

    // these require custom setFromDBObject methods
    object maplist extends MongoListField[ListDoc, Map[String, String]](this) {
      override def asDBObject: DBObject = {
        val dbl = new BasicDBList

        value.foreach {
          m => {
            val dbo = new BasicDBObject

            m.keys.foreach(k => {
              dbo.put(k.toString, m.getOrElse(k, ""))
            })

            dbl.add(dbo)
          }
        }

        dbl
      }

      override def setFromDBObject(dbo: DBObject): Box[List[Map[String, String]]] = {
        val lst: List[Map[String, String]] =
          dbo.keySet.toList.map(k => {
            dbo.get(k.toString) match {
              case bdbo: BasicDBObject if (bdbo.containsField("name") && bdbo.containsField("type")) =>
                Map("name"-> bdbo.getString("name"), "type" -> bdbo.getString("type"))
              case _ => null
            }
          }).filter(_ != null)
        Full(set(lst))
      }
    }

  }
  object ListDoc extends ListDoc with MongoMetaRecord[ListDoc]

  case class JsonDoc(id: String, name: String) extends JsonObject[JsonDoc] {
    def meta = JsonDoc
  }
  object JsonDoc extends JsonObjectMeta[JsonDoc]

  //class MapDoc private () extends MongoRecord[MapDoc] with MongoId[MapDoc] {
  class MapDoc extends MongoRecord[MapDoc] with MongoId[MapDoc] {
    def meta = MapDoc

    object stringmap extends MongoMapField[MapDoc, String](this)
  }
  object MapDoc extends MapDoc with MongoMetaRecord[MapDoc] {
    override def formats = DefaultFormats.lossless // adds .000
  }

  class OptionalDoc private () extends MongoRecord[OptionalDoc] with MongoId[OptionalDoc] {
    def meta = OptionalDoc
    // optional fields
    object stringbox extends StringField(this, 32) {
      override def optional_? = true
      override def defaultValue = "nothin"
    }
  }
  object OptionalDoc extends OptionalDoc with MongoMetaRecord[OptionalDoc]

  class StrictDoc private () extends MongoRecord[StrictDoc] with MongoId[StrictDoc] {
    def meta = StrictDoc
    object name extends StringField(this, 32)
  }
  object StrictDoc extends StrictDoc with MongoMetaRecord[StrictDoc] {

    import net.liftweb.json.JsonDSL._

    ensureIndex(("name" -> 1), true) // unique name
  }
}


/**
 * Systems under specification for MongoRecordExamples.
 */
object MongoRecordExamplesSpec extends Specification("MongoRecordExamples Specification") with MongoTestKit {
  import mongotestrecords._
  import net.liftweb.util.TimeHelpers._

  "TstRecord example" in {

    checkMongoIsRunning

    val pwd = "test"
    val cal = Calendar.getInstance
    cal.set(2009, 10, 2)

    val tr = TstRecord.createRecord
    tr.stringfield("test record string field")
    tr.emailfield("test")
    tr.validate.size must_== 2
    tr.passwordfield.setPassword(pwd)
    tr.emailfield("test@example.com")
    tr.datetimefield(cal)
    tr.patternfield(Pattern.compile("^Mo", Pattern.CASE_INSENSITIVE))
    tr.validate.size must_== 0

    // JsonObjectField
    val dob1 = Calendar.getInstance.setYear(2005).setMonth(7).setDay(4)
    val per = Person("joe", 27, Address("Bulevard", "Helsinki"), List(Child("Mary", 5, Some(dob1.getTime)), Child("Mazy", 3, None)))
    tr.person(per)

    // save the record in the db
    tr.save

    // retrieve from db
    def fromDb = TstRecord.find("_id", tr.id)

    fromDb.isDefined must_== true

    for (t <- fromDb) {
      t._id.value must_== tr._id.value
      t.booleanfield.value must_== tr.booleanfield.value
      TstRecord.formats.dateFormat.format(t.datetimefield.value.getTime) must_==
      TstRecord.formats.dateFormat.format(tr.datetimefield.value.getTime)
      t.doublefield.value must_== tr.doublefield.value
      t.intfield.value must_== tr.intfield.value
      t.localefield.value must_== tr.localefield.value
      t.longfield.value must_== tr.longfield.value
      t.passwordfield.isMatch(pwd) must_== true
      t.stringfield.value must_== tr.stringfield.value
      t.timezonefield.value must_== tr.timezonefield.value
      t.datetimefield.value must_== tr.datetimefield.value
      t.patternfield.value.pattern must_== tr.patternfield.value.pattern
      t.patternfield.value.flags must_== tr.patternfield.value.flags
      t.datefield.value must_== tr.datefield.value
      t.person.value.name must_== tr.person.value.name
      t.person.value.age must_== tr.person.value.age
      t.person.value.address.street must_== tr.person.value.address.street
      t.person.value.address.city must_== tr.person.value.address.city
      t.person.value.children.size must_== tr.person.value.children.size
      for (i <- List.range(0, t.person.value.children.size-1)) {
        t.person.value.children(i).name must_== tr.person.value.children(i).name
        t.person.value.children(i).age must_== tr.person.value.children(i).age
        t.person.value.children(i).birthdate must_== tr.person.value.children(i).birthdate
      }
    }

    if (!debug) TstRecord.drop
  }

  "Ref example" in {

    checkMongoIsRunning

    val ref1 = RefDoc.createRecord
    val ref2 = RefDoc.createRecord

    ref1.save must_== ref1
    ref2.save must_== ref2

    val refString1 = RefStringDoc.createRecord
    val refString2 = RefStringDoc.createRecord

    refString1.save must_== refString1
    refString2.save must_== refString2

    val refUuid1 = RefUuidDoc.createRecord
    val refUuid2 = RefUuidDoc.createRecord

    refUuid1.save must_== refUuid1
    refUuid2.save must_== refUuid2

    val md1 = MainDoc.createRecord
    val md2 = MainDoc.createRecord
    val md3 = MainDoc.createRecord
    val md4 = MainDoc.createRecord

    md1.name.set("md1")
    md2.name.set("md2")
    md3.name.set("md3")
    md4.name.set("md4")

    md1.refdoc.set(ref1.getRef)
    md2.refdoc.set(ref1.getRef)
    md3.refdoc.set(ref2.getRef)
    md4.refdoc.set(ref2.getRef)

    md1.refstringdoc.set(refString1.getRef)
    md2.refstringdoc.set(refString1.getRef)
    md3.refstringdoc.set(refString2.getRef)
    md4.refstringdoc.set(refString2.getRef)

    md1.refdocId.set(ref1.id)
    md2.refdocId.set(ref1.id)
    md3.refdocId.set(ref2.id)
    md4.refdocId.set(ref2.id)

    md1.refuuid.set(refUuid1.id)
    md2.refuuid.set(refUuid1.id)
    md3.refuuid.set(refUuid2.id)
    md4.refuuid.set(refUuid2.id)

    md1.save must_== md1
    md2.save must_== md2
    md3.save must_== md3
    md4.save must_== md4

    MainDoc.count must_== 4
    RefDoc.count must_== 2
    RefStringDoc.count must_== 2

    // get the docs back from the db
    MainDoc.find(md1.id).foreach(m => {
      m.name.value must_== md1.name.value
      m.cnt.value must_== md1.cnt.value
      m.refdoc.value.getId must_== ref1.getRef.getId
      m.refdoc.value.getRef must_== ref1.getRef.getRef
      m.refstringdoc.value.getId must_== refString1.getRef.getId
      m.refstringdoc.value.getRef must_== refString1.getRef.getRef
      m.refdocId.value must_== md1.refdocId.value
      m.refuuid.value must_== md1.refuuid.value
    })

    // fetch a refdoc
    val refFromFetch = md1.refdoc.obj
    refFromFetch.isDefined must_== true
    refFromFetch.open_!.id must_== ref1.id

    // query for a single doc with a JObject query
    val md1a = MainDoc.find(("name") -> "md1")
    md1a.isDefined must_== true
    md1a.foreach(o => o.id must_== md1.id)

    // query for a single doc with a k, v query
    val md1b = MainDoc.find("_id", md1.id)
    md1b.isDefined must_== true
    md1b.foreach(o => o.id must_== md1.id)

    // query for a single doc with a Map query
    val md1c = MainDoc.find(("name" -> "md1"))
    md1c.isDefined must_== true
    md1c.foreach(o => o.id must_== md1.id)

    // find all documents
    MainDoc.findAll.size must_== 4
    RefDoc.findAll.size must_== 2

    // find all documents with JObject query
    val mdq1 = MainDoc.findAll(("name" -> "md1"))
    mdq1.size must_== 1

    // find all documents with $in query, sorted
    val qry = ("name" -> ("$in" -> List("md1", "md2")))
    val mdq2 = MainDoc.findAll(qry, ("name" -> -1))
    mdq2.size must_== 2
    mdq2.head.id must_== md2.id

    // Find all documents using a k, v query
    val mdq3 = MainDoc.findAll("_id", md1.id)
    mdq3.size must_== 1

    // find all documents with field selection
    val mdq4 = MainDoc.findAll(("name" -> "md1"), ("name" -> 1), Empty)
    mdq4.size must_== 1

    // Upsert - this should add a new row
    val md5 = MainDoc.createRecord
    md5.name.set("md5")
    md5.refdoc.set(ref1.getRef)
    md5.refstringdoc.set(refString1.getRef)
    MainDoc.update(("name" -> "nothing"), md5, Upsert)
    MainDoc.findAll.size must_== 5

    // modifier operations $inc, $set, $push...
    val o2 = (("$inc" -> ("cnt" -> 1)) ~ ("$set" -> ("name" -> "md1a")))
    MainDoc.update(("name" -> "md1"), o2)
    // get the doc back from the db and compare
    val mdq5 = MainDoc.find("_id", md1.id)
    mdq5.isDefined must_== true
    mdq5.map ( m => {
      m.name.value must_== "md1a"
      m.cnt.value must_== 1
    })

    if (!debug) {
      // delete them
      md1.delete_!
      md2.delete_!
      md3.delete_!
      md4.delete_!
      md5.delete_!
      ref1.delete_!
      ref2.delete_!

      MainDoc.findAll.size must_== 0

      MainDoc.drop
      RefDoc.drop
      RefStringDoc.drop
    }
  }

  "List example" in {
    checkMongoIsRunning

    val ref1 = RefDoc.createRecord
    val ref2 = RefDoc.createRecord

    ref1.save must_== ref1
    ref2.save must_== ref2

    val name = "ld1"
    val strlist = List("string1", "string2", "string3", "string1")
    val jd1 = JsonDoc("1", "jsondoc1")

    val ld1 = ListDoc.createRecord
    ld1.name.set(name)
    ld1.stringlist.set(strlist)
    ld1.intlist.set(List(99988,88, 88))
    ld1.doublelist.set(List(997655.998,88.8))
    ld1.boollist.set(List(true,true,false))
    ld1.objidlist.set(List(ObjectId.get, ObjectId.get))
    ld1.dtlist.set(List(now, now))
    ld1.jsonobjlist.set(List(jd1, JsonDoc("2", "jsondoc2"), jd1))
    ld1.patternlist.set(List(Pattern.compile("^Mongo"), Pattern.compile("^Mongo2")))
    ld1.maplist.set(List(Map("name" -> "map1", "type" -> "map"), Map("name" -> "map2", "type" -> "map")))

    ld1.save must_== ld1

    val qld1 = ListDoc.find(ld1.id)

    qld1.isDefined must_== true

    qld1.foreach { l =>
      l.name.value must_== ld1.name.value
      l.stringlist.value must_== ld1.stringlist.value
      l.intlist.value must_== ld1.intlist.value
      l.doublelist.value must_== ld1.doublelist.value
      l.boollist.value must_== ld1.boollist.value
      l.objidlist.value must_== ld1.objidlist.value
      l.dtlist.value must_== ld1.dtlist.value
      l.jsonobjlist.value must_== ld1.jsonobjlist.value
      for (i <- List.range(0, l.patternlist.value.size-1)) {
        l.patternlist.value(i).pattern must_== ld1.patternlist.value(i).pattern
      }
      l.maplist.value must_== ld1.maplist.value
      for (i <- List.range(0, l.jsonobjlist.value.size-1)) {
        l.jsonobjlist.value(i).id must_== ld1.jsonobjlist.value(i).id
        l.jsonobjlist.value(i).name must_== ld1.jsonobjlist.value(i).name
      }
    }

    if (!debug) {
      ListDoc.drop
      RefDoc.drop
    }
  }

  "Map Example" in {

    checkMongoIsRunning

    val md1 = MapDoc.createRecord
    md1.stringmap.set(Map("h" -> "hola"))

    md1.save must_== md1

    md1.delete_!

    if (!debug) MapDoc.drop
  }

  "Optional Example" in {

    checkMongoIsRunning

    val od1 = OptionalDoc.createRecord
    od1.stringbox.valueBox must_== Empty
    od1.save must_== od1

    OptionalDoc.find(od1.id).foreach {
      od1FromDB =>
        od1FromDB.stringbox.valueBox must_== od1.stringbox.valueBox
    }


    val od2 = OptionalDoc.createRecord
    od1.stringbox.valueBox must_== Empty
    od2.stringbox.set("aloha")
    od2.save must_== od2

    OptionalDoc.find(od2.id).foreach {
      od2FromDB =>
        od2FromDB.stringbox.valueBox must_== od2.stringbox.valueBox
    }

    if (!debug) OptionalDoc.drop
  }

  "Strict Example" in {

    checkMongoIsRunning

    val sd1 = StrictDoc.createRecord.name("sd1")
    val sd2 = StrictDoc.createRecord.name("sd1")

    sd1.save(true) must_== sd1
    sd2.save(true) must throwA[MongoException]

    sd1.save

    sd2.name("sd2")
    sd2.save(true) must_== sd2

    if (!debug) StrictDoc.drop
  }
}

*/