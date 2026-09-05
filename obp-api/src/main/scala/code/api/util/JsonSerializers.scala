package code.api.util

import com.openbankproject.commons.model.enums.{SimpleEnum, SimpleEnumCollection}
import com.openbankproject.commons.model.{JsonFieldReName, ListResult}
import com.openbankproject.commons.util.Functions.Implicits._
import com.openbankproject.commons.util.Functions.Memo
import com.openbankproject.commons.util.{EnumValue, Functions, JsonAble, OBPEnumeration, ReflectUtils, optional}
import net.liftweb.common.Box
import com.openbankproject.commons.util.json
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import net.liftweb.util.StringHelpers

import java.lang.reflect.{Constructor, Modifier, Parameter}
import scala.reflect.ManifestFactory
import scala.reflect.runtime.{universe => ru}

object JsonSerializers {

  object CustomFormats extends DefaultFormats {
    private val defaultFormats =  org.json4s.DefaultFormats
    // losslessDate/UTC re-exports removed on the json4s 4.x bump: they had no
    // consumers, and 4.x no longer exposes losslessDate on the companion.

    /**
     * DefaultFormats#parameterNameReader has bug, when execute fail, cause return Nil, this is not reasonable,
     * Here override it to: when execute fail, try to call constructor.getParamters
     */
    override val parameterNameReader: ParameterNameReader = new ParameterNameReader {
      override def lookupParameterNames(constructor: org.json4s.reflect.Executable): Seq[String] = try {
        defaultFormats.parameterNameReader.lookupParameterNames(constructor)
      } catch {
        case _: Throwable =>
          val underlying: java.lang.reflect.Executable =
            if (constructor.constructor != null) constructor.constructor else constructor.method
          underlying.getParameters.map(_.getName).toIndexedSeq
      }
    }
  }

  val BoxSerializer: JsonBoxSerializer = new JsonBoxSerializer

  val serializers: List[Serializer[_]] =
    BoxSerializer :: AbstractTypeDeserializer :: SimpleEnumDeserializer :: ScalaProductDeserializer ::
      BigDecimalSerializer :: StringDeserializer ::
      FiledRenameSerializer :: EnumValueSerializer ::
      JsonAbleSerializer :: ListResultSerializer.asInstanceOf[Serializer[_]] :: // here must do class cast, or it cause compile error, looks like a bug of scala.
      JavaMathBigDecimalSerializer ::
      ObpCommonsProductSerializer :: ObpCommonsProductDeserializer :: Nil

  implicit val commonFormats: Formats =  CustomFormats ++ serializers

  val nullTolerateFormats = commonFormats + JNothingSerializer

}

trait ObpSerializer[T] extends Serializer[T] {
  override final def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), T] = Functions.doNothing
}

trait ObpDeSerializer[T] extends Serializer[T] {
  override final def serialize(implicit format: Formats): PartialFunction[Any, json.JValue] = Functions.doNothing
}

object JsonAbleSerializer extends ObpSerializer[JsonAble] {

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case JsonAble(jValue) => jValue
  }
}

object EnumValueSerializer extends Serializer[EnumValue] {
  private val IntervalClass = classOf[EnumValue]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), EnumValue] = {
    case (TypeInfo(clazz, _), json) if(IntervalClass.isAssignableFrom(clazz)) => json match {
      case JString(s) =>
        OBPEnumeration.withName(clazz.asInstanceOf[Class[EnumValue]], s)
      case JNull | JNothing => null
      case x => throw new MappingException(s"Can't convert $x to $clazz")
    }
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: EnumValue => JString(x.toString())
  }
}

/**
 * deSerialize trait or abstract type json, this Serializer should always put at formats chain first, e.g:
 * DefaultFormats + AbstractTypeDeserializer + ...others
 */
object AbstractTypeDeserializer extends ObpDeSerializer[AnyRef] {

  private val enumValueClass = classOf[EnumValue]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), AnyRef] = {
    case (TypeInfo(clazz, _), json)
        if Modifier.isAbstract(clazz.getModifiers) && ReflectUtils.isObpClass(clazz)
        && !enumValueClass.isAssignableFrom(clazz)
        && ReflectUtils.findImplementedClass(clazz).isDefined =>
      val commonClass = ReflectUtils.findImplementedClass(clazz).get

      implicit val manifest = ManifestFactory.classType[AnyRef](commonClass)
      json.extract[AnyRef](format, manifest)
  }
}

object SimpleEnumDeserializer extends ObpDeSerializer[SimpleEnum] {
  private val simpleEnumClazz = classOf[SimpleEnum]
  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), SimpleEnum] = {
    case (TypeInfo(clazz, _), json) if simpleEnumClazz.isAssignableFrom(clazz) =>
      val JString(enumValue) = json.asInstanceOf[JString]

      ReflectUtils.getObject(clazz.getName) // get Companion instance
        .asInstanceOf[SimpleEnumCollection[SimpleEnum]]
        .valueOf(enumValue)
  }
}

object ScalaProductDeserializer extends ObpDeSerializer[JValue] {
  private val ScalaProductClazz = classOf[scala.Product]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), JValue]= {
    case (TypeInfo(ScalaProductClazz, _), json) => json match {
      case x => json // if it is ScalaProduct, we just return the JValue back. 
    }
  }
}

object ScalaOptionDeserializer extends ObpDeSerializer[JValue] {
  private val ScalaOptionClazz = classOf[scala.Option[_]]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), JValue]= {
    case (TypeInfo(ScalaOptionClazz, _), json) => json match {
      case x => null // if it is ScalaProduct, we just return the JValue back. 
    }
//    case (TypeInfo(ScalaProductClazz, _), json) => json match {
//      case x => json
//    }
  }
}

object BigDecimalSerializer extends Serializer[BigDecimal] {
  private val IntervalClass = classOf[BigDecimal]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), BigDecimal] = {
    case (TypeInfo(IntervalClass, _), json) => json match {
      case JString(s) => BigDecimal(s)
//      case JDouble(s) => BigDecimal(s)// not safe,from JInt to BigDecimal, it may lose precision
//      case JInt(s) => BigDecimal(s) // not safe,from JInt to BigDecimal, it may lose precision
      case x => throw new MappingException("Can't convert " + x + " to BigDecimal")
    }
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: BigDecimal => JString(x.toString())
  }
}
object JavaMathBigDecimalSerializer extends Serializer[java.math.BigDecimal] {
  private val IntervalClass = classOf[java.math.BigDecimal]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), java.math.BigDecimal] = {
    case (TypeInfo(IntervalClass, _), json) => json match {
      case JString(s) => BigDecimal(s).bigDecimal
      case x => throw new MappingException("Can't convert " + x + " to BigDecimal")
    }
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: java.math.BigDecimal => JString(x.toString())
  }
}

object StringDeserializer extends ObpDeSerializer[String] {
  private val IntervalClass = classOf[String]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), String] = {
    case (TypeInfo(IntervalClass, _), json) if !json.isInstanceOf[JString] && json != JNull && json != JNothing =>
      compactRender(json)
  }
}

/**
 * when do serialize, fields name to snakify,
 * when do deserialize, fields name to camelify
 */
object FiledRenameSerializer extends Serializer[JsonFieldReName] {
  private val clazz = classOf[JsonFieldReName]
  // This field is just a tag to declare current JSON already set field name to camelize, to avoid check field repeatedly
  val resetCamelizeFieldNames = "resetCamelizeFieldNamesIsJustBeTag"

  // optional is Scala-2.13-compiled (obp-commons); ru.typeOf[optional] needs the Scala 2
  // compiler's TypeTag synthesis at the call site, which Scala 3 does not implement for a
  // cross-module type. ReflectUtils.forType does the equivalent lookup from a class name string.
  private val optionalType: ru.Type = ReflectUtils.forType("com.openbankproject.commons.util.optional")

  // ru.typeOf[Long]/[Double]/[Boolean]/... also needs TypeTag synthesis at the call site, which
  // Scala 3 does not implement even for these standard-library types; forType sidesteps it.
  private val longType: ru.Type = ReflectUtils.forType("scala.Long")
  private val intType: ru.Type = ReflectUtils.forType("scala.Int")
  private val shortType: ru.Type = ReflectUtils.forType("scala.Short")
  private val byteType: ru.Type = ReflectUtils.forType("scala.Byte")
  private val doubleType: ru.Type = ReflectUtils.forType("scala.Double")
  private val floatType: ru.Type = ReflectUtils.forType("scala.Float")
  private val booleanType: ru.Type = ReflectUtils.forType("scala.Boolean")

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), JsonFieldReName] = {
    case (typeInfo @ TypeInfo(entityType, _), json) if isNeedRenameFieldNames(entityType, json) => json match {
      case JObject(fieldList) => {
        // add camelize name fields, if exists camelize name field and value is JNull, replace it, e.g:
        // {"full_name": "hello", "fullName": null, "age": 123} -> {"full_name": "hello", "fullName": "hello", "age": 123}
        val renamedJObject = {
          val camelizeFields: List[JField] = for {
            JField(name, value) <- fieldList
            camelizeName = StringHelpers.camelifyMethod(name)
            if name != camelizeName
          } yield JField(camelizeName, value)

          // combine camelize fields and origin fields, and remove duplicated name fields from origin fields.
          val newFields = (JField(resetCamelizeFieldNames, JNull) :: camelizeFields ::: fieldList).distinctBy(_.name)
          JObject(newFields)
        }

        val optionalFields: Map[String, JValue] = getAnnotedFields(entityType, optionalType)
          .map{
            case (name, tp) if(tp <:< longType || tp <:< intType || tp <:< shortType || tp <:< byteType) => (name, JInt(0))
            case (name, tp) if(tp <:< doubleType || tp <:< floatType) => (name, JDouble(0))
            case (name, tp) if(tp <:< booleanType) => (name, JBool(false))
            case (name, _) => (name, JNull)
          }

        val addedNullValues: JValue = if(optionalFields.isEmpty) {
          renamedJObject
        } else {
          val children = renamedJObject.asInstanceOf[JObject].obj
          val nullFields = optionalFields.filter(pair => !children.contains(pair._1)).map(pair => JField(pair._1, pair._2)).toList
          JObject(children ++: nullFields)
        }

        val idFieldToIdValueName: Map[String, String] = getSomeIdFieldInfo(entityType)
        val processedIdJObject = if(idFieldToIdValueName.isEmpty) {
          addedNullValues
        } else {
          addedNullValues.mapField {
            case JField(name, jValue: JString) if idFieldToIdValueName.contains(name) =>
              JField(name, idFieldToIdValueName(name) -> jValue)
            case jField => jField
          }
        }
        Extraction.extract(processedIdJObject,typeInfo).asInstanceOf[JsonFieldReName]
      }
      case x => throw new MappingException("Can't convert " + x + " to JsonFieldReName")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: JsonFieldReName => {
      val ignoreFieldNames = getObjAnnotedFields(x, optionalType)
      val renamedJFields = ReflectUtils.getConstructorArgs(x)
        .filter(pair => !ignoreFieldNames.contains(pair._1))
        .map(pair => {
          val paramName = StringHelpers.snakify(pair._1)
          val paramValue = pair._2
          isSomeId(paramValue) match {
            case false => JField(paramName, Extraction.decompose(paramValue))
            case true => {
              val idValue = ReflectUtils.getConstructorArgs(paramValue).head._2
              JField(paramName, Extraction.decompose(idValue))
            }
          }
        }) .toList
      JObject(renamedJFields)
    }
  }

  private[this] def isNeedRenameFieldNames(entityType: Class[_], jValue: JValue): Boolean = {
    val isJsonFieldRename = clazz.isAssignableFrom(entityType)

    if(isJsonFieldRename && jValue.isInstanceOf[JObject] && (jValue \ resetCamelizeFieldNames) == JNothing) {
      val JObject(obj) = jValue
      val fieldNames = obj.map(_.name)
      fieldNames.map(StringHelpers.camelifyMethod(_)).exists(fieldName => !fieldNames.contains(fieldName))
    } else {
      false
    }
  }

  // check given object is some Id, only type name ends with "Id" and have a single param constructor
  private def isSomeId(obj: Any) = obj match {
    case null => false
    case _ => obj.getClass.getSimpleName.endsWith("Id") && ReflectUtils.getPrimaryConstructor(obj).asMethod.paramLists.headOption.exists(_.size == 1)
  }
  private def isSomeIdType(tp: ru.Type) = tp.typeSymbol.name.toString.endsWith("Id") && ReflectUtils.getConstructorParamInfo(tp).size == 1

  /**
   * extract constructor params those type is some id, and return the field name to the id constructor value name
   * for example:
   * case class Foo(name: String, bankId: BankId(value:String))
   * getSomeIdFieldInfo(typeOf[Foo]) == Map(("bankId" -> "value"))
   * @param clazz to do extract class
   * @return field name to id type single value name
   */
  private def getSomeIdFieldInfo(clazz: Class[_]) = {
    val paramNameToType: Map[String, ru.Type] = ReflectUtils.getConstructorInfo(clazz)
    paramNameToType
      .filter(nameToType => isSomeIdType(nameToType._2))
      .map(nameToType => {
        val (name, paramType) = nameToType
        val singleParamName = ReflectUtils.getConstructorParamInfo(paramType).head._1
        (name, singleParamName)
      }
      )
  }
  private def getAnnotedFields(clazz: Class[_], annotationType: ru.Type): Map[String, ru.Type] = {
    val symbol  = ReflectUtils.classToSymbol(clazz)
    ReflectUtils.getPrimaryConstructor(symbol.toType)
      .paramLists.headOption.getOrElse(Nil)
      .filter(param =>  param.annotations.exists(_.tree.tpe <:< annotationType))
      .map(it => (it.name.toString, it.info))
      .toMap
  }
  private def getObjAnnotedFields(obj: Any, annotationType: ru.Type): Map[String, ru.Type] = getAnnotedFields(obj.getClass, annotationType)
}


/**
 * make tolerate for missing required constructor parameters
 */
object JNothingSerializer extends ObpDeSerializer[Any] {

  // This field is just a tag to declare all the missing fields are added, to avoid check missing field repeatedly
  val addedMissingFields = "addedMissingFieldsThisFieldIsJustBeTag"

  val defaultValue: Map[Class[_ ], JValue] = Map(
    classOf[Boolean] -> JBool(null.asInstanceOf[Boolean]),
    classOf[Byte] -> JInt(null.asInstanceOf[Byte].intValue()),
    classOf[Short] -> JInt(null.asInstanceOf[Short].intValue()),
    classOf[Int] -> JInt(null.asInstanceOf[Int]),
    classOf[Long] -> JInt(null.asInstanceOf[Long].intValue()),
    classOf[Float] -> JDouble(null.asInstanceOf[Float]),
    classOf[Double] -> JDouble(null.asInstanceOf[Double])
  )

  private def addMissingFields(jObject: JObject, missingFieldNames: Map[String, Class[_]]): JObject = {
    val JObject(obj) = jObject
    val missingJFields = missingFieldNames.toList collect {
      case (name, clazz) if defaultValue.contains(clazz) => JField(name, defaultValue(clazz))
      case (name, _)  => JField(name, JNull)
    }
    val newFields: List[JField] = JField(addedMissingFields, JNull) :: obj ::: missingJFields
    JObject(newFields)
  }

  private def isNoMissingFields(jValue: JValue): Boolean = (jValue \ addedMissingFields) != JNothing

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Any] = {
    case JNothingSerializer(typeInfo, jValue: JObject, missingFields) => {
      val newJValue =  addMissingFields(jValue, missingFields)
      Extraction.extract(newJValue, typeInfo)
    }
  }

  private[this] def unapply(arg: (TypeInfo, JValue))(implicit formats: Formats): Option[(TypeInfo, JValue, Map[String, Class[_]])] =  {
    val (TypeInfo(clazz, _), jValue) = arg
    if (! ReflectUtils.isObpClass(clazz) || !jValue.isInstanceOf[JObject] || jValue == JNothing || jValue == JNull || isNoMissingFields(jValue)) {
      None
    } else {
      val jsonFieldNames: Set[String] = jValue.asInstanceOf[JObject].obj.toSet[JField].collect {
        case JField(name, v) if v != JNothing => name
      }

      val missingFields:Map[String, Class[_]] = getMissingFields(clazz, jsonFieldNames)
      missingFields match {
        case x if x.isEmpty  => None
        case x => Some((arg._1, arg._2, x))
      }
    }
  }

  private val memo = new Memo[(Class[_], Set[String]), Map[String, Class[_]]]

  private[this] def getMissingFields(clazz: Class[_], jsonFieldNames: Set[String]): Map[String, Class[_]] =
    memo.memoize(clazz -> jsonFieldNames) {
      val constructors: Array[Constructor[_]] = clazz.getDeclaredConstructors()
      bestMatching(constructors, jsonFieldNames) match {
        case None => Map.empty
        case Some(array: Array[Arg]) =>
          val missingNameToClass = array collect {
            case arg if arg.required && !jsonFieldNames.contains(arg.path) => (arg.path, arg.paramType)
          }
          missingNameToClass.toMap
      }
    }

  /**
   * absolutely simulate org.json4s.Meta.Constructor#bestMatching,
   * to find beast matching constructor parameters
   * @param constructors
   * @param names json object Field Names
   * @return beast matching constructor parameters according json field names.
   */
  private[this] def bestMatching(constructors: Array[Constructor[_]], names: Set[String]): Option[Array[Arg]] = {

    def countOptionals(args: Array[Arg]) =
      args.foldLeft(0)((n, x) => if (x.optional) n+1 else n)
    def score(args: Array[Arg]) =
      args.foldLeft(0)((s, arg) => if (names.contains(arg.path)) s+1 else -100)


    val maybeObject: Option[Array[Arg]] = if (constructors.isEmpty) {
      None
    } else if(constructors.size == 1) {
      constructors.headOption.map(_.getParameters.map(Arg(_)))
    } else {
      val choices: Array[Array[Arg]] = constructors.map(_.getParameters())
        .map(_.map(Arg(_)))

      val best: (Array[Arg], Int) = choices.tail.foldLeft((choices.head, score(choices.head))) { (best, c) =>
        val newScore = score(c)
        if (newScore == best._2) {
          if (countOptionals(c) < countOptionals(best._1))
            (c, newScore) else best
        } else if (newScore > best._2) (c, newScore) else best
      }
      Some(best._1)
    }

    maybeObject
  }

  private case class Arg(private val parameter: Parameter) {
    if (!parameter.isNamePresent) {
      throw new IllegalArgumentException(
        s"""Parameter names are not present!
           |The constructor [${parameter.getDeclaringExecutable.toGenericString}] parameter names are missing.
           |Please check the compiler parameter '-parameters'.
           |""".stripMargin
      )
    }
    val path: String = parameter.getName()
    val paramType: Class[_] = parameter.getType

    val optional: Boolean = {
      val optionClass: Class[Option[_]] = classOf[Option[_]]
      val boxClass: Class[Box[_]] = classOf[Box[_]]
      optionClass.isAssignableFrom(paramType) || boxClass.isAssignableFrom(paramType)
    }
    val required: Boolean = !optional
  }
}


object ListResultSerializer extends Serializer[ListResult[_]] {
  private val clazz = classOf[ListResult[_]]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), ListResult[_]] = {
    case (typeInfoFull @ TypeInfo(entityType, Some(_)), json) if clazz.isAssignableFrom(entityType) => json match {
      case JObject(singleField::Nil) => {
        // json4s passes a package-private SourceType subclass carrying the full ScalaType.
        // Access it via Java reflection to recover non-erased type args without a compile-time
        // dependency on the package-private SourceType trait.
        val resultsItemType: Class[_] = {
          import scala.util.Try
          def invoke(obj: AnyRef, m: String): AnyRef = obj.getClass.getMethod(m).invoke(obj)
          Try {
            val scalaType = invoke(typeInfoFull, "scalaType")
            val listSt    = invoke(scalaType, "typeArgs").asInstanceOf[Seq[_]].head.asInstanceOf[AnyRef]
            val itemSt    = invoke(listSt,    "typeArgs").asInstanceOf[Seq[_]].head.asInstanceOf[AnyRef]
            invoke(itemSt, "erasure").asInstanceOf[Class[_]]
          }.getOrElse(
            throw new MappingException(
              "when do deserialize to type ListResult, should supply exactly type parameter, " +
              "should not give wildcard like this: jValue.extract[ListResult[List[_]]]"
            )
          )
        }
        assume(resultsItemType != classOf[Object], "when do deserialize to type ListResult, should supply exactly type parameter, should not give wildcard like this: jValue.extract[ListResult[List[_]]]")

        val name = singleField.name
        val manifest: Manifest[Any] = ManifestFactory.classType(resultsItemType.asInstanceOf[Class[Any]])
        val results: List[Any] = singleField.value.asInstanceOf[JArray].children.map(_.extract(format, manifest))
        ListResult(name, results)
      }
      case x => throw new MappingException("Can't convert " + x + " to ListResult")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: ListResult[_] => {
      val singleField = JField(x.name, Extraction.decompose(x.results))
      JObject(singleField)
    }
  }

}


/**
 * Serializes any obp-commons (Scala-2.13-compiled) case class by reading its constructor arguments
 * through ReflectUtils (scala.reflect.runtime.universe) instead of letting json4s's default
 * Reflector build a field descriptor for it.
 *
 * json4s's default Reflector-based decompose calls org.json4s.reflect.ScalaSigReader.readField (via
 * scala.quoted.staging, i.e. it launches a Scala 3 compiler run) whenever a field's generic type
 * argument is erased to java.lang.Object on the classfile - which is what happens for an
 * Option[T]/similar field where T is a primitive value type (Boolean, Int, Long, ...; e.g.
 * ViewSpecification.is_firehose: Option[Boolean] or User.isDeleted: Option[Boolean]). readField can
 * only recover the erased type argument by reading TASTy, and a Scala-2.13-compiled class has none,
 * so it always throws NoSuchElementException: None.get for such a field - not only when the
 * offending type is decomposed directly, but recursively, whenever it is reached as a nested field
 * while decomposing some other obp-commons value (e.g. every OutBound message embeds
 * OutboundAdapterCallContext -> User, and User.isDeleted is exactly this shape). ReflectUtils reads
 * Scala-2.13-compiled classes with their own compiler's reflection, which has no such gap, so this
 * sidesteps the problem instead of special-casing individual fields or types. It intercepts every
 * obp-commons Product uniformly (the same shape MapperSerializer once used for the now-removed
 * Mapper[_] entities) so the fix also covers nested/nested-again nulls automatically, since
 * Extraction.decompose re-consults the same Formats for every field value it recurses into.
 *
 * Deliberately scoped to the com.openbankproject.commons package only (obp-commons, always
 * Scala-2.13-compiled) - NOT the code.* package (obp-api, Scala 3-compiled), where reflecting via
 * scala.reflect.runtime.universe has its own, unrelated set of gaps (isVal/isVar/isLazy etc.) that
 * this serializer must not be exposed to.
 */
object ObpCommonsProductSerializer extends ObpSerializer[Product] {
  private val ObpCommonsPackagePrefix = "com.openbankproject.commons."

  // A class's constructor parameter names are fixed once the class is - getConstructorArgs
  // re-derived them (Type lookup + getPrimaryConstructor's full alternatives scan) on every
  // single call, for every obp-commons Product this Formats chain serializes. Cache by Class,
  // the same approach the now-removed MapperSerializer.mapperMethods once used; only the
  // per-instance values still have to be re-read per call.
  private val paramNamesMemo = new Memo[Class[_], List[String]]

  override def serialize(implicit format: Formats): PartialFunction[Any, json.JValue] = {
    case x: Product if x.getClass.getName.startsWith(ObpCommonsPackagePrefix) =>
      val paramNames = paramNamesMemo.memoize(x.getClass) {
        ReflectUtils.getPrimaryConstructor(ReflectUtils.classToType(x.getClass)).paramLists.headOption.getOrElse(Nil).map(_.name.toString)
      }
      json.Extraction.decompose(ReflectUtils.getCallByNameValues(x, paramNames: _*))
  }
}

/**
 * Deserializes JSON into any obp-commons (Scala-2.13-compiled) concrete case class by reading its
 * constructor parameter names/types through ReflectUtils (scala.reflect.runtime.universe) and
 * building the instance directly, instead of letting json4s's default Reflector-based extraction
 * walk the class.
 *
 * This is the extract-direction counterpart of ObpCommonsProductSerializer above, needed for the
 * identical reason: json4s's default Reflector-based extraction calls
 * org.json4s.reflect.ScalaSigReader.readField (via scala.quoted.staging, i.e. a Scala 3 compiler
 * run) whenever a constructor parameter's generic type argument is erased to java.lang.Object on
 * the classfile - which happens for an Option[T] field where T is a primitive value type (Boolean,
 * Int, Long, ...; e.g. User.isDeleted: Option[Boolean]). readField can only recover the erased type
 * argument by reading TASTy, and a Scala-2.13-compiled class has none, so it always throws
 * NoSuchElementException: None.get for such a field. Confirmed by reproducing
 * code.connector.MessageDocTest: extracting an example OutBoundGetAccountsHeld JSON crashes while
 * building its nested `user: User` field - AbstractTypeDeserializer (above) correctly resolves the
 * abstract `User` to the concrete `UserCommons`, but the default Reflector-based extraction of
 * UserCommons itself then hits readField on UserCommons.isDeleted.
 *
 * Unlike the JVM's own generic signature (which erases Option[Boolean]'s type argument to Object),
 * scala.reflect.runtime.universe reads a Scala-2.13-compiled class's ScalaSig directly and returns
 * the real type argument (confirmed empirically: ReflectUtils.getPrimaryConstructor on UserCommons
 * reports `isDeleted`'s type as `Option[Boolean]`, not `Option[Object]`) - so building the instance
 * field-by-field via ReflectUtils sidesteps the gap entirely, the same way
 * ObpCommonsProductSerializer's getConstructorArgs sidesteps it for decompose.
 *
 * Container types (Option/List/Map) are unwrapped by hand, recursing into
 * `extractFieldValue` for each element/value with its own scala-reflect-derived type; a leaf value
 * is extracted by asking json4s to extract into the type's runtime Class directly
 * (`Extraction.extract(jv, TypeInfo(leafClazz, None))`), which re-enters the full Formats chain -
 * so a nested obp-commons class recurses back into this same deserializer (or, if the nested type
 * is itself abstract, into AbstractTypeDeserializer first, which then recurses into this
 * deserializer once it has resolved the concrete class), and a plain type (String, BigDecimal,
 * Date, ...) is handled by json4s's own existing extraction, unaffected by any of this.
 *
 * Deliberately scoped to the com.openbankproject.commons package only (obp-commons, always
 * Scala-2.13-compiled) - NOT the code.* package (obp-api, Scala 3-compiled), mirroring
 * ObpCommonsProductSerializer's scoping for the same reason: reflecting a Scala-3-compiled class
 * via scala.reflect.runtime.universe has its own, unrelated set of gaps this deserializer must not
 * be exposed to.
 */
object ObpCommonsProductDeserializer extends ObpDeSerializer[AnyRef] {
  private val ObpCommonsPackagePrefix = "com.openbankproject.commons."
  private val enumValueClass = classOf[EnumValue]

  private val OptionTypeName = "scala.Option"
  private val ListTypeName = "scala.collection.immutable.List"
  private val MapTypeName = "scala.collection.immutable.Map"

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), AnyRef] = {
    case (TypeInfo(clazz, _), jObject: JObject)
        if !Modifier.isAbstract(clazz.getModifiers)
        && clazz.getName.startsWith(ObpCommonsPackagePrefix)
        && classOf[Product].isAssignableFrom(clazz)
        && !enumValueClass.isAssignableFrom(clazz)
        && ReflectUtils.classToType(clazz).typeSymbol.asClass.isCaseClass =>
      buildInstance(clazz, jObject)
  }

  // ru.typeOf[optional] needs the Scala 2 compiler to synthesise a TypeTag at this call site,
  // which Scala 3 (this file's own compiler) does not implement - built at runtime from the
  // class name instead, same technique used throughout this migration (ReflectUtils.forType).
  private val optionalAnnotationType = ReflectUtils.forType("com.openbankproject.commons.util.optional")

  // Type + constructor param list are fixed once clazz is; buildInstance re-derived them (a
  // classToType lookup + getPrimaryConstructor's full alternatives scan) on every single JSON
  // object extracted, for every obp-commons case class this Formats chain deserializes. Cache by
  // Class, same approach as ObpCommonsProductSerializer.paramNamesMemo above.
  private val constructorInfoMemo = new Memo[Class[_], (ru.Type, List[ru.Symbol])]

  private def buildInstance(clazz: Class[_], jObject: JObject)(implicit format: Formats): AnyRef = {
    val (tp, params) = constructorInfoMemo.memoize(clazz) {
      val t = ReflectUtils.classToType(clazz)
      (t, ReflectUtils.getPrimaryConstructor(t).paramLists.headOption.getOrElse(Nil))
    }
    val args: Seq[Any] = params.map { param =>
      val jv = jObject \ param.name.toString.trim
      // @optional (com.openbankproject.commons.util.optional) marks a field the domain genuinely
      // allows to be absent despite not being Option[T] (e.g. BankCommons.swiftBic: String) - null
      // is the only sensible value for a missing one. Anything else missing here is a required
      // field: let extractFieldValue's own Extraction.extract fall-through throw json4s's normal
      // MappingException for it, the same as a plain (non-Product) required field would get.
      if (jv == JNothing && param.annotations.exists(_.tree.tpe =:= optionalAnnotationType)) null
      else extractFieldValue(jv, param.info)
    }
    ReflectUtils.invokeConstructor(tp, args: _*).asInstanceOf[AnyRef]
  }

  private def extractFieldValue(jv: JValue, tp: ru.Type)(implicit format: Formats): Any = {
    tp.typeSymbol.fullName match {
      case OptionTypeName =>
        jv match {
          case JNothing | JNull => None
          case x => Some(extractFieldValue(x, tp.typeArgs.head))
        }
      case ListTypeName =>
        jv match {
          case JArray(items) => items.map(it => extractFieldValue(it, tp.typeArgs.head))
          case JNothing | JNull => Nil
          case x => throw new MappingException(s"Can't convert $x to $tp")
        }
      case MapTypeName =>
        jv match {
          case JObject(fields) =>
            val valueType = tp.typeArgs(1)
            fields.map { case JField(name, value) => name -> extractFieldValue(value, valueType) }.toMap
          case JNothing | JNull => Map.empty[String, Any]
          case x => throw new MappingException(s"Can't convert $x to $tp")
        }
      case _ =>
        val leafClazz = ReflectUtils.runtimeClass(tp)
        jv match {
          // A missing field is not defaulted here, primitive or not: JNothingSerializer (already
          // earlier in this same Formats chain, see commonFormats) is the mechanism for
          // tolerating a missing field, and it exists for schema evolution - a stored/cached JSON
          // blob that predates a newly-added column. Defaulting missing fields again here widened
          // that tolerance to genuinely untrusted input: an incoming POST body missing a required
          // field is supposed to fail extraction with 400 InvalidJsonFormat, and a blanket default
          // here made CreateViewJson accept a body with only {"name": ...} and no other field as
          // valid (CustomViewsTest's "invalid JSON" scenario: expected 400, got 201) and made a
          // Berlin Group payment body missing its amount silently build a null-carrying instance
          // that later NPEs downstream instead of failing extraction itself
          // (PaymentInitiationServicePISApiTest's "Wrong Json format Body": expected 400, got 500).
          // The one legitimate case for a missing field - a domain type explicitly marked
          // @optional despite not being Option[T], e.g. BankCommons.swiftBic: String - is handled
          // in buildInstance by checking the constructor param's annotations before ever reaching
          // here, so this case doesn't need to special-case it. Falling through to
          // Extraction.extract reproduces json4s's own strict behavior for a missing field.
          case _ => json.Extraction.extract(jv, TypeInfo(leafClazz, None))
        }
    }
  }
}
