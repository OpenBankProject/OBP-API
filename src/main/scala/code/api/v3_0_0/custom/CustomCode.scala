package code.api.v3_0_0.custom

import java.io.File
import java.nio.file.Files

import scala.meta._
import net.liftweb.json
import net.liftweb.json.JsonAST.JField
import net.liftweb.json.{JValue, JsonAST}
import scala.collection.immutable.Map.Map2

object CustomCode extends App
{
  val jsonString = scala.io.Source.fromFile("src/main/scala/code/api/v3_0_0/custom/newAPis.json").mkString 
  
  val jsonObject: JValue = json.parse(jsonString)
  val newApiSummary: String = (jsonObject \\ "summary").values.head._2.toString
  val newApiDescription: String = (jsonObject \\ "description").values.head._2.toString 
  //TODO, for now this is only in description, could be a single filed later.
  val needAuthentication:Boolean = newApiDescription.contains("Authentication is Mandatory")
  val newApiURl: String = (jsonObject \\ "request_url").values.head._2.toString //eg: my/book
  val newApiResponseBody: JValue= jsonObject \\ "success_response_body"
  
  
  val needAuthenticationStatement: Term.ApplyInfix = needAuthentication match {
    case true => q"cc.user ?~ UserNotLoggedIn"
    case false => q"Box(1) ?~ UserNotLoggedIn" //This will not throw error, only a placeholder 
  }
  
  
  val jsonFieldname = newApiResponseBody.children.head.asInstanceOf[JsonAST.JObject].obj.head.name.toLowerCase.capitalize

  val jsonFieldValue =s"List[$jsonFieldname]" // List[Books]
  val jsonFieldDefaultValue = s"List($jsonFieldname())" //List(Books())
  
  
  val secondLevelFiledNames: List[String] = newApiResponseBody.children.head.asInstanceOf[JsonAST.JObject].obj.head.value.asInstanceOf[JsonAST.JArray].children.head.asInstanceOf[JsonAST.JObject].obj.map(_.name)
  val secondLevelFiledTypes: List[String] = secondLevelFiledNames.map(key => newApiResponseBody.findField{
         case JField(n, v) => n == key
       }).map(_.get.value.getClass.getSimpleName.replaceFirst("J","")).toList
  
  
  
  val secondLevelFiledTypes2: List[Any] = secondLevelFiledNames.map(key => newApiResponseBody.findField{
         case JField(n, v) => n == key
       }).map(_.get.value.values).toList
  
  val SecondLevelCaseFieldNames: List[Term.Param] = { 
    val fieldNames = for{
    a <- 0 until secondLevelFiledNames.size
      } yield Term.Param(Nil, Term.Name(secondLevelFiledNames(a).toLowerCase), Some(Type.Name(secondLevelFiledTypes(a))), Some(Term.Name(s"${secondLevelFiledTypes2(a)}")))
    fieldNames.toList
  } 
  
  
  val RootFiledName = Type.Name("RootInterface")
  val FirstLevelCaseClassFiledName = List(Term.Param(Nil, Term.Name(jsonFieldname.toLowerCase), Some(Type.Name(jsonFieldValue)), Some(Term.Name(jsonFieldDefaultValue))))
  val SecondLevelCaseClassName = Type.Name(jsonFieldname)
  
  val SecondLevelCaseClass: Defn.Class = q"""case class $SecondLevelCaseClassName(..$SecondLevelCaseFieldNames) """
  val FirstLevelCaseClass: Defn.Class = q"""case class $RootFiledName(..$FirstLevelCaseClassFiledName) """ //case class Test(banks: List[Banks])
  
  val instanceRootCaseClass: Defn.Val = q"val rootInterface = RootInterface()"
  val getForComprehensionBody: Term.ForYield = 
    q"""for {
      u <- $needAuthenticationStatement 
      jsonString = scala.io.Source.fromFile("src/main/scala/code/api/v3_0_0/custom/newAPis.json").mkString 
      jsonObject: JValue = json.parse(jsonString)\\"success_response_body"
    } yield {
      successJsonResponse(jsonObject)
    }"""
  
  
  val newUrlForResourceDoc = q""" "/books" """.copy(s"/custom/$newApiURl")
  val newUrlDescriptionForResourceDoc = q""" "" """.copy(s"$newApiDescription")
  val newUrlSummaryForResourceDoc = q""" "" """.copy(s"$newApiSummary")
  
  //val termExample: Term.Apply = q""" Test() """.copy(s"${RootFiledName.value}".parse[Term].get)
  
  val fixedResourceCode: Term.ApplyInfix = 
    q"""
      resourceDocs += ResourceDoc(
        getBooks, 
        apiVersion, 
        "getBooks", 
        "GET", 
        $newUrlForResourceDoc, 
        $newUrlSummaryForResourceDoc, 
        $newUrlDescriptionForResourceDoc, 
        emptyObjectJson, 
        rootInterface, 
        List(UnknownError), 
        Catalogs(Core, notPSD2, OBWG), 
        apiTagBank :: Nil
      )"""
  
  
  
  
  //TODO, escape issue:return the space, I added quotes in the end: allSourceCode.syntax.replaceAll("""  ::  """,""""  ::  """")
  //from "my/book" --> "my  ::  book" 
  val newApiUrlLiftFormat = newApiURl.split("/").mkString("""""","""  ::  """, """""")
  val newURL: Lit.String = q""" "books"  """.copy(newApiUrlLiftFormat)
  val addedPartialFunction: Defn.Val = 
    q"""
      lazy val getBooks: OBPEndpoint = {
        case ("custom" :: $newURL :: Nil) JsonGet req =>
          cc => {
            $getForComprehensionBody
          }
       }"""
  
  val addedEndpoitList = 
    q"""
       def endpointsOfCustom3_0_0 = createTransactionRequestTransferToReferenceAccountCustom :: getBooks::Nil"""
  
  val apiSource: Source = 
    source""" 
        
      package code.api.v3_0_0.custom

      import code.api.util.ApiVersion
      import code.api.util.ErrorMessages._
      import net.liftweb.http.rest.RestHelper
      import scala.collection.immutable.Nil
      import scala.collection.mutable.ArrayBuffer
      import code.api.util.APIUtil._
      import net.liftweb.json
      import net.liftweb.json.JValue
      import code.api.v3_0_0.custom.JSONFactoryCustom300._
      
      trait CustomAPIMethods300 { 
        self: RestHelper =>
          val ImplementationsCustom3_0_0 = new Object() {
          val apiVersion: ApiVersion = ApiVersion.v3_0_0
          val resourceDocs = ArrayBuffer[ResourceDoc]()
          val apiRelations = ArrayBuffer[ApiRelation]()
          val codeContext = CodeContext(resourceDocs, apiRelations)
          val createTransactionRequestTransferToReferenceAccountCustom = null
          
          $addedEndpoitList
          $fixedResourceCode
          $addedPartialFunction
        }
      }
"""
  
  val jfile = new File("src/main/scala/code/api/v3_0_0/custom/APIMethodsCustom300.scala")
  jfile.getParentFile.mkdirs()
  Files.write(
    jfile.toPath,
    apiSource.syntax.replaceAll("""  ::  """,""""  ::  """").getBytes("UTF-8")
  )
  
  val jsonFactorySource: Source = 
    source""" 
      package code.api.v3_0_0.custom
      import code.api.util.APIUtil
      
      $SecondLevelCaseClass
      $FirstLevelCaseClass
    
      object JSONFactoryCustom300{
            
        $instanceRootCaseClass
        
        val allFields =
          for (
            v <- this.getClass.getDeclaredFields
            //add guard, ignore the SwaggerJSONsV220.this and allFieldsAndValues fields
            if (APIUtil.notExstingBaseClass(v.getName()))
          )
            yield {
              v.setAccessible(true)
              v.get(this)
            }
      }

"""
  val jfile2 = new File("src/main/scala/code/api/v3_0_0/custom/JSONFactoryCustom3.0.0.scala")
  jfile2.getParentFile.mkdirs()
  Files.write(
    jfile2.toPath,
    jsonFactorySource.syntax.replaceAll("""`""",""""""""").getBytes("UTF-8")
  )
  
}
