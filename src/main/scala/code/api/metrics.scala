package code.api


import net.liftweb.http.JsonResponse
import net.liftweb.http.rest._
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST._
import _root_.net.liftweb.util.Helpers._
import code.model.dataAccess.APIMetric
import java.util.Date
import java.util.Calendar

case class APICallAmount(
  url: String,
  amount: Int
)
case class APICallAmounts(
  stats : List[APICallAmount]
)
case class APICallsForDay(
  amount : Int,
  date : Date
)
case class APICallsPerDay(
  stats : List[APICallsForDay]
)

object Metrics extends RestHelper {

  serve("obp" / "metrics" prefix {
    case "demo-bar" :: Nil JsonGet json => {
      def byURL(metric : APIMetric) : String =
        metric.url.get

      def byUsage(x : APICallAmount, y : APICallAmount) =
        x.amount > y.amount

      val results = APICallAmounts(APIMetric.findAll.groupBy[String](byURL).toSeq.map(t => APICallAmount(t._1,t._2.length)).toList.sortWith(byUsage))

      JsonResponse(Extraction.decompose(results))
    }

    case "demo-line" :: Nil JsonGet json => {

      def byDay(metric  : APIMetric) : Date = {
        val metricDate = metric.date.get
        val cal = Calendar.getInstance()
        cal.setTime(metricDate)
        cal.set(Calendar.HOUR,0)
        cal.set(Calendar.MINUTE,0)
        cal.set(Calendar.SECOND,0)
        cal.set(Calendar.MILLISECOND,0)
        cal.getTime
       }

      def byOldestDate(x : APICallsForDay, y :  APICallsForDay) : Boolean =
        x.date before y.date

      val results  = APICallsPerDay(APIMetric.findAll.groupBy[Date](byDay).toSeq.map(t => APICallsForDay(t._2.length,t._1)).toList.sortWith(byOldestDate))
      JsonResponse(Extraction.decompose(results))
    }

  })
}