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

package code.webuiprops

import code.api.cache.Caching
import code.api.util.APIUtil.{activeBrand, writeMetricEndpointTiming}
import code.api.util.{APIUtil, ErrorMessages, I18NUtil}
import code.util.MappedUUID
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._

import java.util.UUID.randomUUID

/**
  * props name start with "webui_" can set in to db, this module just support the webui_ props CRUD
  */
object MappedWebUiPropsProvider extends WebUiPropsProvider {
  // default webUiProps value cached seconds
  private val webUiPropsTTL = APIUtil.getPropsAsIntValue("webui.props.cache.ttl.seconds", 0)

  override def getAll(): List[WebUiPropsT] =  WebUiProps.findAll()

  override def getByName(name: String): Box[WebUiPropsT] = WebUiProps.find(By(WebUiProps.Name, name))

  override def createOrUpdate(webUiProps: WebUiPropsT): Box[WebUiPropsT] = {
      WebUiProps.find(By(WebUiProps.Name, webUiProps.name))
      .or(Full(WebUiProps.create))
      .map(_.Name(webUiProps.name.trim()).Value(webUiProps.value).saveMe())
  }

  override def delete(webUiPropsId: String):Box[Boolean] = WebUiProps.find(By(WebUiProps.WebUiPropsId, webUiPropsId)) match {
    case Full(props) => Full(props.delete_!)
    case Empty => Failure(ErrorMessages.WebUiPropsNotFound)
    case Failure(msg, t, c) => Failure(msg, t, c)
  }

  // Rules to obtain the WebUI props value
  // 1) Get requested + brand + language if any
  // 2) Get requested + language if any
  // 3) Get requested if any
  // 4) Get default value
  override def getWebUiPropsValue(requestedPropertyName: String, defaultValue: String, language: String = I18NUtil.currentLocale().toString()): String = writeMetricEndpointTiming {
    import scala.concurrent.duration._
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithImMemory(Some(cacheKey.toString()))(webUiPropsTTL.second) {
        // If we have an active brand, construct a target property name to look for.
        val brandSpecificPropertyName = activeBrand() match {
          case Some(brand) => s"${requestedPropertyName}_FOR_BRAND_${brand}"
          case _ => requestedPropertyName
        }
        
        // In case there is a translation we must use it
        val webUiPropsPropertyName = s"${brandSpecificPropertyName}_${language}"
        val translatedAndOrBrandPropertyName = WebUiProps.find(By(WebUiProps.Name, webUiPropsPropertyName)).isDefined match {
          case true => webUiPropsPropertyName
          case false => brandSpecificPropertyName
        }
        
        WebUiProps.find(By(WebUiProps.Name, translatedAndOrBrandPropertyName)).map(_.value) // Get translated and/or brand specific value if any
          .or(WebUiProps.find(By(WebUiProps.Name, requestedPropertyName)).map(_.value)) // Get requested value if any
            .openOr {
              APIUtil.getPropsValue(requestedPropertyName, defaultValue) // Otherwise return the default value 
            }
      }
    }
  }("getWebUiProps")("MappedWebUiPropsProvider")

}

class WebUiProps extends WebUiPropsT with LongKeyedMapper[WebUiProps] with IdPK {

  override def getSingleton = WebUiProps

  object WebUiPropsId extends MappedUUID(this)
  object Name extends MappedString(this, 255)
  object Value extends MappedText(this)

  override def webUiPropsId: Option[String] = Option(WebUiPropsId.get)
  override def name: String = Name.get
  override def value: String = Value.get
  override def source: Option[String] = Some("database")
}

object WebUiProps extends WebUiProps with LongKeyedMetaMapper[WebUiProps] {
  override def dbIndexes = UniqueIndex(WebUiPropsId) :: UniqueIndex(Name) :: super.dbIndexes
}

