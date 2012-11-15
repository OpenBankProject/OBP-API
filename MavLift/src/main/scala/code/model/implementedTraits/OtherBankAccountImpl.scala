/** 
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License.      

Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
		by 
		Simon Redfern : simon AT tesobe DOT com
		Everett Sochowski: everett AT tesobe DOT com
    Benali Ayoub : ayoub AT tesobe DOT com

 */
package code.model.implementedTraits

import code.model.traits.{OtherBankAccountMetadata,OtherBankAccount}

class OtherBankAccountImpl(id_ : String, label_ : String, nationalIdentifier_ : String,
	swift_bic_ : Option[String], iban_ : Option[String], number_ : String,
	bankName_ : String, metadata_ : OtherBankAccountMetadata) extends OtherBankAccount
{

	def id = id_
	def label = label_
	def nationalIdentifier = nationalIdentifier_
	def swift_bic = swift_bic_
	def iban = iban_
	def number = number_
	def bankName = bankName_
	def metadata = metadata_
}