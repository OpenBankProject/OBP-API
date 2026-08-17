package code.api.dynamic.entity

import code.DynamicData.{DynamicData, DynamicDataProvider}
import code.api.Constant.PARAM_LOCALE
import code.api.dynamic.entity.helper._
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.util.Helper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.model.enums._
import com.openbankproject.commons.util.{ApiVersion, JsonUtils}
import net.liftweb.common._
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

trait APIMethodsDynamicEntity {

  val ImplementationsDynamicEntity = new ImplementationsDynamicEntity()

  class ImplementationsDynamicEntity {

    val implementedInApiVersion = ApiVersion.`dynamic-entity`

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()

    // createDynamicEntityDoc and updateDynamicEntityDoc are dynamic, So here dynamic create resourceDocs
    def resourceDocs = staticResourceDocs

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)

    private def unboxResult[T: Manifest](box: Box[T], entityName: String): T = {
      if (box.isInstanceOf[Failure]) {
        val failure = box.asInstanceOf[Failure]
        // change the internal db column name 'dynamicdataid' to entity's id name
        val msg = failure.msg.replace(DynamicData.idColumnName, StringUtils.uncapitalize(entityName) + "Id")
        val changedMsgFailure = failure.copy(msg = s"$InternalServerError $msg")
        fullBoxOrException[T](changedMsgFailure)
      }

      box.openOrThrowException("impossible error")
    }

  }
}

object APIMethodsDynamicEntity extends APIMethodsDynamicEntity {
  lazy val newStyleEndpoints: List[(String, String)] = ImplementationsDynamicEntity.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

