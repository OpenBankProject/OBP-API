package code.webuiprops

import java.util.UUID.randomUUID

import code.api.cache.Caching
import code.api.util.APIUtil
import code.api.util.APIUtil.saveConnectorMetric
import code.util.MappedUUID
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._

/**
  * props name start with "webui_" can set in to db, this module just support the webui_ props CURD
  */
object MappedWebUiPropsProvider extends WebUiPropsProvider {
  // default webUiProps value cached seconds
  private val webUiPropsTTL = APIUtil.getPropsAsIntValue("webui.props.cache.ttl.seconds", 0)

  override def getAll(): List[WebUiPropsT] =  WebUiProps.findAll()


  override def createOrUpdate(webUiProps: WebUiPropsT): Box[WebUiPropsT] = {
      WebUiProps.find(By(WebUiProps.Name, webUiProps.name))
      .or(Full(WebUiProps.create))
      .map(_.Name(webUiProps.name).Value(webUiProps.value).saveMe())
  }

  override def delete(webUiPropsId: String):Box[Boolean] = WebUiProps.find(By(WebUiProps.WebUiPropsId, webUiPropsId)).map(_.delete_!)

  override def getWebUiPropsValue(nameOfProperty: String, defaultValue: String): String = saveConnectorMetric {
    import scala.concurrent.duration._
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(webUiPropsTTL second) {
        try { //We need call this method without database, so just catch exception and others will also throw exception. 
          WebUiProps.find(By(WebUiProps.Name, nameOfProperty))
            .map(_.value)
            .openOr {
              APIUtil.getPropsValue(nameOfProperty, defaultValue)
            }
        } catch {
          //java.lang.NullPointerException: Looking for Connection Identifier ConnectionIdentifier(lift) but failed to find either a JNDI data 
          // source with the name lift or a lift connection manager with the correct name
          // Only handle this exception. no others.
          case exception: NullPointerException if(exception.getMessage.contains("failed to find either a JNDI data source"))=> 
            APIUtil.getPropsValue(nameOfProperty, defaultValue)
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
  override def dbIndexes = UniqueIndex(WebUiPropsId) :: super.dbIndexes
}

