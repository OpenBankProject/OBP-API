package code.model.implementedTraits

import code.model.traits.{OtherBankAccountMetadata,OtherBankAccount}

class OtherBankAccountImpl(id_ : String, label_ : String, nationalIdentifier_ : String,
	swift_bic_ : Option[String], iban_ : Option[String], metadata_ : OtherBankAccountMetadata) extends OtherBankAccount
{

	def id = id_
	def label = label_
	def nationalIdentifier = nationalIdentifier_
	def swift_bic = swift_bic_
	def iban = iban_
	def metadata = metadata_
}