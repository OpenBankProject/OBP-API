package code.api.util

object StrongCustomerAuthentication extends Enumeration {
  type SCA = Value
  val SMS = Value
  val EMAIL = Value
}

object PemCertificateRole extends Enumeration {
  type ROLE = Value
  val psp_as = Value
  val psp_ic = Value
  val psp_ai = Value
  val psp_pi = Value
}