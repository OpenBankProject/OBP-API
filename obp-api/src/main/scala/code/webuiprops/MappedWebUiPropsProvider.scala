package code.webuiprops

import code.api.cache.Caching
import code.api.util.APIUtil.{activeBrand, generateUUID, writeMetricEndpointTiming}
import code.api.util.{APIUtil, DoobieUtil, ErrorMessages, I18NUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full}


/**
  * props name start with "webui_" can set in to db, this module just support the webui_ props CRUD
  */
object MappedWebUiPropsProvider extends WebUiPropsProvider {
  // default webUiProps value cached seconds
  private val webUiPropsTTL = APIUtil.getPropsAsIntValue("webui.props.cache.ttl.seconds", 0)

  private def fromRow(row: (String, String, String)): WebUiPropsT =
    row match {
      case (webUiPropsId, name, value) => WebUiPropsCommons(name, value, Some(webUiPropsId), Some("database"))
    }

  override def getAll(): List[WebUiPropsT] =
    DoobieUtil.runQuery(
      sql"SELECT webuipropsid, name, value FROM webuiprops".query[(String, String, String)].to[List]
    ).map(fromRow)

  override def getByName(name: String): Box[WebUiPropsT] =
    DoobieUtil.runQuery(
      sql"SELECT webuipropsid, name, value FROM webuiprops WHERE name = $name".query[(String, String, String)].option
    ) match {
      case Some(row) => Full(fromRow(row))
      case None => Empty
    }

  override def createOrUpdate(webUiProps: WebUiPropsT): Box[WebUiPropsT] = {
    val trimmedName = webUiProps.name.trim()
    getByName(trimmedName) match {
      case Full(existing) =>
        DoobieUtil.runUpdate(
          sql"UPDATE webuiprops SET value = ${webUiProps.value} WHERE name = $trimmedName".update.run)
        Full(WebUiPropsCommons(trimmedName, webUiProps.value, existing.webUiPropsId, Some("database")))
      case _ =>
        val newId = generateUUID()
        DoobieUtil.runUpdate(
          sql"INSERT INTO webuiprops (webuipropsid, name, value) VALUES ($newId, $trimmedName, ${webUiProps.value})".update.run)
        Full(WebUiPropsCommons(trimmedName, webUiProps.value, Some(newId), Some("database")))
    }
  }

  override def delete(webUiPropsId: String): Box[Boolean] =
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM webuiprops WHERE webuipropsid = $webUiPropsId".query[Int].unique
    ) match {
      case count if count > 0 =>
        DoobieUtil.runUpdate(sql"DELETE FROM webuiprops WHERE webuipropsid = $webUiPropsId".update.run)
        Full(true)
      case _ => Failure(ErrorMessages.WebUiPropsNotFound)
    }

  // Rules to obtain the WebUI props value
  // 1) Get requested + brand + language if any
  // 2) Get requested + language if any
  // 3) Get requested if any
  // 4) Get default value
  override def getWebUiPropsValue(requestedPropertyName: String, defaultValue: String, language: String = I18NUtil.currentLocale().toString()): String = writeMetricEndpointTiming {
    import scala.concurrent.duration._
    val cacheKey = ("code.webuiprops.MappedWebUiPropsProvider", "getWebUiPropsValue", List(requestedPropertyName, defaultValue, language).mkString("_"))
    Caching.memoizeSyncWithImMemory(Some(cacheKey.toString()))(webUiPropsTTL.second) {
      // If we have an active brand, construct a target property name to look for.
      val brandSpecificPropertyName = activeBrand() match {
        case Some(brand) => s"${requestedPropertyName}_FOR_BRAND_${brand}"
        case _ => requestedPropertyName
      }

      // In case there is a translation we must use it
      val webUiPropsPropertyName = s"${brandSpecificPropertyName}_${language}"
      val translatedAndOrBrandPropertyName = getByName(webUiPropsPropertyName).isDefined match {
        case true => webUiPropsPropertyName
        case false => brandSpecificPropertyName
      }

      getByName(translatedAndOrBrandPropertyName).map(_.value) // Get translated and/or brand specific value if any
        .or(getByName(requestedPropertyName).map(_.value)) // Get requested value if any
        .openOr {
          APIUtil.getPropsValue(requestedPropertyName, defaultValue) // Otherwise return the default value
        }
    }
  }("getWebUiProps")("MappedWebUiPropsProvider")

}
