package code.api.util

import code.api.util.APIUtil.OBPEndpoint
import net.liftweb.http.Req

import scala.reflect.runtime.{universe => ru}

object CallContextFilter {

  type Filter = (CallContext, Req) => CallContext

  /**
    * when you need new filter, just add val as follow, and modify "obp.api.context.filters" in default.props
    * e.g:
    *   obp.api.context.filters=filterHello, filterWorld
    */

  private[this] val filterAuthInfo: Filter = (callContext, _) => {
    callContext.copy(authInfo = APIUtil.getAuthInfo(Some(callContext)))
  }

  private[this] val defaultFilter: Filter = (callContext, _) => {
    callContext
  }
  
  lazy val registerFilters = {
    val filterNames = APIUtil.getPropsValue("obp.api.context.filters", "defaultFilter").split("\\s*,\\s*")
    val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader)
    val instanceMirror = mirror.reflect(CallContextFilter)

    val info = instanceMirror.symbol.asType.info
    filterNames.map(it => info.decl(ru.TermName(it)).asTerm)
      .map(it => instanceMirror.reflectField(it))
      .map(it => it.get.asInstanceOf[Filter])
  }

  def wrapOBPEndpoint(f: OBPEndpoint):OBPEndpoint = {
    case request => {context =>
      val newContext = registerFilters.foldLeft(context)((ct, filter)=> filter(ct, request))
      f(request)(newContext)
    }
  }
}
