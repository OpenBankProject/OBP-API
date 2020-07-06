package com.openbankproject.commons.model.enums

import java.time.format.DateTimeFormatter

import com.openbankproject.commons.util.{EnumValue, OBPEnumeration}
import net.liftweb.common.Box
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.{JBool, JDouble, JInt, JValue}

sealed trait AccountAttributeType extends EnumValue
object AccountAttributeType extends OBPEnumeration[AccountAttributeType]{
  object STRING         extends Value
  object INTEGER        extends Value
  object DOUBLE         extends Value
  object DATE_WITH_DAY  extends Value
}

sealed trait ProductAttributeType extends EnumValue
object ProductAttributeType extends OBPEnumeration[ProductAttributeType]{
  object STRING        extends Value
  object INTEGER       extends Value
  object DOUBLE        extends Value
  object DATE_WITH_DAY extends Value
}

sealed trait CardAttributeType extends EnumValue
object CardAttributeType extends  OBPEnumeration[CardAttributeType]{
  object STRING        extends Value
  object INTEGER       extends Value
  object DOUBLE        extends Value
  object DATE_WITH_DAY extends Value
}

sealed trait CustomerAttributeType extends EnumValue
object CustomerAttributeType extends  OBPEnumeration[CustomerAttributeType]{
  object STRING        extends Value
  object INTEGER       extends Value
  object DOUBLE        extends Value
  object DATE_WITH_DAY extends Value
}

sealed trait TransactionAttributeType extends EnumValue
object TransactionAttributeType extends  OBPEnumeration[TransactionAttributeType]{
  object STRING        extends Value
  object INTEGER       extends Value
  object DOUBLE        extends Value
  object DATE_WITH_DAY extends Value
}

//------api enumerations ----
sealed trait StrongCustomerAuthentication extends EnumValue
object StrongCustomerAuthentication extends OBPEnumeration[StrongCustomerAuthentication] {
  type SCA = Value
  object SMS extends Value
  object EMAIL extends Value
  object DUMMY extends Value
  object UNDEFINED extends Value
}

sealed trait PemCertificateRole extends EnumValue
object PemCertificateRole extends OBPEnumeration[PemCertificateRole] {
  type ROLE = Value
  object PSP_AS extends Value
  object PSP_IC extends Value
  object PSP_AI extends Value
  object PSP_PI extends Value
}
//------api enumerations end ----

sealed trait DynamicEntityFieldType extends EnumValue {
  val jValueType: Class[_]
  def isJValueValid(jValue: JValue): Boolean = jValueType.isInstance(jValue)
  def wrongTypeMsg = s"the value's type should be $this."
}
object DynamicEntityFieldType extends OBPEnumeration[DynamicEntityFieldType]{
 object string  extends Value{val jValueType = classOf[JString]}
 object number  extends Value{val jValueType = classOf[JDouble]}
 object integer extends Value{val jValueType = classOf[JInt]}
 object boolean extends Value{val jValueType = classOf[JBool]}

 object DATE_WITH_DAY extends Value {
   val jValueType = classOf[JString]
   val dateFormat = "yyyy-MM-dd"
   override def isJValueValid(jValue: JValue): Boolean = {
     super.isJValueValid(jValue) && {
       val value = jValue.asInstanceOf[JString].s
       Box.tryo{
         DateTimeFormatter.ofPattern(dateFormat).parse(value)
       }.isDefined
     }
   }

   override def wrongTypeMsg: String = s"the value's type should be $this, format is $dateFormat."
 }
 //object array extends Value{val jValueType = classOf[JArray]}
 //object `object` extends Value{val jValueType = classOf[JObject]} //TODO in the future, we consider support nested type
}

/**
 * connector support operation type for DynamicEntity
 */
sealed trait DynamicEntityOperation extends EnumValue
object DynamicEntityOperation extends OBPEnumeration[DynamicEntityOperation] {
  object GET_ALL extends Value
  object GET_ONE extends Value
  object CREATE extends Value
  object UPDATE extends Value
  object DELETE extends Value
}

sealed trait LanguageParam extends EnumValue
object LanguageParam extends OBPEnumeration[LanguageParam] {
  object EN extends Value
  object ZH extends Value
}


sealed trait AttributeType extends EnumValue
object AttributeType extends OBPEnumeration[AttributeType]{
  object STRING extends Value
  object INTEGER extends Value
  object DOUBLE extends Value
  object DATE_WITH_DAY extends Value
}

sealed trait AttributeCategory extends EnumValue
object AttributeCategory extends OBPEnumeration[AttributeCategory]{
  object Customer extends Value
  object Product extends Value
  object Account extends Value
  object Transaction extends Value
  object Card extends Value
}

object TransactionRequestStatus extends Enumeration {
  type TransactionRequestStatus = Value
  val INITIATED, PENDING, NEXT_CHALLENGE_PENDING, FAILED, COMPLETED, FORWARDED, REJECTED = Value
}