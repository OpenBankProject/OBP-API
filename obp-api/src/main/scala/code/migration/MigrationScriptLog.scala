/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.migration

import code.util.MappedUUID
import net.liftweb.mapper._

class MigrationScriptLog extends MigrationScriptLogTrait with LongKeyedMapper[MigrationScriptLog] with IdPK with CreatedUpdated {

  def getSingleton = MigrationScriptLog

  object MigrationScriptLogId extends MappedUUID(this)
  object Name extends MappedString(this, 100)
  object CommitId extends MappedString(this, 100)
  object IsSuccessful extends MappedBoolean(this)
  object StartDate extends MappedLong(this)
  object EndDate extends MappedLong(this)
  object Remark extends MappedString(this, 1024)

  override def primaryKey: Long = id.get
  override def migrationScriptLogId: String = MigrationScriptLogId.get
  override def name: String = Name.get
  override def commitId: String = CommitId.get  
  override def isSuccessful: Boolean = IsSuccessful.get  
  override def startDate: Long = StartDate.get  
  override def endDate: Long = EndDate.get  
  override def remark: String = Remark.get  
  
}

object MigrationScriptLog extends MigrationScriptLog with LongKeyedMetaMapper[MigrationScriptLog] {
  override def dbIndexes: List[BaseIndex[MigrationScriptLog]] = UniqueIndex(Name, IsSuccessful) :: super.dbIndexes
}



