package code.api.util

import code.api.util.APIUtil.{ApiRelation, ResourceDoc}

import scala.collection.mutable.ArrayBuffer

trait ScannedApis {
  val apiVersion: ScannedApiVersion
  val resourceDocs: ArrayBuffer[ResourceDoc]
  val apiRelations: ArrayBuffer[ApiRelation]
}

object ScannedApis {

}
