package code.api.util

import code.api.Constant.PARAM_LOCALE
import code.util.Helper.{MdcLoggable, ObpS, SILENCE_IS_GOLDEN}

import java.util.{Date, Locale}

import code.util.Helper.MdcLoggable
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.model.enums.I18NResourceDocField
import net.liftweb.common.Full
import net.liftweb.http.S
import net.liftweb.http.provider.HTTPCookie

object I18NUtil extends MdcLoggable {
  // Copied from Sofit
  def getLocalDate(date: Date): String = {
    import java.text.DateFormat
    val df = DateFormat.getDateInstance(DateFormat.LONG, currentLocale())
    val formattedDate = df.format(date)
    formattedDate
  }

  def getDefaultLocale(): Locale = Locale.getAvailableLocales().toList.filter { l =>
    l.toString == ApiPropsWithAlias.defaultLocale || // this will support underscore
      l.toLanguageTag == ApiPropsWithAlias.defaultLocale // this will support hyphen
  }.headOption.getOrElse(new Locale(ApiPropsWithAlias.defaultLocale))
  
  def currentLocale() : Locale = {
    // Cookie name
    val localeCookieName = "SELECTED_LOCALE"
    ObpS.param(PARAM_LOCALE) match {
      // 1st choice: Use query parameter as a source of truth if any
      case Full(requestedLocale) if requestedLocale != null && APIUtil.checkShortString(requestedLocale) == SILENCE_IS_GOLDEN => {
        val computedLocale = I18NUtil.computeLocale(requestedLocale)
        S.addCookie(HTTPCookie(localeCookieName, requestedLocale))
        computedLocale
      }
      // 2nd choice: Otherwise use the cookie  
      case _ =>
        S.findCookie(localeCookieName).flatMap {
          cookie => cookie.value.map(computeLocale)
        } openOr getDefaultLocale()
    }
  }
  // Properly convert a language tag to a Locale
  def computeLocale(tag : String) = tag.split(Array('-', '_')) match {
    case Array(lang) => new Locale(lang)
    case Array(lang, country) => new Locale(lang, country)
    case Array(lang, country, variant) => new Locale(lang, country, variant)
    case _ => 
      val locale = getDefaultLocale()
      logger.warn(s"Cannot parse the string $tag to Locale. Use default value: ${locale.toString()}")
      locale
  }
  
  object ResourceDocTranslation {
    def translate(fieldName: I18NResourceDocField.Value, operationId: String, locale: Option[String], default: String): String = {
      locale match {
        case Some(locale)=>
          val webUiKeyString = "webui_resource_doc_operation_id_"
          val webUiKey = s"$webUiKeyString${operationId}_${fieldName.toString.toLowerCase}_${locale}"
          getWebUiPropsValue(webUiKey, default)
        case None =>
          default
      }
    }
  }
  

}
