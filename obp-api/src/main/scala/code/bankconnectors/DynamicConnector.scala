package code.bankconnectors

import code.api.util.DynamicUtil.compileScalaCode
import code.api.util.{CallContext, DynamicUtil}
import code.dynamicMessageDoc.{DynamicMessageDocProvider, JsonDynamicMessageDoc}
import net.liftweb.common.Box

import scala.concurrent.Future


object DynamicConnector {

  //This map is for the singletonObjects, we can add the new  in dynamic methods.
  //the key is String, and the value can be any type of objects.
  //the usage, first to try to get the object, if not existing, then we create it, eg: 
  //  val heavyClassService: HeavyClass = DynamicConnector.getSingletonObject("heavyClassService").getOrElse(
  //            DynamicConnector.createSingletonObject("heavyClassService",new HeavyClass())
  //          ).asInstanceOf[HeavyClass]
  private val singletonObjectMap = collection.mutable.Map[String, Any]()
  def createSingletonObject (key:String, value: Any) =  singletonObjectMap.put(key, value)
  def getSingletonObject (key:String) =  singletonObjectMap.get(key)
  def updateSingletonObject(key:String, value: Any) = singletonObjectMap.update(key, value)
  def removeSingletonObject(key:String) = singletonObjectMap.remove(key)

  def invoke(bankId: Option[String], process: String, args: Array[AnyRef], callContext: Option[CallContext]) = {
    val function =  getFunction(bankId, process)
     function.map(f =>f(args: Array[AnyRef], callContext: Option[CallContext])).openOrThrowException(s"There is no process $process, it should not be called here")
  }
  
  private def getFunction(bankId: Option[String], process: String) = {
    DynamicMessageDocProvider.provider.vend.getByProcess(bankId, process) map {
      case v :JsonDynamicMessageDoc =>
        createFunction(process, v.decodedMethodBody).openOrThrowException(s"InternalConnector method compile fail")
    }
  }



  /**
   * dynamic create function
   * @param process name of connector
   * @param methodBody method body of connector method
   * @return function of connector method that is dynamic created, can be Function0, Function1, Function2...
   */
  def createFunction(process: String, methodBody:String): Box[(Array[AnyRef], Option[CallContext]) => Future[Box[(AnyRef, Option[CallContext])]]] =
  {
    //messageDoc.process is a bit different with the methodName, we need tweak the format of it:
    //eg: process("obp.getBank") ==> methodName("getBank")
    val method = s"""
                    |${DynamicUtil.importStatements}
                    |
                    |val fn = new ((Array[AnyRef], Option[CallContext]) => Future[Box[(AnyRef, Option[CallContext])]]) (){
                    |  override def apply(args: Array[AnyRef], callContext: Option[CallContext]): Future[Box[(AnyRef, Option[CallContext])]] = {
                    |    $methodBody
                    |  }
                    |}
                    |
                    |fn
                    |""".stripMargin
    compileScalaCode(method)
  }

}

