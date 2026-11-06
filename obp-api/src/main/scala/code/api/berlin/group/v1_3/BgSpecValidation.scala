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

package code.api.berlin.group.v1_3

import code.api.util.APIUtil.DateWithDayFormat
import code.api.util.APIUtil.rfc7231Date
import code.api.util.ErrorMessages.InvalidDateFormat

import java.text.SimpleDateFormat
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{LocalDate, ZoneId}
import java.util.{Date, Locale}

object BgSpecValidation {

  val MaxValidDays: LocalDate = LocalDate.now().plusDays(180) // Max 180 days from today
  val DateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  def getErrorMessage(dateStr: String): String = {
    validateValidUntil(dateStr) match {
      case Right(_)  => ""
      case Left(err) => err
    }
  }

  def getDate(dateStr: String): Date = {
    validateValidUntil(dateStr) match {
      case Right(validDate) =>
        Date.from(validDate.atStartOfDay(ZoneId.systemDefault).toInstant)
      case Left(_) => null
    }
  }

  private def validateValidUntil(dateStr: String): Either[String, LocalDate] = {
    try {
      val date = LocalDate.parse(dateStr, DateFormat)
      val today = LocalDate.now()

      if (date.isBefore(today)) {
        Left(s"$InvalidDateFormat The `validUntil` date ($dateStr) cannot be in the past!")
      } else if (date.isAfter(MaxValidDays)) {
        Left(s"$InvalidDateFormat The `validUntil` date ($dateStr) exceeds the maximum allowed period of 180 days (until $MaxValidDays).")
      } else {
        Right(date) // Valid date (inclusive of 180 days)
      }
    } catch {
      case _: DateTimeParseException =>
        Left(s"$InvalidDateFormat The `validUntil` date ($dateStr) is invalid. Please use the format: ${DateWithDayFormat.toPattern}.")
    }
  }

  def formatToISODate(date: Date): String = {
    if (date == null) ""
    else {
      val localDate: LocalDate = date.toInstant.atZone(ZoneId.systemDefault()).toLocalDate
      localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
  }

}
