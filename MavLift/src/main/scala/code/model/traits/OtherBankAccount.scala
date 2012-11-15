package code.model.traits

trait OtherBankAccount {

	def id : String
	//account holder hame
	def label : String
	def nationalIdentifier : String
	def bankName : String
	def number : String
  	def swift_bic : Option[String]
  	def iban : Option[String]
  	def metadata : OtherBankAccountMetadata
}
