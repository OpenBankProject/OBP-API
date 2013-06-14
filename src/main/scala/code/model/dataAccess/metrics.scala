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
 
 package code.model.dataAccess

 import net.liftweb.mongodb.record.field.{ObjectIdPk,DateField}
 import net.liftweb.record.field.{StringField}
 import net.liftweb.mongodb.record.{MongoRecord,MongoMetaRecord}

class APIMetric extends MongoRecord[APIMetric] with ObjectIdPk[APIMetric] {
  def meta = APIMetric
  object url extends StringField(this,255)
  object date extends DateField(this)
}

object APIMetric extends APIMetric with MongoMetaRecord[APIMetric]
