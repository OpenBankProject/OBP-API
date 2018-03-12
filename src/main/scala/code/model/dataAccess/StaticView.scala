/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */

package code.model.dataAccess

import code.model.{ViewId, _}


/**
  * StaticPublicViewDefinition will be shared by all the system public views.
  */

trait StaticPublicViewDefinition extends ViewDefinition {
  
  final def viewId: ViewId = ViewId("public")
  override final def name: String = "Public"
  override final def description: String = "Public View"
  
  override final def isSystem: Boolean = true
  override final def isFirehose: Boolean = true
  override final def isPublic: Boolean = true
  
  
  override final def usePublicAliasIfOneExists: Boolean = true
  override final def usePrivateAliasIfOneExists: Boolean = false
  override final def hideOtherAccountMetadataIfAlias: Boolean = true
  override final def canSeeTransactionThisBankAccount: Boolean = true
  override final def canSeeTransactionOtherBankAccount: Boolean = true
  override final def canSeeTransactionMetadata: Boolean = true
  override final def canSeeTransactionDescription: Boolean = false
  override final def canSeeTransactionAmount: Boolean = true
  override final def canSeeTransactionType: Boolean = true
  override final def canSeeTransactionCurrency: Boolean = true
  override final def canSeeTransactionStartDate: Boolean = true
  override final def canSeeTransactionFinishDate: Boolean = true
  override final def canSeeTransactionBalance: Boolean = true
  override final def canSeeComments: Boolean = true
  override final def canSeeOwnerComment: Boolean = true
  override final def canSeeTags: Boolean = true
  override final def canSeeImages: Boolean = true
  override final def canSeeBankAccountOwners: Boolean = true
  override final def canSeeBankAccountType: Boolean = true
  override final def canSeeBankAccountBalance: Boolean = true
  override final def canSeeBankAccountCurrency: Boolean = true
  override final def canSeeBankAccountLabel: Boolean = true
  override final def canSeeBankAccountNationalIdentifier: Boolean = true
  override final def canSeeBankAccountSwift_bic: Boolean = true
  override final def canSeeBankAccountIban: Boolean = true
  override final def canSeeBankAccountNumber: Boolean = true
  override final def canSeeBankAccountBankName: Boolean = true
  override final def canSeeBankRoutingScheme: Boolean = true
  override final def canSeeBankRoutingAddress: Boolean = true
  override final def canSeeBankAccountRoutingScheme: Boolean = true
  override final def canSeeBankAccountRoutingAddress: Boolean = true
  override final def canSeeOtherAccountNationalIdentifier: Boolean = true
  override final def canSeeOtherAccountSWIFT_BIC: Boolean = true
  override final def canSeeOtherAccountIBAN: Boolean = true
  override final def canSeeOtherAccountBankName: Boolean = true
  override final def canSeeOtherAccountNumber: Boolean = true
  override final def canSeeOtherAccountMetadata: Boolean = true
  override final def canSeeOtherAccountKind: Boolean = true
  override final def canSeeOtherBankRoutingScheme: Boolean = true
  override final def canSeeOtherBankRoutingAddress: Boolean = true
  override final def canSeeOtherAccountRoutingScheme: Boolean = true
  override final def canSeeOtherAccountRoutingAddress: Boolean = true
  override final def canSeeMoreInfo: Boolean = true
  override final def canSeeUrl: Boolean = true
  override final def canSeeImageUrl: Boolean = true
  override final def canSeeOpenCorporatesUrl: Boolean = true
  override final def canSeeCorporateLocation: Boolean = true
  override final def canSeePhysicalLocation: Boolean = true
  override final def canSeePublicAlias: Boolean = true
  override final def canSeePrivateAlias: Boolean = true
  override final def canAddMoreInfo: Boolean = true
  override final def canAddURL: Boolean = true
  override final def canAddImageURL: Boolean = true
  override final def canAddOpenCorporatesUrl: Boolean = true
  override final def canAddCorporateLocation: Boolean = true
  override final def canAddPhysicalLocation: Boolean = true
  override final def canAddPublicAlias: Boolean = true
  override final def canAddPrivateAlias: Boolean = true
  override final def canAddCounterparty: Boolean = true
  override final def canDeleteCorporateLocation: Boolean = true
  override final def canDeletePhysicalLocation: Boolean = true
  override final def canEditOwnerComment: Boolean = true
  override final def canAddComment: Boolean = true
  override final def canDeleteComment: Boolean = true
  override final def canAddTag: Boolean = true
  override final def canDeleteTag: Boolean = true
  override final def canAddImage: Boolean = true
  override final def canDeleteImage: Boolean = true
  override final def canAddWhereTag: Boolean = true
  override final def canSeeWhereTag: Boolean = true
  override final def canDeleteWhereTag: Boolean = true
  override final def canInitiateTransaction: Boolean = false
  override final def canAddTransactionRequestToOwnAccount: Boolean = false
  override final def canAddTransactionRequestToAnyAccount: Boolean = false
  override final def canSeeBankAccountCreditLimit: Boolean = true
}
/**
  * each SystemPublicView should have its own bankId, AccountId and viewId,
  * and share all the 
  */
case class SystemPublicView(
  bankId : BankId,
  accountId : AccountId,
  users: List[User]
) extends View with StaticPublicViewDefinition


trait StaticOwnerViewDefinition extends ViewDefinition {
  
  final def viewId: ViewId = ViewId("owner")
  override final def name: String = "Owner"
  override final def description: String = "Owner View"
  
  
  override final def isSystem: Boolean = true
  override final def isFirehose: Boolean = true
  override final def isPublic: Boolean = false
  
  
  override final def usePublicAliasIfOneExists: Boolean = false
  override final def usePrivateAliasIfOneExists: Boolean = false
  override final def hideOtherAccountMetadataIfAlias: Boolean = false
  override final def canSeeTransactionThisBankAccount: Boolean = true
  override final def canSeeTransactionOtherBankAccount: Boolean = true
  override final def canSeeTransactionMetadata: Boolean = true
  override final def canSeeTransactionDescription: Boolean = true
  override final def canSeeTransactionAmount: Boolean = true
  override final def canSeeTransactionType: Boolean = true
  override final def canSeeTransactionCurrency: Boolean = true
  override final def canSeeTransactionStartDate: Boolean = true
  override final def canSeeTransactionFinishDate: Boolean = true
  override final def canSeeTransactionBalance: Boolean = true
  override final def canSeeComments: Boolean = true
  override final def canSeeOwnerComment: Boolean = true
  override final def canSeeTags: Boolean = true
  override final def canSeeImages: Boolean = true
  override final def canSeeBankAccountOwners: Boolean = true
  override final def canSeeBankAccountType: Boolean = true
  override final def canSeeBankAccountBalance: Boolean = true
  override final def canSeeBankAccountCurrency: Boolean = true
  override final def canSeeBankAccountLabel: Boolean = true
  override final def canSeeBankAccountNationalIdentifier: Boolean = true
  override final def canSeeBankAccountSwift_bic: Boolean = true
  override final def canSeeBankAccountIban: Boolean = true
  override final def canSeeBankAccountNumber: Boolean = true
  override final def canSeeBankAccountBankName: Boolean = true
  override final def canSeeBankRoutingScheme: Boolean = true
  override final def canSeeBankRoutingAddress: Boolean = true
  override final def canSeeBankAccountRoutingScheme: Boolean = true
  override final def canSeeBankAccountRoutingAddress: Boolean = true
  override final def canSeeOtherAccountNationalIdentifier: Boolean = true
  override final def canSeeOtherAccountSWIFT_BIC: Boolean = true
  override final def canSeeOtherAccountIBAN: Boolean = true
  override final def canSeeOtherAccountBankName: Boolean = true
  override final def canSeeOtherAccountNumber: Boolean = true
  override final def canSeeOtherAccountMetadata: Boolean = true
  override final def canSeeOtherAccountKind: Boolean = true
  override final def canSeeOtherBankRoutingScheme: Boolean = true
  override final def canSeeOtherBankRoutingAddress: Boolean = true
  override final def canSeeOtherAccountRoutingScheme: Boolean = true
  override final def canSeeOtherAccountRoutingAddress: Boolean = true
  override final def canSeeMoreInfo: Boolean = true
  override final def canSeeUrl: Boolean = true
  override final def canSeeImageUrl: Boolean = true
  override final def canSeeOpenCorporatesUrl: Boolean = true
  override final def canSeeCorporateLocation: Boolean = true
  override final def canSeePhysicalLocation: Boolean = true
  override final def canSeePublicAlias: Boolean = true
  override final def canSeePrivateAlias: Boolean = true
  override final def canAddMoreInfo: Boolean = true
  override final def canAddURL: Boolean = true
  override final def canAddImageURL: Boolean = true
  override final def canAddOpenCorporatesUrl: Boolean = true
  override final def canAddCorporateLocation: Boolean = true
  override final def canAddPhysicalLocation: Boolean = true
  override final def canAddPublicAlias: Boolean = true
  override final def canAddPrivateAlias: Boolean = true
  override final def canAddCounterparty: Boolean = true
  override final def canDeleteCorporateLocation: Boolean = true
  override final def canDeletePhysicalLocation: Boolean = true
  override final def canEditOwnerComment: Boolean = true
  override final def canAddComment: Boolean = true
  override final def canDeleteComment: Boolean = true
  override final def canAddTag: Boolean = true
  override final def canDeleteTag: Boolean = true
  override final def canAddImage: Boolean = true
  override final def canDeleteImage: Boolean = true
  override final def canAddWhereTag: Boolean = true
  override final def canSeeWhereTag: Boolean = true
  override final def canDeleteWhereTag: Boolean = true
  override final def canInitiateTransaction: Boolean = true
  override final def canAddTransactionRequestToOwnAccount: Boolean = true
  override final def canAddTransactionRequestToAnyAccount: Boolean = true
  override final def canSeeBankAccountCreditLimit: Boolean = true
}
case class SystemOwnerView(
  bankId : BankId,
  accountId : AccountId,
  users: List[User]
) extends View with StaticOwnerViewDefinition


trait StaticAccountantViewDefinition extends ViewDefinition {
  
  final def viewId: ViewId = ViewId("accountant")
  override final def name: String = "Accountant"
  override final def description: String = "Accountant View"
  
  override final def isSystem: Boolean = true
  override final def isFirehose: Boolean = true
  override final def isPublic: Boolean = false
  
  
  override final def usePublicAliasIfOneExists: Boolean = true
  override final def usePrivateAliasIfOneExists: Boolean = false
  override final def hideOtherAccountMetadataIfAlias: Boolean = true
  override final def canSeeTransactionThisBankAccount: Boolean = true
  override final def canSeeTransactionOtherBankAccount: Boolean = true
  override final def canSeeTransactionMetadata: Boolean = true
  override final def canSeeTransactionDescription: Boolean = false
  override final def canSeeTransactionAmount: Boolean = true
  override final def canSeeTransactionType: Boolean = true
  override final def canSeeTransactionCurrency: Boolean = true
  override final def canSeeTransactionStartDate: Boolean = true
  override final def canSeeTransactionFinishDate: Boolean = true
  override final def canSeeTransactionBalance: Boolean = true
  override final def canSeeComments: Boolean = true
  override final def canSeeOwnerComment: Boolean = true
  override final def canSeeTags: Boolean = true
  override final def canSeeImages: Boolean = true
  override final def canSeeBankAccountOwners: Boolean = true
  override final def canSeeBankAccountType: Boolean = true
  override final def canSeeBankAccountBalance: Boolean = true
  override final def canSeeBankAccountCurrency: Boolean = true
  override final def canSeeBankAccountLabel: Boolean = true
  override final def canSeeBankAccountNationalIdentifier: Boolean = true
  override final def canSeeBankAccountSwift_bic: Boolean = true
  override final def canSeeBankAccountIban: Boolean = true
  override final def canSeeBankAccountNumber: Boolean = true
  override final def canSeeBankAccountBankName: Boolean = true
  override final def canSeeBankRoutingScheme: Boolean = true
  override final def canSeeBankRoutingAddress: Boolean = true
  override final def canSeeBankAccountRoutingScheme: Boolean = true
  override final def canSeeBankAccountRoutingAddress: Boolean = true
  override final def canSeeOtherAccountNationalIdentifier: Boolean = true
  override final def canSeeOtherAccountSWIFT_BIC: Boolean = true
  override final def canSeeOtherAccountIBAN: Boolean = true
  override final def canSeeOtherAccountBankName: Boolean = true
  override final def canSeeOtherAccountNumber: Boolean = true
  override final def canSeeOtherAccountMetadata: Boolean = true
  override final def canSeeOtherAccountKind: Boolean = true
  override final def canSeeOtherBankRoutingScheme: Boolean = true
  override final def canSeeOtherBankRoutingAddress: Boolean = true
  override final def canSeeOtherAccountRoutingScheme: Boolean = true
  override final def canSeeOtherAccountRoutingAddress: Boolean = true
  override final def canSeeMoreInfo: Boolean = true
  override final def canSeeUrl: Boolean = true
  override final def canSeeImageUrl: Boolean = true
  override final def canSeeOpenCorporatesUrl: Boolean = true
  override final def canSeeCorporateLocation: Boolean = true
  override final def canSeePhysicalLocation: Boolean = true
  override final def canSeePublicAlias: Boolean = true
  override final def canSeePrivateAlias: Boolean = true
  override final def canAddMoreInfo: Boolean = true
  override final def canAddURL: Boolean = true
  override final def canAddImageURL: Boolean = true
  override final def canAddOpenCorporatesUrl: Boolean = true
  override final def canAddCorporateLocation: Boolean = true
  override final def canAddPhysicalLocation: Boolean = true
  override final def canAddPublicAlias: Boolean = true
  override final def canAddPrivateAlias: Boolean = true
  override final def canAddCounterparty: Boolean = true
  override final def canDeleteCorporateLocation: Boolean = true
  override final def canDeletePhysicalLocation: Boolean = true
  override final def canEditOwnerComment: Boolean = true
  override final def canAddComment: Boolean = true
  override final def canDeleteComment: Boolean = true
  override final def canAddTag: Boolean = true
  override final def canDeleteTag: Boolean = true
  override final def canAddImage: Boolean = true
  override final def canDeleteImage: Boolean = true
  override final def canAddWhereTag: Boolean = true
  override final def canSeeWhereTag: Boolean = true
  override final def canDeleteWhereTag: Boolean = true
  override final def canInitiateTransaction: Boolean = true
  override final def canAddTransactionRequestToOwnAccount: Boolean = true
  override final def canAddTransactionRequestToAnyAccount: Boolean = false
  override final def canSeeBankAccountCreditLimit: Boolean = true
}
case class SystemAccountantView(
  bankId : BankId,
  accountId : AccountId,
  users: List[User]
) extends View with StaticAccountantViewDefinition



trait StaticAuditorViewDefinition extends ViewDefinition {
  
  final def viewId: ViewId = ViewId("auditor")
  override final def name: String = "Auditor"
  override final def description: String = "Auditor View"
  
  override final def isSystem: Boolean = true
  override final def isFirehose: Boolean = true
  override final def isPublic: Boolean = false
  
  
  override final def usePublicAliasIfOneExists: Boolean = true
  override final def usePrivateAliasIfOneExists: Boolean = false
  override final def hideOtherAccountMetadataIfAlias: Boolean = true
  override final def canSeeTransactionThisBankAccount: Boolean = true
  override final def canSeeTransactionOtherBankAccount: Boolean = true
  override final def canSeeTransactionMetadata: Boolean = true
  override final def canSeeTransactionDescription: Boolean = false
  override final def canSeeTransactionAmount: Boolean = true
  override final def canSeeTransactionType: Boolean = true
  override final def canSeeTransactionCurrency: Boolean = true
  override final def canSeeTransactionStartDate: Boolean = true
  override final def canSeeTransactionFinishDate: Boolean = true
  override final def canSeeTransactionBalance: Boolean = true
  override final def canSeeComments: Boolean = true
  override final def canSeeOwnerComment: Boolean = true
  override final def canSeeTags: Boolean = true
  override final def canSeeImages: Boolean = true
  override final def canSeeBankAccountOwners: Boolean = true
  override final def canSeeBankAccountType: Boolean = true
  override final def canSeeBankAccountBalance: Boolean = true
  override final def canSeeBankAccountCurrency: Boolean = true
  override final def canSeeBankAccountLabel: Boolean = true
  override final def canSeeBankAccountNationalIdentifier: Boolean = true
  override final def canSeeBankAccountSwift_bic: Boolean = true
  override final def canSeeBankAccountIban: Boolean = true
  override final def canSeeBankAccountNumber: Boolean = true
  override final def canSeeBankAccountBankName: Boolean = true
  override final def canSeeBankRoutingScheme: Boolean = true
  override final def canSeeBankRoutingAddress: Boolean = true
  override final def canSeeBankAccountRoutingScheme: Boolean = true
  override final def canSeeBankAccountRoutingAddress: Boolean = true
  override final def canSeeOtherAccountNationalIdentifier: Boolean = true
  override final def canSeeOtherAccountSWIFT_BIC: Boolean = true
  override final def canSeeOtherAccountIBAN: Boolean = true
  override final def canSeeOtherAccountBankName: Boolean = true
  override final def canSeeOtherAccountNumber: Boolean = true
  override final def canSeeOtherAccountMetadata: Boolean = true
  override final def canSeeOtherAccountKind: Boolean = true
  override final def canSeeOtherBankRoutingScheme: Boolean = true
  override final def canSeeOtherBankRoutingAddress: Boolean = true
  override final def canSeeOtherAccountRoutingScheme: Boolean = true
  override final def canSeeOtherAccountRoutingAddress: Boolean = true
  override final def canSeeMoreInfo: Boolean = true
  override final def canSeeUrl: Boolean = true
  override final def canSeeImageUrl: Boolean = true
  override final def canSeeOpenCorporatesUrl: Boolean = true
  override final def canSeeCorporateLocation: Boolean = true
  override final def canSeePhysicalLocation: Boolean = true
  override final def canSeePublicAlias: Boolean = true
  override final def canSeePrivateAlias: Boolean = true
  override final def canAddMoreInfo: Boolean = true
  override final def canAddURL: Boolean = true
  override final def canAddImageURL: Boolean = true
  override final def canAddOpenCorporatesUrl: Boolean = true
  override final def canAddCorporateLocation: Boolean = true
  override final def canAddPhysicalLocation: Boolean = true
  override final def canAddPublicAlias: Boolean = true
  override final def canAddPrivateAlias: Boolean = true
  override final def canAddCounterparty: Boolean = true
  override final def canDeleteCorporateLocation: Boolean = true
  override final def canDeletePhysicalLocation: Boolean = true
  override final def canEditOwnerComment: Boolean = true
  override final def canAddComment: Boolean = true
  override final def canDeleteComment: Boolean = true
  override final def canAddTag: Boolean = true
  override final def canDeleteTag: Boolean = true
  override final def canAddImage: Boolean = true
  override final def canDeleteImage: Boolean = true
  override final def canAddWhereTag: Boolean = true
  override final def canSeeWhereTag: Boolean = true
  override final def canDeleteWhereTag: Boolean = true
  override final def canInitiateTransaction: Boolean = true
  override final def canAddTransactionRequestToOwnAccount: Boolean = false
  override final def canAddTransactionRequestToAnyAccount: Boolean = false
  override final def canSeeBankAccountCreditLimit: Boolean = true
}
case class SystemAuditorView(
  bankId : BankId,
  accountId : AccountId,
  users: List[User]
) extends View with StaticAuditorViewDefinition



trait StaticFirehoseViewDefinition extends ViewDefinition {
  
  final def viewId: ViewId = ViewId("firehose")
  override final def name: String = "Firehose"
  override final def description: String = "Firehose View"
  
  override final def isSystem: Boolean = true
  override final def isFirehose: Boolean = true
  override final def isPublic: Boolean = false
  
  
  override final def usePublicAliasIfOneExists: Boolean = false
  override final def usePrivateAliasIfOneExists: Boolean = false
  override final def hideOtherAccountMetadataIfAlias: Boolean = false
  override final def canSeeTransactionThisBankAccount: Boolean = true
  override final def canSeeTransactionOtherBankAccount: Boolean = true
  override final def canSeeTransactionMetadata: Boolean = true
  override final def canSeeTransactionDescription: Boolean = true
  override final def canSeeTransactionAmount: Boolean = true
  override final def canSeeTransactionType: Boolean = true
  override final def canSeeTransactionCurrency: Boolean = true
  override final def canSeeTransactionStartDate: Boolean = true
  override final def canSeeTransactionFinishDate: Boolean = true
  override final def canSeeTransactionBalance: Boolean = true
  override final def canSeeComments: Boolean = true
  override final def canSeeOwnerComment: Boolean = true
  override final def canSeeTags: Boolean = true
  override final def canSeeImages: Boolean = true
  override final def canSeeBankAccountOwners: Boolean = true
  override final def canSeeBankAccountType: Boolean = true
  override final def canSeeBankAccountBalance: Boolean = true
  override final def canSeeBankAccountCurrency: Boolean = true
  override final def canSeeBankAccountLabel: Boolean = true
  override final def canSeeBankAccountNationalIdentifier: Boolean = true
  override final def canSeeBankAccountSwift_bic: Boolean = true
  override final def canSeeBankAccountIban: Boolean = true
  override final def canSeeBankAccountNumber: Boolean = true
  override final def canSeeBankAccountBankName: Boolean = true
  override final def canSeeBankRoutingScheme: Boolean = true
  override final def canSeeBankRoutingAddress: Boolean = true
  override final def canSeeBankAccountRoutingScheme: Boolean = true
  override final def canSeeBankAccountRoutingAddress: Boolean = true
  override final def canSeeOtherAccountNationalIdentifier: Boolean = true
  override final def canSeeOtherAccountSWIFT_BIC: Boolean = true
  override final def canSeeOtherAccountIBAN: Boolean = true
  override final def canSeeOtherAccountBankName: Boolean = true
  override final def canSeeOtherAccountNumber: Boolean = true
  override final def canSeeOtherAccountMetadata: Boolean = true
  override final def canSeeOtherAccountKind: Boolean = true
  override final def canSeeOtherBankRoutingScheme: Boolean = true
  override final def canSeeOtherBankRoutingAddress: Boolean = true
  override final def canSeeOtherAccountRoutingScheme: Boolean = true
  override final def canSeeOtherAccountRoutingAddress: Boolean = true
  override final def canSeeMoreInfo: Boolean = true
  override final def canSeeUrl: Boolean = true
  override final def canSeeImageUrl: Boolean = true
  override final def canSeeOpenCorporatesUrl: Boolean = true
  override final def canSeeCorporateLocation: Boolean = true
  override final def canSeePhysicalLocation: Boolean = true
  override final def canSeePublicAlias: Boolean = true
  override final def canSeePrivateAlias: Boolean = true
  override final def canAddMoreInfo: Boolean = true
  override final def canAddURL: Boolean = true
  override final def canAddImageURL: Boolean = true
  override final def canAddOpenCorporatesUrl: Boolean = true
  override final def canAddCorporateLocation: Boolean = true
  override final def canAddPhysicalLocation: Boolean = true
  override final def canAddPublicAlias: Boolean = true
  override final def canAddPrivateAlias: Boolean = true
  override final def canAddCounterparty: Boolean = true
  override final def canDeleteCorporateLocation: Boolean = true
  override final def canDeletePhysicalLocation: Boolean = true
  override final def canEditOwnerComment: Boolean = true
  override final def canAddComment: Boolean = true
  override final def canDeleteComment: Boolean = true
  override final def canAddTag: Boolean = true
  override final def canDeleteTag: Boolean = true
  override final def canAddImage: Boolean = true
  override final def canDeleteImage: Boolean = true
  override final def canAddWhereTag: Boolean = true
  override final def canSeeWhereTag: Boolean = true
  override final def canDeleteWhereTag: Boolean = true
  override final def canInitiateTransaction: Boolean = true
  override final def canAddTransactionRequestToOwnAccount: Boolean = true
  override final def canAddTransactionRequestToAnyAccount: Boolean = true
  override final def canSeeBankAccountCreditLimit: Boolean = true
}
case class SystemFirehoseView(
  bankId : BankId,
  accountId : AccountId,
  users: List[User]
) extends View with StaticFirehoseViewDefinition




