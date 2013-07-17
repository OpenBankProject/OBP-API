package code.snippet

import net.liftweb.util.Helpers._
import code.api.GeneratedDocumentation
import code.api.ApiVersionDocumentation
import code.api.ApiPath
import net.liftweb.http.RequestType

class ApiDocumentation(documentation : ApiVersionDocumentation) {

  def apiInfo = {
    ".version * " #> documentation.version
  }
  
  def pathAsString(path : ApiPath) = {
    path.pathElements.foldLeft("")(_ + "/" + _.name)
  }
  
  def calls = {
    
    ".call *" #> documentation.apiCalls.map(call => {
      ".call-url *" #> pathAsString(call.path) &
      ".input-json *" #> call.inputJson.getOrElse("No Input") &
      ".output-json *" #> call.outputJson.getOrElse("No output") &
      ".request-type *" #> call.requestType.method &
      ".docstring *" #> call.docString
    })
    
  }
  
}