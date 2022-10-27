package code.webuiprops

import java.util.UUID.randomUUID

import code.api.cache.Caching
import code.api.util.{APIUtil, ErrorMessages, I18NUtil}
import code.api.util.APIUtil.{activeBrand, saveConnectorMetric}
import code.util.MappedUUID
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._

/**
  * props name start with "webui_" can set in to db, this module just support the webui_ props CRUD
  */
object MappedWebUiPropsProvider extends WebUiPropsProvider {
  // default webUiProps value cached seconds
  private val webUiPropsTTL = APIUtil.getPropsAsIntValue("webui.props.cache.ttl.seconds", 0)

  override def getAll(): List[WebUiPropsT] =  WebUiProps.findAll()


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
  override def getWebUiPropsValue(requestedPropertyName: String, defaultValue: String): String = saveConnectorMetric {
    import scala.concurrent.duration._
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(webUiPropsTTL second) {
        // If we have an active brand, construct a target property name to look for.
        val brandSpecificPropertyName = activeBrand() match {
          case Some(brand) => s"${requestedPropertyName}_FOR_BRAND_${brand}"
          case _ => requestedPropertyName
        }
        
        // In case there is a translation we must use it
        val language = I18NUtil.currentLocale().getLanguage().toLowerCase()
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
}

object WebUiProps extends WebUiProps with LongKeyedMetaMapper[WebUiProps] {
  override def dbIndexes = UniqueIndex(WebUiPropsId) :: UniqueIndex(Name) :: super.dbIndexes
}

