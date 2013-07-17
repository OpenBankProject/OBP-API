package code.api

import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import net.liftweb.http.RequestType
import scala.collection.mutable.ConcurrentMap
import scala.collection.mutable.Queue

trait ApiVersionDocumentation {
  val version : String
  
  val apiCalls = Queue[ApiCall]()
  
  def addCall(call : ApiCall) = apiCalls += call
}

case class ApiPath(pathElements: List[PathElement])

trait ApiCall {
  val path : ApiPath
  val inputJson : Option[String]
  val outputJson : Option[String]
  val docString : String
  val requestType : RequestType //TODO: might be nice to remove the dependency on net.liftweb
}

object GeneratedDocumentation {

  type Version = String
  private val docs : scala.collection.concurrent.Map[Version, ApiVersionDocumentation] = 
    new scala.collection.concurrent.TrieMap[Version, ApiVersionDocumentation]()
  
  def apiVersion(version : String) : Option[ApiVersionDocumentation] = docs.get(version)
  
  def addCall(version_ : String, call : ApiCall) = {
    docs.get(version_) match {
      case Some(d) => d.addCall(call)
      case None => {
        val newVersion = new ApiVersionDocumentation{
          val version = version_
        }
        newVersion.addCall(call)
        docs.put(version_, newVersion)
      }
    }
  }
  
}