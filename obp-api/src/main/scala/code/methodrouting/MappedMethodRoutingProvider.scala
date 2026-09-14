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

package code.methodrouting

import org.json4s._
import code.api.util.CustomJsonFormats
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, EmptyBox, Full}
import com.openbankproject.commons.util.json
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils
import org.json4s.native.Serialization.write
import com.openbankproject.commons.util.Functions.Implicits._
import org.json4s.JsonAST.JArray

object MappedMethodRoutingProvider extends MethodRoutingProvider with CustomJsonFormats{

  override def getById(methodRoutingId: String): Box[MethodRoutingT] =  MethodRouting.find(
    By(MethodRouting.MethodRoutingId, methodRoutingId)
  )

  override def getMethodRoutings(methodName: Option[String], isBankIdExactMatch: Option[Boolean] = None, bankIdPattern: Option[String] = None): List[MethodRouting] = {

    val byMethodName = methodName.map(By(MethodRouting.MethodName, _))
    val byIsBankIdExactMatch = isBankIdExactMatch.map(By(MethodRouting.IsBankIdExactMatch, _))
    val byBankIdPattern = bankIdPattern.map(By(MethodRouting.BankIdPattern, _))

    val queryParam: Seq[QueryParam[MethodRouting]] = List(byMethodName, byIsBankIdExactMatch, byBankIdPattern).collect {
      case Some(by) => by
    }

    MethodRouting.findAll(queryParam :_*)
  }

  override def createOrUpdate(methodRouting: MethodRoutingT): Box[MethodRoutingT] = {

    val bankIdPattern = methodRouting.bankIdPattern
                          .filter(StringUtils.isNotBlank) // treat blank string as not supplied

    //to find exists methodRouting, if methodRoutingId supplied, query by methodRoutingId, or use methodName and methodRoutingId to do query
    val existsMethodRouting: Box[MethodRouting] = methodRouting.methodRoutingId match {
      case Some(id) if (StringUtils.isNotBlank(id)) => getByMethodRoutingId(id)
      case _ => Empty
    }
    val entityToPersist = existsMethodRouting match {
      case _: EmptyBox => MethodRouting.create
      case Full(methodRouting) => methodRouting
    }
    // if not supply bankIdPattern, isExactMatch must be false
    val isExactMatch = if(bankIdPattern.isDefined) methodRouting.isBankIdExactMatch else false

    val existsMethodRoutingParameters = methodRouting.parameters match {
      case parameters if (parameters.nonEmpty) => parameters
      case _ => List.empty[MethodRoutingParam]
    }
    
    tryo{
      entityToPersist
        .MethodName(methodRouting.methodName)
        .BankIdPattern(bankIdPattern.orNull)
        .IsBankIdExactMatch(isExactMatch)
        .ConnectorName(methodRouting.connectorName)
        .Parameters(write(existsMethodRoutingParameters))
        .saveMe()
    }
  }


  override def delete(methodRoutingId: String): Box[Boolean] = getByMethodRoutingId(methodRoutingId).map(_.delete_!)

  private[this] def getByMethodRoutingId(methodRoutingId: String): Box[MethodRouting] = MethodRouting.find(By(MethodRouting.MethodRoutingId, methodRoutingId))

}

class MethodRouting extends MethodRoutingT with LongKeyedMapper[MethodRouting] with IdPK with CustomJsonFormats{

  override def getSingleton = MethodRouting

  object MethodRoutingId extends MappedUUID(this)
  object MethodName extends MappedString(this, 255)
  object BankIdPattern extends MappedString(this, 255){
    override def defaultValue: String = MethodRouting.bankIdPatternMatchAny
  }
  object IsBankIdExactMatch extends MappedBoolean(this)
  object ConnectorName extends MappedString(this, 255)
  object Parameters extends MappedText(this)

  override def methodRoutingId: Option[String] = Option(MethodRoutingId.get)
  override def methodName: String = MethodName.get
  override def bankIdPattern: Option[String] = Option(BankIdPattern.get)
  override def isBankIdExactMatch: Boolean = IsBankIdExactMatch.get
  override def connectorName: String = ConnectorName.get

  //Here we store all the key-value pairs in one big String fields in database.
  override def parameters: List[MethodRoutingParam] = {
    val value = json.parse(Parameters.get ?: "[]").asInstanceOf[JArray]
    value.arr.map(MethodRoutingParam(_))
  }

}

object MethodRouting extends MethodRouting with LongKeyedMetaMapper[MethodRouting] {
  override def dbIndexes = UniqueIndex(MethodRoutingId) :: super.dbIndexes

  /**
    * default bankIdPattern is match any
    */
  val bankIdPatternMatchAny: String = ".*"
}

