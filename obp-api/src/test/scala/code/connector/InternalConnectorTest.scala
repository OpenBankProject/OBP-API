package code.connector

import code.api.util.{CallContext, DynamicUtil}
import code.bankconnectors.InternalConnector
import com.openbankproject.commons.model.{Bank, BankId}
import net.liftweb.common.{Box,Full}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.Future

class InternalConnectorTest extends FlatSpec with Matchers {


  "createFunction" should "should work well" in {
    val resultScala: Box[AnyRef] = InternalConnector.createFunction(
      methodName = "getBank", 
      methodBody = """Future.successful(
                     |  Full((BankCommons(
                     |    BankId("Hello bank id"),
                     |    "1",
                     |    "1",
                     |    "1",
                     |    "1",
                     |    "1",
                     |    "1",
                     |    "1",
                     |    "8"
                     |  ), None))
                     |)""".stripMargin, 
      programmingLang = "Scala"
    )
    val resultJava: Box[AnyRef] = InternalConnector.createFunction(
      methodName = "getBank", 
      methodBody = """package code.bankconnectors;
                     |
                     |import com.openbankproject.commons.model.*;
                     |import java.util.function.Function;
                     |import java.util.function.Supplier;
                     |
                     |/**
                     | * This is a java dynamic function template.
                     | * Must copy the whole content of the file as "dynamic method body".
                     | * Currently, support Java 1.8 version language features
                     | */
                     |public class DynamicJavaConnector implements Supplier<Function<Object[], Object>> {
                     |    private Object apply(Object[] args) {
                     |       BankId bankId = (BankId) args[0];
                     |
                     |       Bank bank = new BankCommons(bankId, "The Java Bank of Scotland",
                     |               "The Royal Bank of Scotland",
                     |               "http://www.red-bank-shoreditch.com/logo.gif",
                     |               "http://www.red-bank-shoreditch.com",
                     |               "OBP",
                     |               "Java",
                     |               "Swift bic value",
                     |               "Java"
                     |               );
                     |       return bank;
                     |    }
                     |
                     |    @Override
                     |    public Function<Object[], Object> get() {
                     |        return this::apply;
                     |    }
                     |}
                     |""".stripMargin, 
      programmingLang = "Java"
    )
    val resultJs: Box[AnyRef] = InternalConnector.createFunction(
      methodName = "getBank", 
      methodBody = """{
                     |    const [bankId] = args;
                     |    // call java or scala type in this way
                     |    const BigDecimal = Java.type('java.math.BigDecimal');
                     |    // define a class
                     |    class SwiftBic{
                     |        constructor(name, value) {
                     |            this.name = name;
                     |            this.value = value;
                     |        }
                     |    }
                     |    // define async function
                     |    const someAsyncFn = async () => new BigDecimal('123.456')
                     |    // call other async methods
                     |    const data = await someAsyncFn();
                     |
                     |    const bank = {
                     |        "bankId":{
                     |            "value":"HelloJsBank:"+ bankId
                     |        },
                     |        "shortName":"The Js Bank of Scotland" +data.toString(),
                     |        "fullName":"The Js Bank of Scotland",
                     |        "logoUrl":"http://www.red-bank-shoreditch.com/logo.gif",
                     |        "websiteUrl":"http://www.red-bank-shoreditch.com",
                     |        "bankRoutingScheme":"OBP",
                     |        "bankRoutingAddress":"Js",
                     |        "swiftBic": new SwiftBic("Mock Swift", 10).name,
                     |        "nationalIdentifier":"Js",
                     |    }
                     |
                     |    return bank;
                     |}""".stripMargin, 
      programmingLang = "Js"
    )

    
    
    resultScala.isDefined shouldBe(true)
    resultJava.isDefined shouldBe(true)
    resultJs.isDefined shouldBe(true)

    val getBankResult = DynamicUtil.executeFunction("getBank", resultScala, Array(BankId("1"), Some(CallContext()))).asInstanceOf[Future[Box[(Bank, Option[CallContext])]]]

    val result: Box[(Bank, Option[CallContext])] = scala.concurrent.Await.result(getBankResult, 5 minutes)


    result.map(_._1.bankId.value) equals Full("Hello bank id")
    
    {
      val getBankResult = DynamicUtil.executeFunction("getBank", resultJava, Array(BankId("1"), Some(CallContext()))).asInstanceOf[Future[Box[(Bank, Option[CallContext])]]]

      val result: Box[(Bank, Option[CallContext])] = scala.concurrent.Await.result(getBankResult, 5 minutes)

      result.map(_._1.fullName) equals Full("The Js Bank of Scotland")
      
    }
    {
      val getBankResult = DynamicUtil.executeFunction("getBank", resultJs, Array(BankId("1"), Some(CallContext()))).asInstanceOf[Future[Box[(Bank, Option[CallContext])]]]

      val result: Box[(Bank, Option[CallContext])] = scala.concurrent.Await.result(getBankResult, 5 minutes)

      result.map(_._1.shortName) equals Full("The Java Bank of Scotland")
    }
    
  }
  
}
