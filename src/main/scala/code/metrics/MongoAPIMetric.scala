/**
Open Bank Project - API
Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd

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

 package code.metrics

import java.util.Date

import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.record.field.StringField

 private class MongoAPIMetric extends MongoRecord[MongoAPIMetric] with ObjectIdPk[MongoAPIMetric] with APIMetric {
   def meta = MongoAPIMetric
   object userId extends StringField(this,255)
   object url extends StringField(this,255)
   object date extends DateField(this)

   def getUrl() = url.get
   def getDate() = date.get
   def getUserId() = userId.get
}

private object MongoAPIMetric extends MongoAPIMetric with MongoMetaRecord[MongoAPIMetric] with APIMetrics {

  def saveMetric(userId: String, url : String, date : Date) : Unit = {
    MongoAPIMetric.createRecord.
      userId(userId).
      url(url).
      date(date).
      save
  }

  def getAllGroupedByUrl() : Map[String, List[APIMetric]] = {
    MongoAPIMetric.findAll.groupBy[String](_.url.get)
  }

  def getAllGroupedByDay() : Map[Date, List[APIMetric]] = {
    MongoAPIMetric.findAll.groupBy[Date](APIMetrics.getMetricDay)
  }

  def getAllGroupedByUserId() : Map[String, List[APIMetric]] = {
    MongoAPIMetric.findAll.groupBy[String](_.getUserId)
  }
}
