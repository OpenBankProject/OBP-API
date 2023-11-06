package code

import code.api.util.{APIUtil, CustomJsonFormats}
import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.{Extraction, Formats}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
import redis.clients.jedis.Jedis

class RedisTest extends FeatureSpec with Matchers with GivenWhenThen with MdcLoggable  {

  val url = APIUtil.getPropsValue("cache.redis.url", "127.0.0.1")
  val port = APIUtil.getPropsAsIntValue("cache.redis.port", 6379)
  implicit def formats: Formats = CustomJsonFormats.formats
  
  lazy val jedis = new Jedis(url, port)
  
  feature("The Unit test for Redis")
  {
    scenario("test the simple key value")
    {
      jedis.set("BasicObjectKey", "this is for testing")
      val result = jedis.get("BasicObjectKey")
      result should equal ("this is for testing")
      val result2 = jedis.get("BasicObjectKey1")
      val result3 = jedis.get("BasicObjectKey1")
    }
    
    scenario("test the basic case class")
    {
      case class BasicObject(
        customer_number: String,
        legal_name: String
      )
      
      val basicObject = BasicObject("customer_number_123","Tom")
      val redisValue = json.compactRender(Extraction.decompose(basicObject))
      jedis.set("BasicObjectKey", redisValue)
      val result = jedis.get("BasicObjectKey")
      println(result)
      result should equal (""""[{"$outer":{},"customer_number":"customer_number_123","legal_name":"Tom"}]"""")
    }
    
  }

 

 
  
}