package code.api.berlin.group.v1_3

import code.api.util.APIUtil.DateWithDayFormat
import code.api.util.APIUtil.rfc7231Date
import code.api.util.ErrorMessages.InvalidDateFormat

import java.text.SimpleDateFormat
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{LocalDate, ZoneId}
import java.util.{Date, Locale}

object BgSpecValidation {

  //val MaxValidDays: LocalDate = LocalDate.now().plusDaysCustom(180) // Max 180 days from today
  //FOR TEST
  val MaxValidDays: LocalDate = BgSpecValidation.plusDaysCustom(LocalDate.now(), 180) // Max 180 days from today
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
        Left(s"$InvalidDateFormat The `validUntil` date ($dateStr) exceeds the maximum allowed period of 180 days (until $MaxValidDays). today: ($today)")
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

  def plusDaysCustom(date: LocalDate, daysToAdd: Long): LocalDate = {
    if (daysToAdd == 0) return date

    var newDate = date
    var remainingDays = daysToAdd

    // Предположим, что у нас есть методы для получения длины месяца и дня месяца
    while (remainingDays != 0) {
      val daysInCurrentMonth = newDate.lengthOfMonth() - newDate.getDayOfMonth()

      // Если оставшихся дней меньше, чем до конца текущего месяца
      if (remainingDays <= daysInCurrentMonth) {
        return newDate.plusDays(remainingDays)
      } else {
        // Переходим на следующий месяц
        remainingDays -= daysInCurrentMonth + 1
        newDate = newDate.plusMonths(1).withDayOfMonth(1)  // Переходим на 1-е число следующего месяца
      }
    }
    newDate
  }
}
