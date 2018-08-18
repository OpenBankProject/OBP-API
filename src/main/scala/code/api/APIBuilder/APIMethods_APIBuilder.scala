/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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

package code.api.APIBuilder

import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import net.liftweb.http.rest.RestHelper
import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import code.api.util.APIUtil._
import net.liftweb.json
import net.liftweb.json.JValue
import code.api.APIBuilder.JsonFactory_APIBuilder._

trait APIMethods_APIBuilder
{
  self: RestHelper =>
  
  val ImplementationsBuilderAPI = new Object()
  {
    val apiVersion: ApiVersion = ApiVersion.apiBuilder
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    
    def endpointsOfBuilderAPI = getBooks :: Nil
    
    resourceDocs += ResourceDoc(
      getBooks, 
      apiVersion, 
      "getBooks", 
      "GET",
      "/my/good/books",
      "Get Books",
      "Return All my books ,Authentication is Mandatory",
      emptyObjectJson, 
      rootInterface,
      List(UnknownError),
      Catalogs(Core, notPSD2, OBWG), 
      apiTagApiBuilder :: Nil
    )
    
    lazy val getBooks: OBPEndpoint ={
      case ("my" :: "good" :: "books" :: Nil) JsonGet req =>
        cc =>
        {
          for{
            u <- cc.user ?~ UserNotLoggedIn;
            jsonString = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/newAPis.json").mkString;
            jsonObject: JValue = json.parse(jsonString) \\ "success_response_body"
          }yield{
              successJsonResponse(jsonObject)
          }
        }
    }
  }
}