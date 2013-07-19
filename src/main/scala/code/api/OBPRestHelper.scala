package code.api

import net.liftweb.http.rest.RestHelper
import net.liftweb.http.Req
import net.liftweb.common._
import net.liftweb.http.LiftResponse
import net.liftweb.http.JsonResponse
import code.util.APIUtil._
import code.model.User
import code.api.OAuthHandshake._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.http.RequestType
import net.liftweb.http.ParsePath
import net.liftweb.http.LiftRules
import net.liftweb.http.ParamCalcInfo
import net.liftweb.http.provider.HTTPRequest
import net.liftweb.http.provider.servlet.HTTPRequestServlet
import net.liftweb.http.LiftServlet
import javax.servlet.http.HttpServletRequest
import net.liftweb.json.Extraction
import net.liftweb.util.Helpers.tryo
import scala.reflect.runtime.universe._
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonAST.JField
import java.util.Date
import scala.math.BigInt
import net.liftweb.json.JsonAST.JArray
import net.liftweb.json.JsonAST.JDouble
import net.liftweb.json.JsonAST.JInt
import net.liftweb.json.JsonAST.JBool
import code.model.ModeratedTransaction
import net.liftweb.json.DefaultFormats

trait PathElement {
    def name : String
  }

case class StaticElement(name : String) extends PathElement
case class VariableElement(name : String) extends PathElement

class OBPRestHelper extends RestHelper with Loggable {

  implicit def jsonResponseBoxToJsonReponse(box: Box[JsonResponse]): JsonResponse = {
    box match {
      case Full(r) => r
      case Failure(msg, _, _) => {
        logger.info("API Failure: " + msg)
        errorJsonResponse(msg)
      }
      case _ => errorJsonResponse()
    }
  }

  def failIfBadOauth(fn: (Box[User]) => Box[JsonResponse]) : JsonResponse = {
    if (isThereAnOAuthHeader) {
      getUser match {
        case Full(u) => fn(Full(u))
        case Failure(msg, _, _) => errorJsonResponse(msg)
        case _ => errorJsonResponse("oauth error")
      }
    } else fn(Empty)
  }

  class RichStringList(list: List[String]) {
    val listLen = list.length

    /**
     * Normally we would use ListServeMagic's prefix function, but it works with PartialFunction[Req, () => Box[LiftResponse]]
     * instead of the PartialFunction[Req, Box[User] => Box[JsonResponse]] that we need. This function does the same thing, really.
     */
    def oPrefix(pf: PartialFunction[Req, Box[User] => Box[JsonResponse]]): PartialFunction[Req, Box[User] => Box[JsonResponse]] =
      new PartialFunction[Req, Box[User] => Box[JsonResponse]] {
        def isDefinedAt(req: Req): Boolean =
          req.path.partPath.startsWith(list) && {
            pf.isDefinedAt(req.withNewPath(req.path.drop(listLen)))
          }

        def apply(req: Req): Box[User] => Box[JsonResponse] =
          pf.apply(req.withNewPath(req.path.drop(listLen)))
      }
    
    def oPrefix2[IN, OUT](pf: PartialFunction[Req, (Box[User], Box[IN]) => Box[OUT]]): PartialFunction[Req, (Box[User], Box[IN]) => Box[OUT]] =
      new PartialFunction[Req, (Box[User], Box[IN]) => Box[OUT]] {
        def isDefinedAt(req: Req): Boolean =
          req.path.partPath.startsWith(list) && {
            pf.isDefinedAt(req.withNewPath(req.path.drop(listLen)))
          }

        def apply(req: Req): (Box[User], Box[IN]) => Box[OUT] =
          pf.apply(req.withNewPath(req.path.drop(listLen)))
      }
  }
  
  type ApiPath = List[PathElement]
  
  def caseClassBoxToJsonResponse[T](output : Box[T]) : Box[JsonResponse] = {
    output.map(x => successJsonResponse(Extraction.decompose(x)))
  }
  
  val exampleValueFullName = typeOf[ExampleValue[String]].typeSymbol.fullName
  
  def registerApiCall[INPUT : TypeTag, OUTPUT](apiPath : ApiPath, reqType : RequestType, documentationString: String, handler : PartialFunction[Req, (Box[User], Box[INPUT]) => Box[OUTPUT]])
  (implicit m: TypeTag[OUTPUT], m2 : Manifest[INPUT]) = {
    
    val testPath : List[String] = apiPath.map{
      case StaticElement(name) => name
      case VariableElement(_) => "test"
    }
    
    val reqPath = ParsePath(testPath, "", true, false)
    import net.liftweb.http.provider.HTTPProvider
    
    //Some bits of httpRequest have to be mocked to avoid exceptions in handler.isDefinedAt(testRequest)
    import org.mockito.Mockito._
    val httpRequest : HTTPRequest = mock(classOf[HTTPRequest])
    when(httpRequest.contentType).thenReturn(Full("application/json"))
    when(httpRequest.headers).thenReturn(Nil)
    
    val testRequest : Req = new Req(reqPath, LiftRules.context.path, reqType,
        Full("application/json"), httpRequest, 5, 6, () => ParamCalcInfo(Nil, Map(), Nil, Empty), Map())
    

    //Convert the handler into the more general Box[User] => Box[JsonResponse] that oauthServe expects
    val oauthHandler = new PartialFunction[Req, Box[User] => Box[JsonResponse]] {
      def isDefinedAt(req: Req) : Boolean = handler.isDefinedAt(req)
      
      def apply(req: Req) : Box[User] => Box[JsonResponse] = {
        val json = req.json
        import net.liftweb.json._
        val input = for {
          j <- json
          in <- tryo{j.extract[INPUT]} //TODO: what if no input is expected? Nothing seems to be a bad idea since it doesn't actually exist in java and gets erased (to Object?)
        } yield in
        //val in = tryo{json.map(_.extract[INPUT](DefaultFormats, m2))}.getOrElse(Empty)
        (user: Box[User]) => caseClassBoxToJsonResponse(handler.apply(req).apply(user, input))
      }
    }

    def getExampleValue(exampleAnnotation: Annotation, requiredType: Type): Option[JValue] = {
      //Check that for ExampleValue[T], T is the same type as returnType
      val args = exampleAnnotation.scalaArgs
      if (args.size == 1) {
        val arg = args(0)
        if (arg.tpe =:= requiredType) {
          val exampleValueArg = tryo { arg.productElement(0) }
          exampleValueArg.flatMap(x => x match {
            case Constant(s: String) => Some(JString(s))
            case Constant(d: Double) => Some(JDouble(d))
            case Constant(i: Int) => Some(JInt(i))
            case Constant(b: Boolean) => Some(JBool(b))
            //TODO: Doesn't work for Date (it's not a constant/primitive)
            case Constant(d: Date) => Some(JString(DefaultFormats.dateFormat.format(d))) //TODO: Need a better way to make sure we're using the right format
            case other => {
              //TODO: Extract ExampleValue.example here and check if it's a Date, and if so, do something like Some(JString(DefaultFormats.dateFormat.format(d)))
              
              None : Option[JValue]
            }
          })
        } else None
      } else None
    }

    //TODO: Large amounts of refactoring
    def primaryDescription(bar: reflect.runtime.universe.Type): Option[JValue] = {
      val caseAccessors = bar.members.collect {
        case m: MethodSymbol if m.isCaseAccessor => m
      }.map(acc => {
        val returnType = acc.returnType
        val exampleValueAnnotation = acc.annotations.find(ann => {
          ann.tpe.typeSymbol.fullName == exampleValueFullName
        })
        
        val exampleValue = exampleValueAnnotation.flatMap(getExampleValue(_, returnType))
        
        JField(acc.name.toString, exampleValue.getOrElse(fallbackDescription(returnType).getOrElse(primaryDescription(returnType).getOrElse(JString("")))))
      }).toList
      
      caseAccessors.size match {
        case 0 => None
        case _ => Some(JObject(caseAccessors))
      }
    }

    def fallbackDescription(t : reflect.runtime.universe.Type): Option[JValue] = {
      
      t.typeSymbol match {
        case c : ClassSymbol => {
          c.fullName match {
            case "java.lang.String" => Some(JString("string"))
            case "scala.Boolean" => Some(JString("boolean"))
            case "java.util.Date" => Some(JString("date"))
            case "scala.Byte" | "scala.Short" | "scala.Int" | "scala.Double" | "scala.Float" | "scala.Long" | "scala.math.BigDecimal" | "java.math.BigDecimal" |
              "scala.math.BigInt" | "java.math.BigInt" => Some(JString("number"))
            case "scala.collection.immutable.List" => {
              t match {
                case TypeRef(_, _, args) => {
                  if(args.size == 1) {
                    val listType = args(0)
                    Some(JArray(List(primaryDescription(listType)).flatten))
                  } else {
                    logger.warn("unexpected number of type arguments for a list!")
                    None
                  }
                }
                case _ => {
                  logger.warn("list was somehow not a typeref")
                  None
                }
              }
            }
            case _ => None
          }
        }
      }
    }
    
    def getCaseClassAccessorsAsJsonString[t: TypeTag]: Option[String] = {
      val caseAccessors = typeOf[t].members.collect {
        case m: MethodSymbol if m.isCaseAccessor => m
      }.map(acc => {
        val returnType = acc.returnType
        val exampleValueAnnotation = acc.annotations.find(ann => {
          ann.tpe.typeSymbol.fullName == exampleValueFullName
        })
        
        val exampleValue = exampleValueAnnotation.flatMap(getExampleValue(_, returnType))
        
        JField(acc.name.toString, exampleValue.getOrElse(fallbackDescription(returnType).getOrElse(primaryDescription(returnType).getOrElse(JString("")))))
      }).toList
      
      import net.liftweb.json.Printer._
      import net.liftweb.json.JsonAST.render
      
      caseAccessors.size match {
        case 0 => None
        case _ => Some(pretty(render(JObject(caseAccessors))))
      }

    }
    
    if(handler.isDefinedAt(testRequest)) {
      
      val apiCallDocumentation = new ApiCall {
        val path = ApiPath(apiPath)
        val requestType = reqType
        val docString = documentationString

        val inputJson = getCaseClassAccessorsAsJsonString[INPUT]
        val outputJson = getCaseClassAccessorsAsJsonString[OUTPUT]
      }
      
      val apiVersion = if(apiPath.size >= 2) Some(apiPath(1)) else None
      
      apiVersion match {
        case Some(v) => {
          GeneratedDocumentation.addCall(v.name, apiCallDocumentation)
          oauthServe(oauthHandler)
          GeneratedDocumentation.apiVersion(v.name).foreach(_.apiCalls.foreach(x => println("in: " + x.inputJson + " out: " + x.outputJson)))
        }
        case None => logger.error("Bad api path could not be added: " + apiPath)
      }
      
    }
    else {
      logger.error("Api call did not fulfill documented behaviour! Path " + 
          apiPath + " with request type " + reqType + " did not match supplied partialfunction.") //TODO: describe which api call
    }
    
  }

  //Give all lists of strings in OBPRestHelpers the oPrefix method
  implicit def stringListToRichStringList(list : List[String]) : RichStringList = new RichStringList(list)

  //TODO: Fold this into registerApiCall once that method is properly implemented
  def oauthServe(handler : PartialFunction[Req, Box[User] => Box[JsonResponse]]) : Unit = {
    val obpHandler : PartialFunction[Req, () => Box[LiftResponse]] = {
      new PartialFunction[Req, () => Box[LiftResponse]] {
        def apply(r : Req) = {
          failIfBadOauth {
            handler(r)
          }
        }
        def isDefinedAt(r : Req) = handler.isDefinedAt(r)
      }
    }
    serve(obpHandler)
  }

  override protected def serve(handler: PartialFunction[Req, () => Box[LiftResponse]]) : Unit= {

    val obpHandler : PartialFunction[Req, () => Box[LiftResponse]] = {
      new PartialFunction[Req, () => Box[LiftResponse]] {
        def apply(r : Req) = {
          //Wraps the partial function with some logging
          logAPICall
          handler(r)
        }
        def isDefinedAt(r : Req) = handler.isDefinedAt(r)
      }
    }
    super.serve(obpHandler)
  }


}