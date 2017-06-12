package code.bankconnectors

import com.sksamuel.avro4s.SchemaFor

/**
  * Defines avro schemas for case classes used in communication via kafka.
  *
  * Example:
  * implicit val schemaForMyCaseClass = SchemaFor[MyCaseClass]
  *
  */
object AvroSchemas {
  implicit val schemaForGetBanks = SchemaFor[GetBanks]

}
