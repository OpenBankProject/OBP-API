/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */

package code.api


import net.liftweb.http.JsonResponse
import net.liftweb.http.rest._
import net.liftweb.json.Extraction
import java.util.Date
import code.metrics.APIMetrics

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

//  serve("obp" / "metrics" prefix {
//    case "demo-bar" :: Nil JsonGet json => {
//
//      def byUsage(x : APICallAmount, y : APICallAmount) =
//        x.amount > y.amount
//
//      val results = APICallAmounts(APIMetrics.apiMetrics.vend.getAllGroupedByUrl().toSeq.map(t => APICallAmount(t._1,t._2.length)).toList.sortWith(byUsage))
//
//      JsonResponse(Extraction.decompose(results))
//    }
//
//    case "demo-line" :: Nil JsonGet json => {
//
//      def byOldestDate(x : APICallsForDay, y :  APICallsForDay) : Boolean =
//        x.date before y.date
//
//      val results  = APICallsPerDay(APIMetrics.apiMetrics.vend.getAllGroupedByDay().toSeq.map(t => APICallsForDay(t._2.length,t._1)).toList.sortWith(byOldestDate))
//      JsonResponse(Extraction.decompose(results))
//    }
//
//  })
}