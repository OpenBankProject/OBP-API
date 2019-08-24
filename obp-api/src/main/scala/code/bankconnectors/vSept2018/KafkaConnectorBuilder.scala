package code.bankconnectors.vSept2018

import java.io.File
import java.util.Date
import java.util.regex.Matcher

import code.api.util.ApiTag.ResourceDocTag
import code.api.util.{ApiTag, CallContext, OBPQueryParam}
import code.bankconnectors.Connector
import com.openbankproject.commons.util.ReflectUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils._

import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

import code.api.util.CodeGenerateUtils.createDocExample

object KafkaConnectorBuilder extends App {
  // rewrite method code.webuiprops.MappedWebUiPropsProvider#getWebUiPropsValue, avoid access DB cause dataSource not found exception
  {
    import javassist.ClassPool
    val pool = ClassPool.getDefault
    val ct = pool.getCtClass("code.webuiprops.MappedWebUiPropsProvider$")
    val m = ct.getDeclaredMethod("getWebUiPropsValue")
    m.insertBefore("""return ""; """)
    ct.toClass
  }

  val needToGenerateMethodsNames = List(
//    "getKycChecks",
//    "getKycDocuments",
//    "getKycMedias",
//    "getKycStatuses",
//    "createOrUpdateKycCheck",
//    "createOrUpdateKycDocument",
//    "createOrUpdateKycMedia",
//    "createOrUpdateKycStatus",
//    "createCustomer",
    "createBankAccount",
  )

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val clazz: ru.ClassSymbol = ru.typeOf[Connector].typeSymbol.asClass
  private val classMirror: ru.ClassMirror = mirror.reflectClass(clazz)
  private val generatedMethods: Iterable[CommonGenerator] = ru.typeOf[Connector].decls//this will return all Connector.scala direct(no inherited) declared members,eg: method, val
    .filter(_.isMethod) //Note: filter by method type.
    .filter(directlyDeclaredMember => needToGenerateMethodsNames.contains(directlyDeclaredMember.name.toString))// Find the method according to the `genMethodNames` List.
    .filter(directlyDeclaredMember => {
      directlyDeclaredMember.typeSignature.paramLists(0).find(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).isDefined //Just find the method, which has the `CallContext` as parameter
    })
    .map(directlyDeclaredMember => (directlyDeclaredMember.name.toString, directlyDeclaredMember.typeSignature)) //eg: (name =createBankAccount), (typeSignature = all parameters, return type info)
    .map{
      case (methodName, typeSignature) if(methodName.matches("^(get|check).+")) => GetGenerator(methodName, typeSignature)
      //Not finished yet, need to prepare create,delete Generator too.
      case (methodName, typeSignature) => new CommonGenerator(methodName, typeSignature)
    }

  //If there are more methods in the `needToGenerateMethodsNames` than `generatedMethods`, it mean some methods names are wrong... 
  //This will throw the Exception back directly
  if(needToGenerateMethodsNames.size > generatedMethods.size) {
    val generatedMethodsNames = generatedMethods.map(_.methodName).toList
    val invalidMethodNames = needToGenerateMethodsNames.filterNot(generatedMethodsNames.contains(_))
    throw new IllegalArgumentException(s"Some methods names are invalid, please check following methods typo: ${invalidMethodNames.mkString(", ")}")
  }

  println("-------------------")
//  generatedMethods.map(_.toString).foreach(println(_))
  println("===================")

  val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""), "src/main/scala/code/bankconnectors/vSept2018/KafkaMappedConnector_vSept2018.scala")
  val source = FileUtils.readFileToString(path)
  val start = "//---------------- dynamic start -------------------please don't modify this line"
  val end   = "//---------------- dynamic end ---------------------please don't modify this line"
  val placeHolderInSource = s"""(?s)$start.+$end"""
  val insertCode =
    s"""
       |$start
       |// ---------- create on ${new Date()}
       |${generatedMethods.map(_.toString).mkString}
       |$end
    """.stripMargin
  val newSource = source.replaceFirst(placeHolderInSource, Matcher.quoteReplacement(insertCode))
  FileUtils.writeStringToFile(path, newSource)

  // to check whether example is correct.
  private val tp: ru.Type = ReflectUtils.getTypeByName("com.openbankproject.commons.dto.InBoundGetProductCollectionItemsTree")

  println(createDocExample(tp))
}

/**
 * We will generate all the kafka connector code in to toString method: 
 * eg: if we use `createBankAccount` as an example: 
 * toString will return  `messageDocs` and `override def createBankAccount`  
 * 
 * @param methodName eg: createBankAccount
 * @param typeSignature eg: all the parameters and return type information are all in this filed.     
 */
class CommonGenerator(val methodName: String, typeSignature: Type) {
  /**
   * typeSignature.toString -->                                                                                                                       
   * (bankId: com.openbankproject.commons.model.BankId, accountId: com.openbankproject.commons.model.AccountId, accountType: String,                 
   * accountLabel: String, currency: String, initialBalance: BigDecimal, accountHolderName: String, branchId: String, accountRoutingScheme: String, 
   * accountRoutingAddress: String, callContext: Option[code.api.util.CallContext])                                                                 
   * code.api.util.APIUtil.OBPReturnType[net.liftweb.common.Box[com.openbankproject.commons.model.BankAccount]]
   *
   * .replaceAll("(\\w+\\.)+", "")--> this will clean the package path, only keep the Class Name.                                                                                                            
   * (bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal,                
   * accountHolderName: String, branchId: String, accountRoutingScheme: String, accountRoutingAddress: String, callContext: Option[CallContext])    
   * OBPReturnType[Box[BankAccount]]
   *
   * .replaceFirst("\\)", "): ") --> this will add the `:` before the returnType.                                                                                                             
   * (bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal,                
   * accountHolderName: String, branchId: String, accountRoutingScheme: String, accountRoutingAddress: String, callContext: Option[CallContext]):   
   * OBPReturnType[Box[BankAccount]]
   *
   * .replaceFirst("""\btype\b""", "`type`") -->  //TODO not sure, what is this? to clean some special case?                                                                                                    
   * (bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal,                
   * accountHolderName: String, branchId: String, accountRoutingScheme: String, accountRoutingAddress: String, callContext: Option[CallContext]):   
   * OBPReturnType[Box[BankAccount]]
   *
   */
  protected[this] def cleanParametersAndReturnTpyeString = typeSignature.toString 
    .replaceAll("(\\w+\\.)+", "")
    .replaceFirst("\\)", "): ")
    .replaceFirst("""\btype\b""", "`type`")

  /**
   * Get all the parameters name as a String from `typeSignature` object.
   * eg: it will return 
   * , bankId, accountId, accountType, accountLabel, currency, initialBalance, accountHolderName, branchId, accountRoutingScheme, accountRoutingAddress
   */
  private[this] val parametersNamesString = typeSignature.paramLists(0)//paramLists will return all the curry parameters set. 
    .filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]) // remove the `CallContext` field.
    .map(_.name.toString)//get all parameters name
    .map(it => if(it =="type") "`type`" else it)//This is special case for `type`, it is the keyword in scala. 
    match {
    case Nil => ""
    case list:List[String] => list.mkString(", ", ", ", "")
  }
  
  /**
   * get the messageDocDescription field from the connector method name:
   * 
   * eg: 
   * createBankAccountFuture --> Create Bank Account         
   * createBankAccount-->Create Bank Account                 
   */
  private[this] val messageDocDescription = methodName
    .replace("Future", "")
    .replaceAll("([a-z])([A-Z])", "$1 $2")    //createBankAccount-->create Bank Account
    .capitalize                   // create Bank Account  --> Create Bank Account 


  /**
   * this val really depends on the OutBound classes under path `com.openbankproject.commons.dto`.
   * Need prepare the OutBound classes before create this.
   */
  private[this] val outBoundExample = {
    val fullyQualifiedClassName = s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}" // com.openbankproject.commons.dto.OutBoundCreateBankAccount
    val outBoundType = if(ReflectUtils.isTypeExists(fullyQualifiedClassName)) 
      ReflectUtils.getTypeByName(fullyQualifiedClassName)
    else 
      throw new RuntimeException(s"OutBound${methodName.capitalize} class is not existing in `com.openbankproject.commons.dto` path. Please create it first. ")
    createDocExample(outBoundType).replaceAll("(?m)^(\\S)", "      $1")
  }
  
  
  private[this] val inBoundExample = {
    val fullyQualifiedClassName = s"com.openbankproject.commons.dto.InBound${methodName.capitalize}"

    val inBoundType = if(ReflectUtils.isTypeExists(fullyQualifiedClassName))
      ReflectUtils.getTypeByName(fullyQualifiedClassName)
    else
      throw new RuntimeException(s"InBound${methodName.capitalize} class is not existing in `com.openbankproject.commons.dto` path. Please create it first. ")
    
    createDocExample(inBoundType).replaceAll("(?m)^(\\S)", "      $1")
  }

  val signature = s"$methodName$cleanParametersAndReturnTpyeString"

  val outboundName = s"OutBound${methodName.capitalize}"
  val inboundName = s"InBound${methodName.capitalize}"

  val tagValues = ReflectUtils.getType(ApiTag).decls
    .filter(it => it.isMethod && it.asMethod.returnType <:< typeOf[ResourceDocTag])
    .map(_.asMethod)
    .filter(method => method.paramLists.isEmpty)
    .map(method => ReflectUtils.invokeMethod(ApiTag, method))
    .map(_.asInstanceOf[ResourceDocTag])
    .map(_.tag)
    .map(it => (it.replaceAll("""\W""", "").toLowerCase, it))
    .toMap

  val tag = tagValues.filter(it => methodName.toLowerCase().contains(it._1)).map(_._2).toList.sortBy(_.size).lastOption.getOrElse("- Core")


  protected[this] def methodBody: String =
    s"""{
      |    import com.openbankproject.commons.dto.{${outboundName} => OutBound, ${inboundName} => InBound}
      |
      |    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get ${parametersNamesString})
      |    logger.debug(s"Kafka ${methodName} Req is: $$req")
      |    processRequest[InBound](req) map (convertToTuple(callContext))
      |  }
    """.stripMargin

  override def toString =
    s"""
       |  messageDocs += MessageDoc(
       |    process = "obp.$methodName",
       |    messageFormat = messageFormat,
       |    description = "$messageDocDescription",
       |    outboundTopic = Some(Topics.createTopicByClassName(${outboundName}.getClass.getSimpleName).request),
       |    inboundTopic = Some(Topics.createTopicByClassName(${outboundName}.getClass.getSimpleName).response),
       |    exampleOutboundMessage = (
       |    $outBoundExample
       |    ),
       |    exampleInboundMessage = (
       |    $inBoundExample
       |    ),
       |    adapterImplementation = Some(AdapterImplementation("${tag}", 1))
       |  )
       |  override def $signature = ${methodBody}
    """.stripMargin
}

case class GetGenerator(override val methodName: String, typeSignature: Type) extends CommonGenerator(methodName, typeSignature) {
  private[this] val resultType = typeSignature.resultType.toString.replaceAll("(\\w+\\.)+", "")

  private[this] val isReturnBox = resultType.startsWith("Box[")

  private[this] val cachMethodName = if(isReturnBox) "memoizeSyncWithProvider" else "memoizeWithProvider"

  private[this] val timeoutFieldName = uncapitalize(methodName.replaceFirst("^[a-z]+", "")) + "TTL"
  private[this] val cacheTimeout = ReflectUtils.findMethod(ru.typeOf[KafkaMappedConnector_vSept2018], timeoutFieldName)(_ => true)
    .map(_.name.toString)
    .getOrElse("accountTTL")

  override def cleanParametersAndReturnTpyeString = super.cleanParametersAndReturnTpyeString
      .replaceFirst("""callContext:\s*Option\[CallContext\]""", "@CacheKeyOmit callContext: Option[CallContext]")

  override protected[this] def methodBody: String =
    s"""saveConnectorMetric {
      |    /**
      |      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      |      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      |      * The real value will be assigned by Macro during compile time at this line of a code:
      |      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      |      */
      |    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      |    CacheKeyFromArguments.buildCacheKey {
      |      Caching.${cachMethodName}(Some(cacheKey.toString()))(${cacheTimeout} second) ${super.methodBody.replaceAll("(?m)^ ", "     ")}
      |    }
      |  }("$methodName")
    """.stripMargin
}




