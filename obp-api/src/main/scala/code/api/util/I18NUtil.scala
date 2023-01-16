package code.api.util

import code.api.Constant.PARAM_LOCALE

import java.util.{Date, Locale}

import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.model.enums.LanguageParam
import net.liftweb.common.Full
import net.liftweb.http.S
import net.liftweb.http.provider.HTTPCookie

object I18NUtil {
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
    S.param(PARAM_LOCALE) match {
      // 1st choice: Use query parameter as a source of truth if any
      case Full(requestedLocale) if requestedLocale != null => {
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
  }
  
  object ResourceDocTranslation {
    def summary(operationId: String, language: Option[LanguageParam], default: String): String = {
      language match {
        case Some(locale) =>
          val webUiKey = s"webui_resource_doc_operation_id_${operationId}_summary_${locale}"
          getWebUiPropsValue(webUiKey, default)
        case None =>
          default
      }
    }
    def description(operationId: String, language: Option[LanguageParam], default: String): String = {
      language match {
        case Some(locale) =>
          val webUiKey = s"webui_resource_doc_operation_id_${operationId}_description_${locale}"
          getWebUiPropsValue(webUiKey, default)
        case None =>
          default
      }
    }
  }
  

}
