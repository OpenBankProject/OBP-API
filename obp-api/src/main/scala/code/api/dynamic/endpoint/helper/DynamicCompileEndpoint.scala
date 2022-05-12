package code.api.dynamic.endpoint.helper

import code.api.util.APIUtil.{OBPEndpoint, OBPReturnType, futureToBoxedResponse, scalaFutureToLaFuture}
import code.api.util.DynamicUtil.{Sandbox, Validation}
import code.api.util.{CallContext, CustomJsonFormats, DynamicUtil}
import net.liftweb.common.Box
import net.liftweb.http.{JsonResponse, Req}

/**
 * this is super trait of dynamic compile endpoint, the dynamic compiled code should extends this trait and supply
 * logic of process method
 */
trait DynamicCompileEndpoint {
  implicit val formats = CustomJsonFormats.formats

  // * is any bankId
  val boundBankId: String

  protected def process(callContext: CallContext, request: Req, pathParams: Map[String, String]): Box[JsonResponse]

  val endpoint: OBPEndpoint = new OBPEndpoint {
    override def isDefinedAt(x: Req): Boolean = true

    override def apply(request: Req): CallContext => Box[JsonResponse] = { cc =>
      val Some(pathParams) = cc.resourceDocument.map(_.getPathParams(request.path.partPath))

      validateDependencies()

      Sandbox.sandbox(boundBankId).runInSandbox {
        process(cc, request, pathParams)
      }

    }
  }

  private def validateDependencies() = {
    val dependencies = DynamicUtil.getDynamicCodeDependentMethods(this.getClass, "process" == )
    Validation.validateDependency(dependencies)
  }
}

object DynamicCompileEndpoint {
   implicit def scalaFutureToBoxedJsonResponse[T](scf: OBPReturnType[T])(implicit m: Manifest[T]): Box[JsonResponse] = {
    futureToBoxedResponse(scalaFutureToLaFuture(scf))
  }
}