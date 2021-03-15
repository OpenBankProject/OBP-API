package code.api.v4_0_0.dynamic

import code.api.util.APIUtil.{OBPEndpoint, OBPReturnType, futureToBoxedResponse, scalaFutureToLaFuture}
import code.api.util.{CallContext, CustomJsonFormats}
import net.liftweb.common.Box
import net.liftweb.http.{JsonResponse, Req}

/**
 * this is super trait of dynamic compile endpoint, the dynamic compiled code should extends this trait and supply
 * logic of process method
 */
trait DynamicCompileEndpoint {
  implicit val formats = CustomJsonFormats.formats

  protected def process(callContext: CallContext, request: Req): Box[JsonResponse]

  val endpoint: OBPEndpoint = new OBPEndpoint {
    override def isDefinedAt(x: Req): Boolean = true

    override def apply(request: Req): CallContext => Box[JsonResponse] = process(_, request)
  }

  protected implicit def scalaFutureToBoxedJsonResponse[T](scf: OBPReturnType[T])(implicit m: Manifest[T]): Box[JsonResponse] = {
    futureToBoxedResponse(scalaFutureToLaFuture(scf))
  }
  protected def getPathParams(callContext: CallContext, request: Req): Map[String, String] = {
    val Some(resourceDoc) = callContext.resourceDocument
    resourceDoc.getPathParams(request.path.partPath)
  }
}
