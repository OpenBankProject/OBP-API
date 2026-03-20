package code.api.util

import code.api.util.APIUtil.rfc7231Date

import java.time.Duration

object DateTimeUtil {

  /*
    Examples:
    println(formatDuration(90000))   // "1 day, 1 hour"
    println(formatDuration(86400))   // "1 day"
    println(formatDuration(172800))  // "2 days"
    println(formatDuration(7200))    // "2 hours"
    println(formatDuration(3661))    // "1 hour, 1 minute, 1 second"
    println(formatDuration(120))     // "2 minutes"
    println(formatDuration(30))      // "30 seconds"
    println(formatDuration(0))       // "less than a second"
  */
  def formatDuration(seconds: Long): String = {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    def plural(value: Long, unit: String): Option[String] =
      if (value > 0) Some(s"$value ${unit}${if (value > 1) "s" else ""}") else None

    val parts = List(
      plural(days, "day"),
      plural(hours, "hour"),
      plural(minutes, "minute"),
      plural(secs, "second")
    ).flatten // Remove None values

    if (parts.isEmpty) "less than a second" else parts.mkString(", ")
  }

  // Define the correct RFC 7231 date format (IMF-fixdate)
  // Create a new instance per call to avoid SimpleDateFormat thread-safety issues
  private def newRfc7231Format = {
    val fmt = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.ENGLISH)
    fmt.setLenient(false)
    fmt
  }

  def isValidRfc7231Date(dateStr: String): Boolean = {
    try {
      newRfc7231Format.parse(dateStr)
      // Check that the timezone part is exactly "GMT"
      dateStr.endsWith(" GMT")
    } catch {
      case _: Exception => false
    }
  }

}
