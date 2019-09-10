package code.webhook

import javassist.ClassPool
import javassist.bytecode.Bytecode
import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContextEvent, ServletContextListener}

@WebListener("Add some special logic before execute bootstrap.liftweb.Boot")
class OBPServletContextListener extends ServletContextListener{
  override def contextInitialized(sce: ServletContextEvent): Unit = {

    // modify lift framework class Req bytecode in lift-webkit_2.12-3.3.0.jar, make GET request can send request body.
    // note: if lift version be updated, this maybe cause error, because the target code maybe not at line 258
    {
      val pool = ClassPool.getDefault

      val req = pool.getCtClass("net.liftweb.http.Req$")
      val method = req.getDeclaredMethod("$anonfun$apply$14")
      val codeIterator = method.getMethodInfo.getCodeAttribute.iterator()
      //modify code, replace if(reqType.get_?) to if(reqType.head_?)
      val code = new Bytecode(method.getMethodInfo.getConstPool)
      code.addInvokevirtual(pool.getCtClass("net.liftweb.http.RequestType"), "head_$qmark", "()Z")

      codeIterator.write(code.get(), 258)
      req.toClass
    }
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {}
}
