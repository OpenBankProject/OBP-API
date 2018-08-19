/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
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

 package code.metrics

import java.util.Date

import code.bankconnectors.OBPQueryParam
import net.liftweb.common.Box
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.record.field.{LongField, StringField}

import scala.concurrent.Future

 private class MongoAPIMetric extends MongoRecord[MongoAPIMetric] with ObjectIdPk[MongoAPIMetric] with APIMetric {
   def meta = MongoAPIMetric
   object userId extends StringField(this,255)
   object url extends StringField(this,255)
   object date extends DateField(this)
   object duration extends LongField(this)
   object userName extends StringField(this,255)
   object appName extends StringField(this,255)
   object developerEmail extends StringField(this,255)
   //The consumerId, Foreign key to Consumer not key
   object consumerId extends StringField(this,255)
   //name of the Scala Partial Function being used for the endpoint
   object implementedByPartialFunction  extends StringField(this,255)
   //name of version where the call is implemented) -- S.request.get.view
   object implementedInVersion  extends StringField(this,255)
   //(GET, POST etc.) --S.request.get.requestType
   object verb extends StringField(this,255)
   object correlationId extends StringField(this,255)


   def getUrl() = url.get
   def getDate() = date.get
   def getDuration(): Long = duration.get
   def getUserId() = userId.get
   def getUserName(): String = userName.get
   def getAppName(): String = appName.get
   def getDeveloperEmail(): String = developerEmail.get
   override def getConsumerId(): String = consumerId.get
   override def getImplementedByPartialFunction(): String = implementedByPartialFunction.get
   override def getImplementedInVersion(): String = implementedInVersion.get
   override def getVerb(): String = verb.get
   override def getCorrelationId(): String = correlationId.get
}

private object MongoAPIMetric extends MongoAPIMetric with MongoMetaRecord[MongoAPIMetric] with APIMetrics {

  def saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String, correlationId: String): Unit = {
    MongoAPIMetric.createRecord.
      userId(userId).
      url(url).
      date(date).
      duration(duration).
      userName(userName).
      appName(appName).
      developerEmail(developerEmail).
      consumerId(consumerId).
      implementedByPartialFunction(implementedByPartialFunction).
      implementedInVersion(implementedInVersion).
      verb(verb).
      correlationId(correlationId)
    saveTheRecord()
  }

//  def getAllGroupedByUrl() : Map[String, List[APIMetric]] = {
//    MongoAPIMetric.findAll.groupBy[String](_.url.get)
//  }
//
//  def getAllGroupedByDay() : Map[Date, List[APIMetric]] = {
//    MongoAPIMetric.findAll.groupBy[Date](APIMetrics.getMetricDay)
//  }
//
//  def getAllGroupedByUserId() : Map[String, List[APIMetric]] = {
//    MongoAPIMetric.findAll.groupBy[String](_.getUserId)
//  }

  override def getAllMetrics(queryParams: List[OBPQueryParam]): List[APIMetric] = {
    MongoAPIMetric.findAll
  }
  override def bulkDeleteMetrics(): Boolean = ???
  
  override def getAllAggregateMetricsFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AggregateMetrics]]] = ???
  
  override def getTopApisFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopApi]]] = ???
  
  override def getTopConsumersFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopConsumer]]] = ???

}
