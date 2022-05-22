package code.api.util

import java.util.{Date, Locale}

import net.liftweb.http.S
import net.liftweb.util.Props

object I18NUtil {
  // Copied from Sofit
  def getLocalDate(date: Date): String = {
    import java.text.DateFormat
    val df = DateFormat.getDateInstance(DateFormat.LONG, currentLocale())
    val formattedDate = df.format(date)
    formattedDate
  }

  def getLocale(): Locale = Locale.getAvailableLocales().toList.filter { l =>
    l.toLanguageTag == Props.get("language_tag", "en-GB")
  }.headOption.getOrElse(Locale.ENGLISH)
  
  def currentLocale() : Locale = {
    // Cookie name
    val localeCookieName = "SELECTED_LOCALE"
    S.findCookie(localeCookieName).flatMap {
      cookie => cookie.value.map(computeLocale)
    } openOr getLocale()
  }
  // Properly convert a language tag to a Locale
  def computeLocale(tag : String) = tag.split(Array('-', '_')) match {
    case Array(lang) => new Locale(lang)
    case Array(lang, country) => new Locale(lang, country)
    case Array(lang, country, variant) => new Locale(lang, country, variant)
  }

}
