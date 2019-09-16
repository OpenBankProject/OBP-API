package com.openbankproject.commons.model.enums

import com.openbankproject.commons.util.{EnumValue, OBPEnumeration}

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
sealed trait DynamicEntityFieldType extends EnumValue
object DynamicEntityFieldType extends OBPEnumeration[DynamicEntityFieldType]{
 object string  extends Value
 object number extends Value
 object integer extends Value
 object boolean extends Value
// object array extends Value
// object `object` extends Value //TODO in the future, we consider support nested type
}