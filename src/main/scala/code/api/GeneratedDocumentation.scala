package code.api

import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import net.liftweb.http.RequestType

trait ApiVersionDocumentation {
  val version : String
  
  val apiCalls : scala.collection.mutable.Queue[ApiCall]
  
  def addCall(call : ApiCall) = apiCalls += call
}

trait ApiCall {
  val path : List[PathElement]
  val inputJson : Option[String]
  val outputJson : Option[String]
  val docString : String
  val requestType : RequestType //TODO: probably want to not make this depend on net.liftweb
}

object GeneratedDocumentation {

  type Version = String
  //TODO: Scala 2.10 concurrentMap
  val docs : scala.collection.mutable.Map[Version, ApiVersionDocumentation] = 
    new HashMap[Version, ApiVersionDocumentation]() with SynchronizedMap[Version, ApiVersionDocumentation]
  
  def apiVersion(version : String) : Option[ApiVersionDocumentation] = docs.get(version)
  
}