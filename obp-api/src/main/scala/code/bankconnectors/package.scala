package code

import java.lang.reflect.Method

import code.api.util.APIUtil
import code.bankconnectors.akka.AkkaConnector_vDec2018
import code.bankconnectors.rest.RestConnector_vMar2019
import code.bankconnectors.vSept2018.KafkaMappedConnector_vSept2018
import com.openbankproject.commons.util.ReflectUtils
import net.sf.cglib.proxy.{Enhancer, MethodInterceptor, MethodProxy}

package object bankconnectors {


  /**
    * a star connector object, usage:
    *
    * first modify default.props, default connector is mapped:
    *   connector=star
    *   connector.start.methodName.getBanks=mapped
    *   connector.start.methodName.getCustomersByUserIdFuture=rest_vMar2019
    *
    * run the follow demo code anywhere
    *   import code.bankconnectors.StarConnector
    *   val b = new Boot() // initiate connectors state
    *   StarConnector.getBanks(None) //call LocalMappedConnector
    *   StarConnector.getCustomersByUserIdFuture("hello", None) // call RestConnector_vMar2019
    */
  val StarConnector: Connector = {
    def getConnectorObject(methodName: String) = {
      val connectorName = APIUtil.getPropsValue(s"connector.start.methodName.${methodName}","mapped")
      connectorName match {
        case "mapped" => LocalMappedConnector
        case "rest_vMar2019" => RestConnector_vMar2019
        case "kafka_vSept2018" => KafkaMappedConnector_vSept2018
        case "akka_vDec2018" => AkkaConnector_vDec2018
        case _ => throw new IllegalStateException(s"config of connector.start.methodName.${methodName} have wrong value, not exists connector of name ${connectorName}")
      }
    }

    val intercept:MethodInterceptor = (_: Any, method: Method, args: Array[AnyRef], _: MethodProxy) => {
      val methodName = method.getName
      val objToCall =  getConnectorObject(methodName)
      ReflectUtils.invokeMethod(objToCall, methodName, args:_*).asInstanceOf[AnyRef]
    }
    val enhancer: Enhancer = new Enhancer()
    enhancer.setSuperclass(classOf[Connector])
    enhancer.setCallback(intercept)
    enhancer.create().asInstanceOf[Connector]
  }
}
