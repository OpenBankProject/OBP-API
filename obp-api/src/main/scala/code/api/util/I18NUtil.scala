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

package code.api.util

import code.api.Constant.PARAM_LOCALE
import code.util.Helper.{MdcLoggable, ObpS, SILENCE_IS_GOLDEN}
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.model.enums.I18NResourceDocField

import java.util.{Date, Locale}

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
    ObpS.param(PARAM_LOCALE) match {
      // Use query parameter as a source of truth if any
      case net.liftweb.common.Full(requestedLocale) if requestedLocale != null && APIUtil.checkShortString(requestedLocale) == SILENCE_IS_GOLDEN =>
        I18NUtil.computeLocale(requestedLocale)
      case _ =>
        getDefaultLocale()
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
