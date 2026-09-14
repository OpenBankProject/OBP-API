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

package code.metadata.counterparties

import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.CounterpartyBespoke
import net.liftweb.mapper.{MappedString, _}

import scala.collection.immutable.List

class MappedCounterpartyBespoke extends LongKeyedMapper[MappedCounterpartyBespoke] with IdPK {
  def getSingleton = MappedCounterpartyBespoke
  
  object mCounterparty extends MappedLongForeignKey(this, MappedCounterparty)
  object mKey extends MappedString(this, 255)
  object mVaule extends MappedString(this, 255)
  
}
object MappedCounterpartyBespoke extends MappedCounterpartyBespoke with LongKeyedMetaMapper[MappedCounterpartyBespoke]{}


object MapperCounterpartyBespokes extends CounterpartyBespokes with MdcLoggable{
  
  def createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke]): List[MappedCounterpartyBespoke]= {
    bespokes.map(
      bespoke =>
        MappedCounterpartyBespoke
          .create
          .mCounterparty(mapperCounterpartyPrimaryKey)
          .mKey(bespoke.key)
          .mVaule(bespoke.value)
          .saveMe()
    )
  }
  
  def getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long): List[MappedCounterpartyBespoke] =
    MappedCounterpartyBespoke
      .findAll(
        By(MappedCounterpartyBespoke.mCounterparty, mapperCounterpartyPrimaryKey)
      )
  
  
}