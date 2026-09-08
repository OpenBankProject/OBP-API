package code.api.util

import org.json4s._
import code.api.Constant.SHOW_USED_CONNECTOR_METHODS
import code.api.{APIFailureNewStyle, JsonResponseException}
import code.api.util.ErrorMessages.DynamicResourceDocMethodDependency
import cats.effect.IO
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.util.Functions.Memo
import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
import javassist.{ClassPool, LoaderClassPath}
import net.liftweb.common.{Box, Empty, Failure, Full, ParamFailure}
import org.json4s.{Extraction, JValue}
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.apache.commons.lang3.StringUtils
import org.graalvm.polyglot.{Context, Engine, HostAccess, PolyglotAccess}

import java.security.{AccessControlContext, AccessController, CodeSource, Permission, PermissionCollection, Permissions, Policy, PrivilegedAction, ProtectionDomain}
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.regex.Pattern
import javax.script.ScriptEngineManager
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, Promise}
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.runtimeMirror
import scala.runtime.NonLocalReturnControl
import scala.tools.reflect.{ToolBox, ToolBoxError}

object DynamicUtil extends MdcLoggable{

  // Master kill-switch for user-generated dynamic code (RCE surface). Defaults to OFF
  // everywhere — including test/dev — unless explicitly enabled via this prop. Tests that
  // exercise dynamic-code compilation must set allow_user_generated_scala_code=true
  // explicitly (see test.default.props / the CI "Setup props" step).
  def dynamicCodeExecutionEnabled: Boolean =
    APIUtil.getPropsValue("allow_user_generated_scala_code") match {
      case Full(v) => v.toBoolean
      case _ => false
    }

  val toolBox: ToolBox[universe.type] = runtimeMirror(getClass.getClassLoader).mkToolBox()

  /** One compiler diagnostic from [[checkScalaCode]]. `line`/`column` are 1-based in the code that was checked; 0 when unknown. */
  case class CompileProblem(line: Int, column: Int, severity: String, message: String)

  /** Collects compiler diagnostics with positions; the default toolbox front end only keeps the messages. */
  private class CollectingFrontEnd extends scala.tools.reflect.FrontEnd {
    // FrontEnd.log already records every diagnostic in `infos`; nothing to print.
    override def display(info: Info): Unit = ()
  }
  private val checkFrontEnd = new CollectingFrontEnd
  // A second toolbox, so a dry-run check never touches the memoised compile results of the real one.
  private val checkToolBox: ToolBox[universe.type] = runtimeMirror(getClass.getClassLoader).mkToolBox(frontEnd = checkFrontEnd)

  /**
   * Compile `code` for diagnostics only: nothing is evaluated, nothing is cached. Empty list = compiles.
   * Toolboxes are not thread-safe, so checks are serialised. Callers must apply the kill switch and a role.
   */
  def checkScalaCode(code: String): List[CompileProblem] = checkToolBox.synchronized {
    checkFrontEnd.reset()
    val failure: Option[String] = try {
      checkToolBox.typecheck(checkToolBox.parse(code))
      None
    } catch {
      case e: ToolBoxError => Some(e.message)
    }
    val collected = checkFrontEnd.infos.toList.filter(_.severity == checkFrontEnd.ERROR).map { info =>
      val (line, column) = if (info.pos != null && info.pos.isDefined) (info.pos.line, info.pos.column) else (0, 0)
      CompileProblem(line, column, "ERROR", info.msg)
    }
    if (collected.nonEmpty) collected
    else failure.map(m => CompileProblem(0, 0, "ERROR", m.stripPrefix("reflective typecheck has failed:").stripPrefix("reflective compilation has failed:").trim)).toList
  }
  private val memoClassPool = new Memo[ClassLoader, ClassPool]

  private def getClassPool(classLoader: ClassLoader) = memoClassPool.memoize(classLoader){
    val cp = ClassPool.getDefault
    cp.appendClassPath(new LoaderClassPath(classLoader))
    cp
  }

  // code -> dynamic method function
  // the same code should always be compiled once, so here cache them
  private val dynamicCompileResult = new ConcurrentHashMap[String, Box[Any]]()

  type DynamicFunction = (Array[AnyRef], Option[CallContext]) => Future[Box[(String, Option[CallContext])]]

  /**
   * Compile scala code
   * toolBox have bug that first compile fail, second or later compile success.
   * @param code
   * @return compiled Full[function|object|class] or Failure
   */
  def compileScalaCode[T](code: String): Box[T] = {
    if (!dynamicCodeExecutionEnabled)
      return Failure(ErrorMessages.DynamicCodeExecutionDisabled)
    compileScalaCodeUnchecked[T](code)
  }

  // Used ONLY by DynamicUtil.Validation's props-driven config parsing (operator config,
  // not user-generated code) so the app can still boot with the kill-switch off.
  private def compileScalaCodeUnchecked[T](code: String): Box[T] = {
    logger.trace(s"code.api.util.DynamicUtil.compileScalaCode.size is ${dynamicCompileResult.size()}")
    val compiledResult: Box[Any] = dynamicCompileResult.computeIfAbsent(code, _ => {
      val tree = try {
        toolBox.parse(code)
      } catch {
        case e: ToolBoxError =>
          return Failure(e.message)
      }

      try {
        val func: () => Any = toolBox.compile(tree)
        Box.tryo(func())
      } catch {
        case _: ToolBoxError =>
          // try compile again
          try {
            val func: () => Any = toolBox.compile(tree)
            Box.tryo(func())
          } catch {
            case e: ToolBoxError =>
              Failure(e.message)
          }
      }
    })

    compiledResult.map(_.asInstanceOf[T])
  }

  /**
   * 
   * @param methodName the method name
   * @param function the method body, if it is empty, then throw exception. if it is existing, then call this function.
   * @param args the method parameters
   * @return the result of the execution of the function.
   */
  def executeFunction(methodName: String, function: Box[Any], args: Array[AnyRef]) = {
    val result = function.orNull match {
      case func: Function0[AnyRef] => func()
      case func: Function[AnyRef, AnyRef] => func(args.head)
      case func: Function2[AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1))
      case func: Function3[AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2))
      case func: Function4[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3))
      case func: Function5[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4))
      case func: Function6[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5))
      case func: Function7[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6))
      case func: Function8[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7))
      case func: Function9[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8))
      case func: Function10[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9))
      case func: Function11[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10))
      case func: Function12[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11))
      case func: Function13[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12))
      case func: Function14[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13))
      case func: Function15[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14))
      case func: Function16[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15))
      case func: Function17[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16))
      case func: Function18[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17))
      case func: Function19[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17), args.apply(18))
      case func: Function20[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17), args.apply(18), args.apply(19))
      case null => throw new IllegalStateException(s"There is  no method $methodName, it should not be called here")
      case _ => throw new IllegalStateException(s"$methodName can not be called here.")
    }
    result.asInstanceOf[AnyRef]
  }

  /**
   * this method will create a object from the JValue.
   * from JValue --> Case Class String -->  DynamicUtil.compileScalaCode(code) --> object 
   * @param jValue
   * @return 
   */
  def toCaseObject(jValue: JValue): Product = {
    val caseClasses = JsonUtils.toCaseClasses(jValue)
    val code =
      s"""
         | $caseClasses
         |
         | // throws exception: org.json4s.MappingException:
         | //No usable value for name
         | //Did not find value which can be converted into java.lang.String
         |
         |implicit val formats = code.api.util.CustomJsonFormats.formats
         |(jValue: org.json4s.JsonAST.JValue) => {
         |  jValue.extract[RootJsonClass]
         |}
         |""".stripMargin
    val fun: Box[JValue => Product] = DynamicUtil.compileScalaCode(code)
    fun match {
      case Full(func) => func.apply(jValue)
      case Failure(msg: String, exception: Box[Throwable], _) =>
        throw exception.getOrElse(new RuntimeException(msg))
      case _ => throw new RuntimeException(s"Json extract to case object fail, json: \n ${prettyRender(jValue)}")
    }
  }

  /**
   * NOTE: MEMORY_USER this ctClass will be cached in ClassPool, it may load too many classes into heap. 
   * @param clazz
   * @param predicate
   * @return
   */
  def getDynamicCodeDependentMethods(clazz: Class[_], predicate:  String => Boolean = _ => true): List[(String, String, String)] = 
  if (SHOW_USED_CONNECTOR_METHODS) {
    val className = clazz.getTypeName
    val listBuffer = new ListBuffer[(String, String, String)]()
    val classPool = getClassPool(clazz.getClassLoader)
    //NOTE: MEMORY_USER this ctClass will be cached in ClassPool, it may load too many classes into heap. 
    val ctClass = classPool.get(className)
    for {
      method <- ctClass.getDeclaredMethods.toList
      if predicate(method.getName)
      ternary @ (typeName, methodName, signature) <- APIUtil.getDependentMethods(className, method.getName, method.getSignature)
    } yield {
      // if method is also dynamic compile code, extract it's dependent method
      if(className.startsWith(typeName) && methodName.startsWith(clazz.getPackage.getName+ "$")) {
        listBuffer.appendAll(APIUtil.getDependentMethods(typeName, methodName, signature))
      } else {
        listBuffer.append(ternary)
      }
    }

    listBuffer.distinct.toList
  } else {
    Nil
  }

  trait Sandbox {
    @throws[Exception]
    def runInSandbox[R](action: => R): R

    /**
     * Run a dynamic body's IO under the same security sandbox, for native (http4s) runtime-compiled
     * dynamic endpoints (Piece C). The body's SYNCHRONOUS CONSTRUCTION (forcing the by-name `io`,
     * i.e. applying the compiled handler / running the user statements up to the first Future) runs
     * inside the privileged context with the restricted permissions; the resulting IO is then
     * evaluated by the cats-effect runtime OUTSIDE the privileged context. This mirrors the Lift
     * path exactly: there `runInSandbox { process(...) }` wrapped only the synchronous construction
     * plus the blocking wait, while the user's Future body (DB / network / serialization) ran on the
     * EC thread outside `doPrivileged`. Running the whole IO inside `doPrivileged` instead would
     * (wrongly) subject framework I/O — DB sockets, etc. — to the dynamic-code permission set.
     *
     * Non-local `return`: when the dynamic body is the runtime-compiled template it is a closure,
     * so `return errorResponse(...)` throws a `NonLocalReturnControl` carrying the IO it should
     * return (the Lift runInSandbox caught the JsonResponse equivalent). We recover that IO here so
     * an early `return` in user code yields its response rather than a 500. (In PractiseEndpoint the
     * body is a real method, so `return` is an ordinary return and never reaches this catch.)
     */
    def runInSandboxIO[A](io: => IO[A]): IO[A] = {
      def forceBodyIO(): IO[A] =
        try io
        catch { case e: scala.runtime.NonLocalReturnControl[_] => e.value.asInstanceOf[IO[A]] }
      IO.defer(runInSandbox(forceBodyIO()))
    }
  }

  object Sandbox {
    // SecurityManager was deprecated for removal in JDK 17 (JEP 411) and setSecurityManager()
    // now throws UnsupportedOperationException on this runtime (JDK 25). Catch and ignore so
    // the rest of the Sandbox (AccessController.doPrivileged) still compiles and runs — but
    // with no SecurityManager installed, AccessController.doPrivileged is a pass-through:
    // Sandbox.runInSandbox no longer actually restricts what dynamic-endpoint/connector
    // code can do (file/network/reflection access are all unguarded). Log loudly so this
    // silent security regression isn't invisible in production — it was previously masked
    // by three DynamicUtilTest scenarios that are now `assume`-skipped for the same reason.
    try {
      if (System.getSecurityManager == null) {
        Policy.setPolicy(new Policy() {
          override def getPermissions(codeSource: CodeSource): PermissionCollection = {
            for (element <- Thread.currentThread.getStackTrace) {
              if ("sun.rmi.server.LoaderHandler" == element.getClassName && "loadClass" == element.getMethodName)
                return new Permissions
            }
            super.getPermissions(codeSource)
          }

          override def implies(domain: ProtectionDomain, permission: Permission) = true
        })
        System.setSecurityManager(new SecurityManager)
      }
    } catch {
      case _: UnsupportedOperationException =>
        logger.warn("code.api.util.DynamicUtil.Sandbox: SecurityManager is unavailable on this JVM " +
          "(JEP 486, JDK 24+). Sandbox.runInSandbox / Sandbox.createSandbox will NOT enforce any " +
          "permission restrictions on dynamic-endpoint / connector-builder code — file, network and " +
          "reflection access are unguarded. This is expected on JDK 24+ but is a real reduction in " +
          "isolation for the dynamic-code feature; do not rely on this sandbox for untrusted code on this runtime.")
    }

    def createSandbox(permissionList: List[Permission]): Sandbox = {
      val accessControlContext: AccessControlContext = {
        val permissions = new Permissions()
        permissionList.foreach(permissions.add)
        val protectionDomain = new ProtectionDomain(null, permissions)
        new AccessControlContext(Array(protectionDomain))
      }

      new Sandbox {
        @throws[Exception]
        def runInSandbox[R](action: => R): R = {
          val privilegedAction: PrivilegedAction[R] = () => action
          AccessController.doPrivileged(privilegedAction, accessControlContext)
        }
        // The former NonLocalReturnControl[JsonResponse] catch (for the Lift dynamic-code path's
        // `return Full(errorJsonResponse(...))`) is gone: the only caller is runInSandboxIO, whose
        // forceBodyIO already recovers a NonLocalReturnControl before it reaches here.
      }
    }

    private val memoSandbox = new Memo[String, Sandbox]

    /**
     * this method will call create Sandbox underneath, but will have default permissions and bankId permission and cache.  
     */
    def sandbox(bankId: String): Sandbox = memoSandbox.memoize(bankId) {
      Sandbox.createSandbox(BankId.permission(bankId) :: Validation.allowedRuntimePermissions)
    }
  }

  /**
   * common import statements those are used by compiler
   */
 val importStatements =
    """
      |import java.net.{ConnectException, URLEncoder, UnknownHostException}
      |import java.util.Date
      |import java.util.UUID.randomUUID
      |
      |import _root_.org.apache.pekko.stream.StreamTcpException
      |import org.apache.pekko.http.scaladsl.model.headers.RawHeader
      |import org.apache.pekko.http.scaladsl.model.{HttpProtocol, _}
      |import org.apache.pekko.util.ByteString
      |import code.api.APIFailureNewStyle
      |import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions
      |import code.api.cache.Caching
      |import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, writeMetricEndpointTiming, _}
      |import code.api.util.ErrorMessages._
      |import code.api.util.ExampleValue._
      |import code.api.util.{APIUtil, CallContext, OBPQueryParam}
      |import code.api.dynamic.endpoint.helper.MockResponseHolder
      |import code.bankconnectors._
      |import code.customer.internalMapping.MappedCustomerIdMappingProvider
      |import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
      |import code.util.AkkaHttpClient._
      |import code.util.Helper.MdcLoggable
      |import com.openbankproject.commons.dto.{InBoundTrait, _}
      |import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
      |import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, DynamicEntityOperation, ProductAttributeType}
      |import com.openbankproject.commons.model.{ErrorMessage, TopicTrait, _}
      |import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
      |// import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
      |import net.liftweb.common.{Box, Empty, _}
      |import com.openbankproject.commons.util.json
      |import org.json4s.Extraction.decompose
      |import org.json4s.JsonDSL._
      |import org.json4s.ParserUtil.ParseException
      |import org.json4s.{JValue, _}
      |import com.openbankproject.commons.util.JsonAliases._
      |import net.liftweb.util.Helpers.tryo
      |import org.apache.commons.lang3.StringUtils
      |
      |import scala.collection.immutable.List
      |import scala.collection.mutable.ArrayBuffer
      |import scala.concurrent.duration._
      |import scala.concurrent.{Await, Future}
      |import com.openbankproject.commons.dto._
      |import code.api.util.APIUtil.ResourceDoc
      |import code.api.util.DynamicUtil.Sandbox
      |import code.api.util.NewStyle.HttpCode
      |import code.api.util._
      |import code.api.v4_0_0.JSONFactory400
      |import code.api.dynamic.endpoint.helper.{CompiledObjects, DynamicCompileEndpoint}
      |import code.api.dynamic.endpoint.helper.practise.PractiseEndpoint
      |import com.openbankproject.commons.ExecutionContext
      |import code.api.util.CustomJsonFormats
      |import com.openbankproject.commons.model.BankId
      |import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
      |import net.liftweb.common.{Box, Full}
      |import org.apache.commons.lang3.StringUtils
      |
      |import java.io.File
      |import java.security.{AccessControlException, Permission}
      |import java.util.PropertyPermission
      |import scala.collection.immutable.List
      |import scala.io.Source
      |""".stripMargin


  object Validation {

    /**
     * Turn the `dynamic_code_compile_validate_dependencies` props value into the Scala source
     * that, once compiled, yields the whitelist.
     *
     * A named function rather than an inline expression so a test can drive the real thing.
     * DynamicUtilTest used to hold a character-for-character copy of it, which meant the two
     * could diverge with the test still green -- the copy was only kept in step here because
     * whoever edited one happened to see the other. This is the only compile that happens
     * reflectively at boot, so nothing at compile time would have caught the divergence either.
     *
     * `Map[String, String](` rather than `Map(`: the props default is an empty list, and a bare
     * `Map()` leaves its type parameters undetermined, so the trailing `.toMap` cannot prove the
     * elements are pairs and the reflective compilation fails. The `.toMap` is itself needed
     * because `mapValues` returns a view rather than a Map on 2.13.
     */
    def dependenciesScalaCode(dependenciesString: String): String =
      s"${DynamicUtil.importStatements}" +
        dependenciesString.replaceFirst("\\[", "Map[String, String](").dropRight(1) +
        ").mapValues(v => StringUtils.split(v, ',').map(_.trim).toSet).toMap"

    val dynamicCodeSandboxPermissions = APIUtil.getPropsValue("dynamic_code_sandbox_permissions", "[]").trim
    val scalaCodePermissioins = "List[java.security.Permission]"+dynamicCodeSandboxPermissions.replaceFirst("\\[","(").dropRight(1)+")"
    val permissions:Box[List[java.security.Permission]] = DynamicUtil.compileScalaCodeUnchecked(scalaCodePermissioins)
    
    // all Permissions put at here
    // Here is the Java Permission document, please extend these permissions carefully. 
    // https://docs.oracle.com/javase/8/docs/technotes/guides/security/spec/security-spec.doc3.html#17001
    // If you are not familiar with the permissions, we provide the clear error messages for the missing permissions in the log.
    // eg1 scala test level : and have a look at the scala test for `createSandbox` method, you can see how to add permissions there too. 
    // eg2 api level:  "OBP-40047: DynamicResourceDoc method have no enough permissions.  No permission of: (\"java.io.FilePermission\" \"stop-words-en.txt\" \"write\")"
    //       --> you can extends following permission: new java.net.SocketPermission("ir.dcs.gla.ac.uk:80", "connect,resolve"), 
    // NOTE: These permissions are only checked during runtime, not the compilation period.
//    val allowedRuntimePermissions = List[Permission](
//      new NetPermission("specifyStreamHandler"),
//      new ReflectPermission("suppressAccessChecks"),
//      new RuntimePermission("getenv.*"),
//      new PropertyPermission("cglib.useCache", "read"),
//      new PropertyPermission("net.sf.cglib.test.stressHashCodes", "read"),
//      new PropertyPermission("cglib.debugLocation", "read"),
//      new RuntimePermission("accessDeclaredMembers"),
//      new RuntimePermission("getClassLoader"),
//    )
    val allowedRuntimePermissions = permissions.openOrThrowException("Can not compile the props `dynamic_code_sandbox_permissions` to permissions")

    val dependenciesString = APIUtil.getPropsValue("dynamic_code_compile_validate_dependencies", "[]").trim
    val scalaCodeDependencies = dependenciesScalaCode(dependenciesString)
    val dependenciesBox: Box[Map[String, Set[String]]] = DynamicUtil.compileScalaCodeUnchecked(scalaCodeDependencies)
    
    /**
     * Compilation OBP Dependencies Guard, only checked the OBP methods, not scala/Java libraies(are checked during the runtime.).
     * 
     * allowedCompilationMethods --> 
     * The following methods will be checked when you call the `Create Dynamic ResourceDoc/MessageDoc` endpoints.
     *  You can control all the OBP methods here.
     */
    // all allowed methods put at here, typeName -> methods
//    val allowedCompilationMethods: Map[String, Set[String]] = Map(
//      // companion objects methods
//      NewStyle.function.getClass.getTypeName -> "*",
//      CompiledObjects.getClass.getTypeName -> "sandbox",
//      HttpCode.getClass.getTypeName -> "200",
//      DynamicCompileEndpoint.getClass.getTypeName -> "getPathParams, scalaFutureToBoxedJsonResponse",
//      APIUtil.getClass.getTypeName -> "errorJsonResponse, errorJsonResponse$default$1, errorJsonResponse$default$2, errorJsonResponse$default$3, errorJsonResponse$default$4, scalaFutureToLaFuture, futureToBoxedResponse",
//      ErrorMessages.getClass.getTypeName -> "*",
//      ExecutionContext.Implicits.getClass.getTypeName -> "global",
//      JSONFactory400.getClass.getTypeName -> "createBanksJson",
//
//      // class methods
//      classOf[Sandbox].getTypeName -> "runInSandbox",
//      classOf[CallContext].getTypeName -> "*",
//      classOf[ResourceDoc].getTypeName -> "getPathParams",
//      "scala.reflect.runtime.package$" -> "universe",
//
//      // allow any method of PractiseEndpoint for test
//      PractiseEndpoint.getClass.getTypeName + "*" -> "*",
//
//    ).mapValues(v => StringUtils.split(v, ',').map(_.trim).toSet)
    val allowedCompilationMethods: Map[String, Set[String]] = dependenciesBox.openOrThrowException("Can not compile the props `dynamic_code_compile_validate_dependencies` to Map")

    //Do not touch this Set, try to use the `allowedPermissions` and `allowedMethods` to control the sandbox 
    val restrictedTypes = Set(
      "scala.reflect.runtime.",
      "java.lang.reflect.",
      "scala.concurrent.ExecutionContext"
    )

    private def isRestrictedType(typeName: String) = ReflectUtils.isObpClass(typeName) || restrictedTypes.exists(typeName.startsWith)

    /**
     * validate dependencies, (className, methodName, signature)
     * 
     * Here only validate the restricted types(isObpClass + val restrictedTypes), not all scala/java types.
     */
    private def validateDependency(dependentMethods: List[(String, String, String)]) = {
      val notAllowedDependentMethods = dependentMethods collect {
        case (typeName, method, _)
          if isRestrictedType(typeName) &&
            !allowedCompilationMethods.get(typeName).exists(set => set.contains(method) || set.contains("*")) &&
            !allowedCompilationMethods.exists { it =>
              val (tpName, allowedMethods) = it
              tpName.endsWith("*") &&
                typeName.startsWith(StringUtils.substringBeforeLast(tpName, "*")) &&
                (allowedMethods.contains(method) || allowedMethods.contains("*"))
            }
        =>
          s"$typeName.$method"
      }
      // change to JsonResponseException
      if(notAllowedDependentMethods.nonEmpty) {
        val illegalDependency = notAllowedDependentMethods.mkString("[", ", ", "]")
        throw JsonResponseException(s"$DynamicResourceDocMethodDependency $illegalDependency", 400, "none")
      }
    }

    def validateDependency(obj: AnyRef): Unit = {
      if(APIUtil.getPropsAsBoolValue("dynamic_code_compile_validate_enable",false)){
        val dependentMethods: List[(String, String, String)] = DynamicUtil.getDynamicCodeDependentMethods(obj.getClass)
        validateDependency(dependentMethods)
      } else{ // If false, nothing to do here.
        ;
      }
    }
  }

  private val jsEngine = Engine.newBuilder.option("engine.WarnInterpreterOnly", "false")
    .allowExperimentalOptions(true)
    .build()

  private val memoDynamicFunction = new Memo[String, Box[DynamicFunction]]

  def createJsFunction(methodBody:String, bindingVars: Map[String, AnyRef] = Map.empty): Box[DynamicFunction] =
    if (!dynamicCodeExecutionEnabled) Failure(ErrorMessages.DynamicCodeExecutionDisabled)
    else memoDynamicFunction.memoize("Javascript:" + methodBody) {
    Box tryo {
      val jsCode = s"""async function processor(args, callContext) {
       $methodBody
      }
      // wrap function in order to convert return value to json string
      async (args) => JSON.stringify(await processor(args));
      """;
      val context = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL)
        .allowPolyglotAccess(PolyglotAccess.ALL)
        .allowHostClassLookup(_ => true)
        .option("js.ecmascript-version", "2020")
        .engine(jsEngine).build

      // bind variables
      val bindings = context.getBindings("js")
      bindingVars.foreach(it => bindings.putMember(it._1, it._2))

      // call js
      val jsFunc = context.eval("js", jsCode)

      (args: Array[AnyRef], cc: Option[CallContext]) => {
        val p = Promise[Box[(String, Option[CallContext])]]()
        // to JValue: Extraction.decompose(it)(formats)
        val resolve: Consumer[String] = (it: String) =>
          p.success(Full(it -> cc))

        // TODO refactor APIFailureNewStyle error message.
        val reject:Consumer[Any]= e =>
          p.success(ParamFailure(s"Js reject error message: $e", Empty, Empty, APIFailureNewStyle(e.toString, 400, cc.map(_.toLight))))

        //cc.map(_.toOutboundAdapterCallContext).orNull
        jsFunc.execute(args ++ cc)
          .invokeMember("then", resolve, reject)
          .invokeMember("catch", reject)
        p.future
      }
    }
  }

  private val javaEngine = (new ScriptEngineManager).getEngineByName("java")

  def createJavaFunction(methodBody:String): Box[DynamicFunction] =
    if (!dynamicCodeExecutionEnabled) Failure(ErrorMessages.DynamicCodeExecutionDisabled)
    else memoDynamicFunction.memoize("java:" + methodBody) {
    import com.openbankproject.commons.ExecutionContext.Implicits.global
    import com.openbankproject.commons.util.JsonAliases.compactRender

    Box tryo {
      val packageExp = UUID.randomUUID().toString.replaceAll("^|-", "_")
      val packageMatcher = Pattern.compile("""(?m)^\s*package\s+\S+?\s*;""").matcher(methodBody)

      val javaCode = s"""package code.api.util.dynamic.${packageExp};
                        |${packageMatcher.replaceFirst("")}
                        |""".stripMargin

      val func = javaEngine.eval(javaCode).asInstanceOf[java.util.function.Function[Array[AnyRef], Any]]

      (args: Array[AnyRef], cc: Option[CallContext]) => Future {
        val value = func(args ++ cc)
        val jValue = Extraction.decompose(value)(CustomJsonFormats.formats)
        val zson = compactRender(jValue)
        Box !! (zson-> cc)
      }
    }
  }
}
