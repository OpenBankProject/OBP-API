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
  
  /**
   * Large parts of this should probably be moved outside of this class. Refactoring required!
   */
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
    
    /**
     * We accept the ApiPath as a parameter as it's not easy to get from the handler partial function (maybe with reflection?). We need to check that it's in fact
     * the same path as the one matched in the handler, and we can do this by mocking a request for apiPath and seeing if the handler partial function is defined for it.
     */
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

    /**
     * Note: TODO: exampleAnnotation should actually be ExampleValue (not very type safe at the moment)
     * 
     * Gets an example value from an ExampleValue, ensuring that the type of the value is that of requiredType
     */
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
    /**
     * Try to get a description of a type, first be checking for an ExampleValue annotation, and falling back on a less useful
     * description. The less useful description could actually be made more useful with some fake value. Anyhow, it copies a bunch
     * of code from getCaseClassAccessorsAsJsonString and needs (has it been mentioned enough?) refactoring.
     */
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

    /**
     * This method should be used if a case class accessor isn't annotated with an ExampleValue.
     * 
     * Provides a JValue with a string description of the type
     * 
     * TODO: Probably this should actually return some fake data rather than a type description
     * e.g. Booleans should return JBool(false) rather than JString("boolean")
     */
    def fallbackDescription(t : reflect.runtime.universe.Type): Option[JValue] = {
      
      t.typeSymbol match {
        case c : ClassSymbol => {
          c.fullName match {
            case "java.lang.String" => Some(JString("string"))
            case "scala.Boolean" => Some(JString("boolean"))
            case "java.util.Date" => Some(JString("date"))
            case "scala.Byte" | "scala.Short" | "scala.Int" | "scala.Double" | "scala.Float" | "scala.Long" | "scala.math.BigDecimal" | "java.math.BigDecimal" |
              "scala.math.BigInt" | "java.math.BigInt" => Some(JString("number"))
            case "scala.collection.immutable.List" => { //If it's a list, we should actually describe the type of the list
              t match {
                case TypeRef(_, _, args) => {
                  if(args.size == 1) { //Only do something if the list is parameterized with a single type
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
    
    /**
     * The idea here is to get back a json string with some sample data for a case class.
     * The implementation details are a bit messy/incomplete and need more work.
     * 
     * E.g. case class Bank(name: String, isOpenNow: Boolean)
     * 
     * could result in Some({"name" : "Example Name", "isOpenNow" : false })
     */
    def getCaseClassAccessorsAsJsonString[t: TypeTag]: Option[String] = {
      /**
       * caseAccessors is a list of json reprentations for each of the case fields of t
       * 
       * e.g. case class Bank(name: String, isOpenNow: Boolean, corpInfo : CorporateInfo)
       *      case class CorporateInfo(numEmployees : Int)
       *      
       * could result in something like List(("name" -> "Example Name"), ("isOpenNow" -> false), ("corpInfo" -> List(("numEmployees" -> 40000))))
       */
      val caseAccessors = typeOf[t].members.collect {
        case m: MethodSymbol if m.isCaseAccessor => m
      }.map(acc => {
        val returnType = acc.returnType
        val exampleValueAnnotation = acc.annotations.find(ann => {
          ann.tpe.typeSymbol.fullName == exampleValueFullName //picks out an annotation that is an ExampleValue
        })
        
        val exampleValue = exampleValueAnnotation.flatMap(getExampleValue(_, returnType))
        																					//primaryDescription isn't a very good name in this case...
        JField(acc.name.toString, exampleValue.getOrElse(fallbackDescription(returnType).getOrElse(primaryDescription(returnType).getOrElse(JString("")))))
      }).toList
      
      import net.liftweb.json.Printer._
      import net.liftweb.json.JsonAST.render
      
      caseAccessors.size match {
        case 0 => None
        case _ => Some(pretty(render(JObject(caseAccessors)))) //convert a JObject with fields caseAccessors into a string
      }

    }
    
    /**
     * Here we make sure apiPath (which was used to create testRequest) matches that of the handler partial function to register with lift
     */
    if(handler.isDefinedAt(testRequest)) {
      
      val apiCallDocumentation = new ApiCall {
        val path = ApiPath(apiPath)
        val requestType = reqType
        val docString = documentationString

        //Generate some sample json from the INPUT type (which should be the case class used to create json in the api)
        val inputJson = getCaseClassAccessorsAsJsonString[INPUT]
        //Generate some sample json from the INPUT type (which should be the case class used to create json in the api)
        val outputJson = getCaseClassAccessorsAsJsonString[OUTPUT]
      }
      
      //Assumes apiPath is of the format obp/API_VERSION/something
      val apiVersion = if(apiPath.size >= 2) Some(apiPath(1)) else None
      
      apiVersion match {
        case Some(v) => {
          //Register the documentation
          GeneratedDocumentation.addCall(v.name, apiCallDocumentation)
          //Actually allow the call to be accessed
          oauthServe(oauthHandler)
          //Below is just for debugging
          GeneratedDocumentation.apiVersion(v.name).foreach(_.apiCalls.foreach(x => println("in: " + x.inputJson + " out: " + x.outputJson)))
        }
        case None => logger.error("Bad api path could not be added: " + apiPath) //apiPath didn't match the expected format, so the version couldn't be extracted
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