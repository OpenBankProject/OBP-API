package code.dynamicEntity

import java.util.regex.Pattern

import code.api.util.ErrorMessages.DynamicEntityInstanceValidateFail
import code.api.util.{APIUtil, CallContext, NewStyle}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.{DynamicEntityFieldType, DynamicEntityOperation}
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.DynamicEntityFieldType.DATE_WITH_DAY
import net.liftweb.common.{Box, EmptyBox, Full}
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.util.SimpleInjector
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.util.matching.Regex

object DynamicEntityProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(buildOne _) {}

  def buildOne: MappedDynamicEntityProvider.type = MappedDynamicEntityProvider
}

trait DynamicEntityT {
  def dynamicEntityId: Option[String]
  def entityName: String
  def metadataJson: String

  /**
   * The user who created the DynamicEntity
   * @return
   */
  def userId: String

  /**
   * Add Option(bank_id) to Dynamic Entity.
   * Then we should treat the two cases very separately.
   *
   * bank_id is NULL
   * These are the current "system" Dynamic Entities
   *
   * new Role CanGetAnySystemDynamicEntityInstance (bank_ID IS NULL) = Need this
   *
   * bank_id is NOT NULL
   * These will be the new Bank level Dynamic Entities
   *
   */
  def bankId: Option[String]
  //---------util methods

  lazy val userIdJobject: JObject = ("userId" -> userId)
  private lazy val definition: JObject = parse(metadataJson).asInstanceOf[JObject] merge userIdJobject
  //convert metadataJson to JValue, so the final json field metadataJson have no escaped " to \", have good readable
  lazy val jValue = dynamicEntityId match {
    case Some(id) => {
      val jId: JObject = "dynamicEntityId" -> id
      // add dynamicEntityId to JObject
      definition merge jId
    }
    case None => definition
  }

  /**
   * validate the commit json whether fulfil DynamicEntity schema
   * @param entityJson commit json object to add new instance of given dynamic entity
   * @return return error message
   */
  def validateEntityJson(entityJson: JObject, callContext: Option[CallContext]): Future[Option[String]] = {
    val required: List[String] = (definition \ entityName \ "required").asInstanceOf[JArray].arr.map(_.asInstanceOf[JString].s)

    val missingProperties = required diff entityJson.obj.map(_.name)

    if(missingProperties.nonEmpty) {
      return Future.successful(
        Some(s"The 'required' field's not be fulfilled, missing properties: ${missingProperties.mkString(", ")}")
      )
    }

    val invalidPropertyMsg: List[Future[String]] = for {
      JField(propertyName, propertyDef: JObject) <- (definition \ entityName \ "properties").asInstanceOf[JObject].obj
      propertyTypeName = (propertyDef \ "type").asInstanceOf[JString].s
      propertyValue = entityJson \ propertyName
      typeEnumOption = DynamicEntityFieldType.withNameOption(propertyTypeName)
    } yield {
      val minLength: JValue = propertyDef \ "minLength"
      val maxLength: JValue = propertyDef \ "maxLength"
      (propertyTypeName, typeEnumOption, propertyValue) match {
        case (_, _, JNothing | JNull)                             => Future.successful("") // required properties already checked.

        case (_, Some(typeEnum), v) if !typeEnum.isJValueValid(v) =>
          Future.successful(s"The value of '$propertyName' is wrong, ${typeEnum.wrongTypeMsg}")

        case (t, None, v)  if t.startsWith("reference:") && !v.isInstanceOf[JString] =>
          val errorMsg = s"The type of '$propertyName' is 'reference', value should be a string that represent reference entity's Id"
          Future.successful(errorMsg)

        case (t, None, v) if t.startsWith("reference:")           =>
          val value = v.asInstanceOf[JString].s
          ReferenceType.validateRefValue(t, propertyName, value, callContext)

        case (_, Some(DynamicEntityFieldType.string), v)
          if ! DynamicEntityFieldType.string.isLengthValid(v, minLength, maxLength) =>
          var errorMsg = s"The $propertyName's value length is not correct"
          if(minLength != JNothing) {
            errorMsg += s", minLength: ${minLength.asInstanceOf[JInt].num}"
          }
          if(maxLength != JNothing) {
            errorMsg += s", maxLength: ${maxLength.asInstanceOf[JInt].num}"
          }
          errorMsg += "."
          Future.successful(errorMsg)

        case _                                                    => Future.successful("")
      }
    }

    Future.sequence(invalidPropertyMsg)
    .map(_.filter(_.nonEmpty))
    .collect {
      case Nil => None
      case list => Some(list.mkString("; "))
    }
  }
}

object ReferenceType {

  private def recoverFn(fieldName: String, value: String, entityName: String): PartialFunction[Throwable, String] = {
      case _: Throwable => s"entity '$entityName' not found by the value '$value', the field name is '$fieldName'."
  }
  private def mapFn(fieldName: String, value: String, entityName: String)(queryResult: Any): String = {
    val errorMsg = s"entity '$entityName' not found by the value '$value', the field name is '$fieldName'."
    queryResult match {
      case (null|None|Nil, _: Option[_]) => errorMsg
      case (_: EmptyBox, _: Option[_]) => errorMsg
      case null|None|Nil => errorMsg
      case _: EmptyBox => errorMsg
      case _ => ""
    }
  }
  private def valueNotMatchFn(fieldName: String, value: String, reg: Regex): Future[String] = {
      Future.successful(s"reference type field have wrong format value '$value', field name is '$fieldName', it should be this format: ${reg.regex}")
  }

  private lazy val staticRefTypeToValidateFunction = ListMap[String, (String, String, Option[CallContext])=> Future[String]](
    // single param methods
    "reference:Bank" -> { (fieldName, value, callContext)=>
      NewStyle.function.getBank(BankId(value), callContext)
        .map(mapFn(fieldName, value, "Bank"))
        .recover(recoverFn(fieldName, value, "Bank"))
    },
    "reference:Consumer" -> {(fieldName, value, callContext) =>
      NewStyle.function.getConsumerByConsumerId(value, callContext)
        .map(mapFn(fieldName, value, "Consumer"))
        .recover(recoverFn(fieldName, value, "Consumer"))
    },
    "reference:Customer" -> {(fieldName, value, callContext) =>
        NewStyle.function.getCustomerByCustomerId(value, callContext)
          .map(mapFn(fieldName, value, "Customer"))
          .recover(recoverFn(fieldName, value, "Customer"))
    },
    "reference:MethodRouting" -> {(fieldName, value, callContext) =>
        NewStyle.function.getMethodRoutingById(value, callContext)
          .map(mapFn(fieldName, value, "MethodRouting"))
          .recover(recoverFn(fieldName, value, "MethodRouting"))
    },
    "reference:DynamicEntity" -> {(fieldName, value, callContext) =>
        NewStyle.function.getDynamicEntityById(value, callContext)
          .map(mapFn(fieldName, value, "DynamicEntity"))
          .recover(recoverFn(fieldName, value, "DynamicEntity"))
    },
    "reference:TransactionRequest" -> {(fieldName, value, callContext) =>
        NewStyle.function.getTransactionRequestImpl(TransactionRequestId(value), callContext)
          .map(mapFn(fieldName, value, "TransactionRequest"))
          .recover(recoverFn(fieldName, value, "TransactionRequest"))
    },
    "reference:ProductAttribute" -> {(fieldName, value, callContext) =>
        NewStyle.function.getProductAttributeById(value, callContext)
          .map(mapFn(fieldName, value, "ProductAttribute"))
          .recover(recoverFn(fieldName, value, "ProductAttribute"))
    },
    "reference:AccountAttribute" -> {(fieldName, value, callContext) =>
        NewStyle.function.getAccountAttributeById(value, callContext)
          .map(mapFn(fieldName, value, "AccountAttribute"))
          .recover(recoverFn(fieldName, value, "AccountAttribute"))
    },
    "reference:TransactionAttribute" -> {(fieldName, value, callContext) =>
        NewStyle.function.getTransactionAttributeById(value, callContext)
          .map(mapFn(fieldName, value, "TransactionAttribute"))
          .recover(recoverFn(fieldName, value, "TransactionAttribute"))
    },
    "reference:CustomerAttribute" -> {(fieldName, value, callContext) =>
        NewStyle.function.getCustomerAttributeById(value, callContext)
          .map(mapFn(fieldName, value, "CustomerAttribute"))
          .recover(recoverFn(fieldName, value, "CustomerAttribute"))
    },
    "reference:AccountApplication" -> {(fieldName, value, callContext) =>
        NewStyle.function.getAccountApplicationById(value, callContext)
          .map(mapFn(fieldName, value, "AccountApplication"))
          .recover(recoverFn(fieldName, value, "AccountApplication"))
    },
    "reference:CardAttribute" -> {(fieldName, value, callContext) =>
        NewStyle.function.getCardAttributeById(value, callContext)
          .map(mapFn(fieldName, value, "CardAttribute"))
          .recover(recoverFn(fieldName, value, "CardAttribute"))
    },
    "reference:Counterparty" -> {(fieldName, value, callContext) =>
        NewStyle.function.getCounterpartyByCounterpartyId(CounterpartyId(value), callContext)
          .map(mapFn(fieldName, value, "Counterparty"))
          .recover(recoverFn(fieldName, value, "Counterparty"))
    },

    // double parameters methods
    "reference:Branch:bankId&branchId" -> {(fieldName, value, callContext) =>
      val reg = "bankId=(.+)&branchId=(.+)".r
      value match {
        case reg(a, b) => NewStyle.function.getBranch(BankId(a), BranchId(b), callContext)
          .map(mapFn(fieldName, value, "Branch"))
          .recover(recoverFn(fieldName, value, "Branch"))
        case _ => valueNotMatchFn(fieldName, value, reg)
      }
    },
    "reference:Atm:bankId&atmId" -> {(fieldName, value, callContext) =>
      val reg = "bankId=(.+)&atmId=(.+)".r
      value match {
        case reg(a, b) => NewStyle.function.getAtm(BankId(a), AtmId(b), callContext)
          .map(mapFn(fieldName, value, "Atm"))
          .recover(recoverFn(fieldName, value, "Atm"))
        case _ => valueNotMatchFn(fieldName, value, reg)
      }
    },
    "reference:BankAccount:bankId&accountId" -> {(fieldName, value, callContext) =>
      val reg = "bankId=(.+)&accountId=(.+)".r
      value match {
        case reg(a, b) => NewStyle.function.getBankAccount(BankId(a), AccountId(b), callContext)
          .map(mapFn(fieldName, value, "BankAccount"))
        .recover(recoverFn(fieldName, value, "BankAccount"))
        case _ => valueNotMatchFn(fieldName, value, reg)
      }
    },
    "reference:Product:bankId&productCode" -> {(fieldName, value, callContext) =>
      val reg = "bankId=(.+)&productCode=(.+)".r
      value match {
        case reg(a, b) => NewStyle.function.getProduct(BankId(a), ProductCode(b), callContext)
          .map(mapFn(fieldName, value, "Product"))
          .recover(recoverFn(fieldName, value, "Product"))
        case _ => valueNotMatchFn(fieldName, value, reg)
      }
    },
    "reference:PhysicalCard:bankId&cardId" -> {(fieldName, value, callContext) =>
      val reg = "bankId=(.+)&cardId=(.+)".r
      value match {
        case reg(a, b) => NewStyle.function.getPhysicalCardForBank(BankId(a), b, callContext)
          .map(mapFn(fieldName, value, "PhysicalCard"))
          .recover(recoverFn(fieldName, value, "PhysicalCard"))
        case _ => valueNotMatchFn(fieldName, value, reg)
      }
    },

    // three parameters methods
    "reference:Transaction:bankId&accountId&transactionId" -> {(fieldName, value, callContext) =>
      val reg = "bankId=(.+)&accountId=(.+)&transactionId=(.+)".r
      value match {
        case reg(a, b, c) => NewStyle.function.getTransaction(BankId(a), AccountId(b), TransactionId(c), callContext)
          .map(mapFn(fieldName, value, "Transaction"))
          .recover(recoverFn(fieldName, value, "Transaction"))
        case _ => valueNotMatchFn(fieldName, value, reg)
      }
    },
    "reference:Counterparty:bankId&accountId&counterpartyId" -> {(fieldName, value, callContext) =>
      val reg = "bankId=(.+)&accountId=(.+)&counterpartyId=(.+)".r
      value match {
        case reg(a, b, c) => NewStyle.function.getCounterpartyTrait(BankId(a), AccountId(b), c, callContext)
          .map(mapFn(fieldName, value, "Counterparty"))
          .recover(recoverFn(fieldName, value, "Counterparty"))
        case _ => valueNotMatchFn(fieldName, value, reg)
      }
    }
  )

  def referenceTypeNames: List[String] = {
    val dynamicRefs: List[String] = NewStyle.function.getDynamicEntities()
      .map(entity => s"reference:${entity.entityName}")

    val staticRefs: List[String] = staticRefTypeToValidateFunction.keys.toList
     dynamicRefs ++: staticRefs
  }

  // Regex to extract parameters, e.g: "reference:BankAccount:bankId&accountId" extract to "bankId&accountId"
  private val RefParamRegx = "reference:(?:[^:]+):?(.+)?".r

  /**
   * check whether given value is legal reference value.
   * note: won't check whether params in value exists corresponding refer instances
   * @param referenceType
   * @param value
   * @return true if value is legal format
   */
  def isLegalReferenceValue(referenceType: String, value: String): Boolean = {
      referenceType match {
        case RefParamRegx(null) => StringUtils.isNotBlank(value)
        case RefParamRegx(v) =>
          // e.g: convert "bankId&accountId" to "bankId=(.+)&accountId=(.+)"
          val valuePatternStr = v.replaceAll("(?=&)|$", "=(.+)")
          Pattern.compile(valuePatternStr).matcher(value).matches()
        case _ => false
    }
  }

  def referenceTypeAndExample: List[String] = {
    val exampleId1 = APIUtil.generateUUID()
    val exampleId2 = APIUtil.generateUUID()
    val exampleId3 = APIUtil.generateUUID()
    val exampleId4 = APIUtil.generateUUID()
    val reg1 = """reference:([^:]+)""".r
    val reg2 = """reference:(?:[^:]+):([^&]+)&([^&]+)""".r
    val reg3 = """reference:(?:[^:]+):([^&]+)&([^&]+)&([^&]+)""".r
    val reg4 = """reference:(?:[^:]+):([^&]+)&([^&]+)&([^&]+)&([^&]+)""".r
    referenceTypeNames.zipWithIndex.map { pair =>
      val (refTypeName, index) = pair
      val example = refTypeName match {
        case reg1(_) => exampleId1
        case reg2(a, b) => s"$a=$exampleId1&$b=$exampleId2"
        case reg3(a, b, c) => s"$a=$exampleId1&$b=$exampleId2&$c=$exampleId3"
        case reg4(a, b, c, d) => s"$a=$exampleId1&$b=$exampleId2&$c=$exampleId3&$d=$exampleId4"
        case _ => throw new RuntimeException(s"wrong reference type: $refTypeName")
      }
      s""""someField$index": {
         |    "type": "$refTypeName",
         |    "example": "$example"
         |}
         |""".stripMargin
    }
  }

  def validateRefValue(typeName: String, propertyName: String, value: String, callContext: Option[CallContext]): Future[String] = {
    if(staticRefTypeToValidateFunction.contains(typeName)) {
      staticRefTypeToValidateFunction.get(typeName).get.apply(propertyName, value, callContext)
    } else {
      val dynamicEntityName = typeName.replace("reference:", "")
      val errorMsg = s"""$dynamicEntityName not found by the id value '$value', propertyName is '$propertyName'"""
      NewStyle.function.invokeDynamicConnector(DynamicEntityOperation.GET_ONE,dynamicEntityName, None, Some(value), None, callContext)
        .recover {
          case _: Throwable => errorMsg
        }
        .map {
          case (Full(_), _) => ""
          case _ => errorMsg
        }
    }
  }
}

case class DynamicEntityCommons(entityName: String,
                                metadataJson: String,
                                dynamicEntityId: Option[String] = None,
                                userId: String,
                                bankId: Option[String] 
                               ) extends DynamicEntityT with JsonFieldReName

object DynamicEntityCommons extends Converter[DynamicEntityT, DynamicEntityCommons] {

  /**
   * create DynamicEntityCommons object, and do validation
   *
   * @param jsonObject the follow schema json:
   * {{{
   *   {
   *     "BankId": "gh.29.uk",
   *     "FooBar": {
   *         "description": "description of this entity, can be markdown text.",
   *         "required": [
   *             "name"
   *         ],
   *         "properties": {
   *             "name": {
   *                 "type": "string",
   *                 "minLength": 3,
   *                 "maxLength": 20,
   *                 "example": "James Brown",
   *                 "description":"description of **name** field, can be markdown text."
   *             },
   *             "number": {
   *                 "type": "integer",
   *                 "example": "698761728934",
   *                 "description": "description of **number** field, can be markdown text."
   *             }
   *         }
   *     }
   * }
   * }}}
   * @param dynamicEntityId
   * @return object of DynamicEntityCommons
   */
  def apply(jsonObject: JObject, dynamicEntityId: Option[String], userId: String): DynamicEntityCommons = {

    def checkFormat(requirement: Boolean, message: String) = {
      if (!requirement) throw new IllegalArgumentException(message)
    }

    val fields = jsonObject.obj

    // validate whether json is object and have a single field, currently support one entity definition
    checkFormat(fields.nonEmpty, s"$DynamicEntityInstanceValidateFail The Json root object should have a single entity, but current have none.")
    checkFormat(fields.size <= 2, s"$DynamicEntityInstanceValidateFail The Json root object should at most two fields: entity and BankId, but current entityNames: ${fields.map(_.name).mkString(",  ")}")

    val bankId: Option[String] = fields.filter(_.name=="bankId").map(_.value.asInstanceOf[JString].values).headOption
    
    val JField(entityName, metadataJson) = fields.filter(_.name!="bankId").head
    
    val namePattern = "[-_A-Za-z0-9]+".r.pattern
    // validate entity name
    checkFormat(namePattern.matcher(entityName).matches(), s"$DynamicEntityInstanceValidateFail The entity name should contains characters [-_A-Za-z0-9], but current entity name: $entityName")

    // validate entityName corresponding value is json object
    val metadataStr = compactRender(metadataJson)
    checkFormat(metadataJson.isInstanceOf[JObject], s"$DynamicEntityInstanceValidateFail The $entityName should have an object value, but current value is: $metadataStr")

    val required = metadataJson \ "required"

    // validate 'required' field exists and is a json array[string]
    checkFormat(required != JNothing , s"$DynamicEntityInstanceValidateFail There must be 'required' field in $entityName, and type is json array[string]")
    checkFormat(required.isInstanceOf[JArray] && required.asInstanceOf[JArray].arr.forall(_.isInstanceOf[JString]), s"$DynamicEntityInstanceValidateFail The 'required' field's type of $entityName should be array[string]")

    val description = metadataJson \ "description"
    // validate 'description' field, if exists it must be JString type and not blank
    if(description != JNothing) {
      checkFormat(description.isInstanceOf[JString] , s"$DynamicEntityInstanceValidateFail The 'description' field in $entityName must be string type.")
      val JString(entityDescription) = description.asInstanceOf[JString]
      checkFormat(entityDescription.nonEmpty , s"$DynamicEntityInstanceValidateFail The 'description' field in $entityName must be a not empty string value.")
    }

    val properties = metadataJson \ "properties"

    // validate 'properties' field exists and is json object
    checkFormat(properties != JNothing , s"$DynamicEntityInstanceValidateFail There must be 'required' field in $entityName, and type is array[string]")
    checkFormat(properties.isInstanceOf[JObject], s"$DynamicEntityInstanceValidateFail The 'properties' field's type of $entityName should be json object")

    val propertiesObj = properties.asInstanceOf[JObject]

    val requiredFields = required.asInstanceOf[JArray].arr.map(_.asInstanceOf[JString].s)

    val allFields = propertiesObj.obj

    val missingRequiredFields = requiredFields diff allFields.map(_.name)

    checkFormat(missingRequiredFields.isEmpty , s"$DynamicEntityInstanceValidateFail missing properties: ${missingRequiredFields.mkString(", ")}")

    // validate there is no required field missing in properties
    val notFoundRequiredField = requiredFields.diff(allFields.map(_.name))
    checkFormat(metadataJson.isInstanceOf[JObject], s"$DynamicEntityInstanceValidateFail In the $entityName, all 'required' fields should be present, these are missing: ${notFoundRequiredField.mkString(", ")}")

    // validate all properties have a type and example
    allFields.foreach(field => {
      val JField(fieldName, value) = field
      // validate field name
      checkFormat(namePattern.matcher(fieldName).matches(), s"$DynamicEntityInstanceValidateFail The field name should contains characters [-_A-Za-z0-9], the wrong field name: $fieldName")

      checkFormat(value.isInstanceOf[JObject], s"$DynamicEntityInstanceValidateFail The property of $fieldName's type should be json object")

      // 'type' exists and value should be one of allowed type
      val fieldType = value \ "type"
      val fieldTypeName = fieldType.asInstanceOf[JString].s

      checkFormat(fieldType.isInstanceOf[JString] && fieldTypeName.nonEmpty, s"$DynamicEntityInstanceValidateFail The property of $fieldName's 'type' field should be exists and type is json string")
      checkFormat(allowedFieldType.contains(fieldTypeName), s"$DynamicEntityInstanceValidateFail The property of $fieldName's 'type' field should be one of these string value: ${allowedFieldType.mkString(", ")}")

      val fieldTypeOp: Option[DynamicEntityFieldType] = DynamicEntityFieldType.withNameOption(fieldTypeName)

      if(fieldTypeOp.exists(_ == DynamicEntityFieldType.string)) {
        val minLength = value \ "minLength"
        val maxLength = value \ "maxLength"
        def toInt(jValue: JValue) = jValue.asInstanceOf[JInt].num.intValue()
        if(minLength != JNothing) {
          checkFormat(minLength.isInstanceOf[JInt], s"$DynamicEntityInstanceValidateFail The property of minLength's 'type' should be integer")
          checkFormat(toInt(minLength) >= 0, s"$DynamicEntityInstanceValidateFail The property of minLength value should be non-negative integer, current value: ${toInt(minLength)}")
        }
        if(maxLength != JNothing) {
          checkFormat(maxLength.isInstanceOf[JInt], s"$DynamicEntityInstanceValidateFail The property of maxLength's 'type' should be integer")
          checkFormat(toInt(maxLength) >= 0, s"$DynamicEntityInstanceValidateFail The property of minLength value should be non-negative integer, current value: ${toInt(maxLength)}")
        }
        if(minLength != JNothing && maxLength != JNothing) {
          checkFormat(toInt(minLength) < toInt(maxLength), s"$DynamicEntityInstanceValidateFail The property of minLength value should be less than maxLength, minLength: ${toInt(minLength)}, maxLength: ${toInt(maxLength)}")
        }
      }


      // example is exists
      val fieldExample = value \ "example"
      checkFormat(fieldExample != JNothing, s"$DynamicEntityInstanceValidateFail The property of $fieldName's 'example' field should be exists")
      // example type is correct
      if(fieldTypeOp.isDefined) {
        val Some(dEntityFieldType: DynamicEntityFieldType) = fieldTypeOp
        checkFormat(dEntityFieldType.isJValueValid(fieldExample),
          s"$DynamicEntityInstanceValidateFail The value of $fieldName's 'example' is wrong, ${dEntityFieldType.wrongTypeMsg}")
      } else if(ReferenceType.referenceTypeNames.contains(fieldTypeName)) {
        checkFormat(fieldExample.isInstanceOf[JString], s"$DynamicEntityInstanceValidateFail The property of $fieldName's 'example' field should be type ${DynamicEntityFieldType.string}")
        checkFormat(ReferenceType.isLegalReferenceValue(fieldTypeName, fieldExample.asInstanceOf[JString].s), s"$DynamicEntityInstanceValidateFail The property of $fieldName's 'example' is illegal format.")
      } else {
        // if go to here, means add new field type, but not supply corresponding value validation, that means a bug need fix to avoid throw the follow Exception
        throw new RuntimeException(s"DynamicEntity $entityName's field $fieldName, type is $fieldTypeName, this type is not do validation.")
      }


      val propertyDescription = value \ "description"
      // validate 'description' field, if exists it must be JString type and not blank
      if(propertyDescription != JNothing) {
        checkFormat(propertyDescription.isInstanceOf[JString] , s"$DynamicEntityInstanceValidateFail The property of $fieldName's 'description' field in $entityName must be string type.")
        val JString(descriptionValue) = propertyDescription.asInstanceOf[JString]
        checkFormat(descriptionValue.nonEmpty , s"$DynamicEntityInstanceValidateFail The property of $fieldName's 'description' field in $entityName must be a not empty string value.")
      }
    })

    DynamicEntityCommons(entityName, compactRender(jsonObject), dynamicEntityId, userId, bankId)
  }

  private def allowedFieldType: List[String] = DynamicEntityFieldType.values.map(_.toString) ++: ReferenceType.referenceTypeNames
}

/**
 * example case classes, as an example schema of DynamicEntity, for request body example usage
 * @param FooBar
 */
case class DynamicEntityFooBar(bankId: Option[String], FooBar: DynamicEntityDefinition, dynamicEntityId: Option[String] = None, userId: Option[String] = None)
case class DynamicEntityDefinition(description: String, required: List[String],properties: DynamicEntityFullBarFields)
case class DynamicEntityFullBarFields(name: DynamicEntityStringTypeExample, number: DynamicEntityIntTypeExample)
case class DynamicEntityStringTypeExample(`type`: DynamicEntityFieldType, minLength: Int, maxLength: Int, example: String, description: String)
case class DynamicEntityIntTypeExample(`type`: DynamicEntityFieldType, example: Int, description: String)
//-------------------example case class end


trait DynamicEntityProvider {
  def getById(dynamicEntityId: String): Box[DynamicEntityT]

  def getByEntityName(entityName: String): Box[DynamicEntityT]

  def getDynamicEntities(): List[DynamicEntityT]
  
  def getDynamicEntitiesByUserId(userId: String): List[DynamicEntity]

  def getDynamicEntitiesByBankId(bankId: String): List[DynamicEntity]

  def createOrUpdate(dynamicEntity: DynamicEntityT): Box[DynamicEntityT]

  def delete(dynamicEntity: DynamicEntityT):Box[Boolean]
}






