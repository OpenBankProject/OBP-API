package code.api.util

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Failure}
import net.liftweb.json._


object ErrorUtil {
  def apiFailure(errorMessage: String, httpCode: Int)(forwardResult: (Box[User], Option[CallContext])): (Box[User], Option[CallContext]) = {
    val (_, second) = forwardResult
    val apiFailure = APIFailureNewStyle(
      failMsg = errorMessage,
      failCode = httpCode,
      ccl = second.map(_.toLight)
    )
    val failureBox = Empty ~> apiFailure
    (
      fullBoxOrException(failureBox),
      second
    )
  }

  def apiFailureToBox[T](errorMessage: String, httpCode: Int)(cc: Option[CallContext]): Box[T] = {
    val apiFailure = APIFailureNewStyle(
      failMsg = errorMessage,
      failCode = httpCode,
      ccl = cc.map(_.toLight)
    )
    val failureBox: Box[T] = Empty ~> apiFailure
    fullBoxOrException(failureBox)
  }



  implicit val formats: Formats = DefaultFormats
  def extractFailureMessage(e: Throwable): String = {
    parse(e.getMessage)
      .extractOpt[APIFailureNewStyle] // Extract message from APIFailureNewStyle
      .map(_.failMsg) // or provide a original one
      .getOrElse(e.getMessage)
  }


}
