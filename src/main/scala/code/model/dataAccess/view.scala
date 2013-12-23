/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
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

import net.liftweb.mapper._
import code.model.{View, BankAccount, User}

class ViewPrivileges extends LongKeyedMapper[ViewPrivileges] with IdPK with CreatedUpdated {
  def getSingleton = ViewPrivileges
  object user extends MappedLongForeignKey(this, OBPUser)
  object view extends MappedLongForeignKey(this, ViewImpl)
}
object ViewPrivileges extends ViewPrivileges with LongKeyedMetaMapper[ViewPrivileges]

class ViewImpl extends View with LongKeyedMapper[ViewImpl] with ManyToMany with CreatedUpdated{
  def getSingleton = ViewImpl

  def primaryKeyField = id_
  object users_ extends MappedManyToMany(ViewPrivileges, ViewPrivileges.view, ViewPrivileges.user, OBPUser)
  object account extends MappedLongForeignKey(this, HostedAccount)


  object id_ extends MappedLongIndex(this)
  object name_ extends MappedString(this, 255)
  object description_ extends MappedString(this, 255)
  object permalink_ extends MappedString(this, 255)

  def users : List[User] =  users_.toList
  object isPublic_ extends MappedBoolean(this){
    override def defaultValue = false
    override def dbIndexed_? = true
  }
  object usePrivateAliasIfOneExists_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object usePublicAliasIfOneExists_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object hideOtherAccountMetadataIfAlias_ extends MappedBoolean(this){
    override def defaultValue = false
  }

  object canSeeTransactionThisBankAccount_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionOtherBankAccount_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionMetadata_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionDescription_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionAmount_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionType_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionCurrency_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionStartDate_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionFinishDate_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTransactionBalance_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeComments_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOwnerComment_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeTags_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeImages_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountOwners_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountType_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountBalance_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountCurrency_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountLabel_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountNationalIdentifier_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountSwift_bic_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountIban_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountNumber_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountBankName_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeBankAccountBankPermalink_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountNationalIdentifier_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountSWIFT_BIC_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountIBAN_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountBankName_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountNumber_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountMetadata_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOtherAccountKind_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeMoreInfo_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeUrl_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeImageUrl_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeOpenCorporatesUrl_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeCorporateLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeePhysicalLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeePublicAlias_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeePrivateAlias_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddMoreInfo_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddURL_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddImageURL_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddOpenCorporatesUrl_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddCorporateLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddPhysicalLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddPublicAlias_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddPrivateAlias_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteCorporateLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeletePhysicalLocation_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canEditOwnerComment_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddComment_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteComment_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddTag_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteTag_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddImage_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteImage_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canAddWhereTag_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canSeeWhereTag_ extends MappedBoolean(this){
    override def defaultValue = false
  }
  object canDeleteWhereTag_ extends MappedBoolean(this){
    override def defaultValue = false
  }

  //e.g. "Public", "Authorities", "Our Network", etc.
  def id: Long = id_.get
  def name: String = name_.get
  def description : String = description_.get
  def permalink : String = permalink_.get
  def isPublic : Boolean = isPublic_.get

  //the view settings
  def usePrivateAliasIfOneExists: Boolean = usePrivateAliasIfOneExists_.get
  def usePublicAliasIfOneExists: Boolean = usePublicAliasIfOneExists_.get
  def hideOtherAccountMetadataIfAlias: Boolean = hideOtherAccountMetadataIfAlias_.get

  //reading access

  //transaction fields
  def canSeeTransactionThisBankAccount : Boolean = canSeeTransactionThisBankAccount_.get
  def canSeeTransactionOtherBankAccount : Boolean = canSeeTransactionOtherBankAccount_.get
  def canSeeTransactionMetadata : Boolean = canSeeTransactionMetadata_.get
  def canSeeTransactionDescription: Boolean = canSeeTransactionDescription_.get
  def canSeeTransactionAmount: Boolean = canSeeTransactionAmount_.get
  def canSeeTransactionType: Boolean = canSeeTransactionType_.get
  def canSeeTransactionCurrency: Boolean = canSeeTransactionCurrency_.get
  def canSeeTransactionStartDate: Boolean = canSeeTransactionStartDate_.get
  def canSeeTransactionFinishDate: Boolean = canSeeTransactionFinishDate_.get
  def canSeeTransactionBalance: Boolean = canSeeTransactionBalance_.get

  //transaction metadata
  def canSeeComments: Boolean = canSeeComments_.get
  def canSeeOwnerComment: Boolean = canSeeOwnerComment_.get
  def canSeeTags : Boolean = canSeeTags_.get
  def canSeeImages : Boolean = canSeeImages_.get

  //Bank account fields
  def canSeeBankAccountOwners : Boolean = canSeeBankAccountOwners_.get
  def canSeeBankAccountType : Boolean = canSeeBankAccountType_.get
  def canSeeBankAccountBalance : Boolean = canSeeBankAccountBalance_.get
  def canSeeBankAccountCurrency : Boolean = canSeeBankAccountCurrency_.get
  def canSeeBankAccountLabel : Boolean = canSeeBankAccountLabel_.get
  def canSeeBankAccountNationalIdentifier : Boolean = canSeeBankAccountNationalIdentifier_.get
  def canSeeBankAccountSwift_bic : Boolean = canSeeBankAccountSwift_bic_.get
  def canSeeBankAccountIban : Boolean = canSeeBankAccountIban_.get
  def canSeeBankAccountNumber : Boolean = canSeeBankAccountNumber_.get
  def canSeeBankAccountBankName : Boolean = canSeeBankAccountBankName_.get
  def canSeeBankAccountBankPermalink : Boolean = canSeeBankAccountBankPermalink_.get

  //other bank account fields
  def canSeeOtherAccountNationalIdentifier : Boolean = canSeeOtherAccountNationalIdentifier_.get
  def canSeeOtherAccountSWIFT_BIC : Boolean = canSeeOtherAccountSWIFT_BIC_.get
  def canSeeOtherAccountIBAN : Boolean = canSeeOtherAccountIBAN_.get
  def canSeeOtherAccountBankName : Boolean = canSeeOtherAccountBankName_.get
  def canSeeOtherAccountNumber : Boolean = canSeeOtherAccountNumber_.get
  def canSeeOtherAccountMetadata : Boolean = canSeeOtherAccountMetadata_.get
  def canSeeOtherAccountKind : Boolean = canSeeOtherAccountKind_.get

  //other bank account meta data
  def canSeeMoreInfo: Boolean = canSeeMoreInfo_.get
  def canSeeUrl: Boolean = canSeeUrl_.get
  def canSeeImageUrl: Boolean = canSeeImageUrl_.get
  def canSeeOpenCorporatesUrl: Boolean = canSeeOpenCorporatesUrl_.get
  def canSeeCorporateLocation : Boolean = canSeeCorporateLocation_.get
  def canSeePhysicalLocation : Boolean = canSeePhysicalLocation_.get
  def canSeePublicAlias : Boolean = canSeePublicAlias_.get
  def canSeePrivateAlias : Boolean = canSeePrivateAlias_.get
  def canAddMoreInfo : Boolean = canAddMoreInfo_.get
  def canAddURL : Boolean = canAddURL_.get
  def canAddImageURL : Boolean = canAddImageURL_.get
  def canAddOpenCorporatesUrl : Boolean = canAddOpenCorporatesUrl_.get
  def canAddCorporateLocation : Boolean = canAddCorporateLocation_.get
  def canAddPhysicalLocation : Boolean = canAddPhysicalLocation_.get
  def canAddPublicAlias : Boolean = canAddPublicAlias_.get
  def canAddPrivateAlias : Boolean = canAddPrivateAlias_.get
  def canDeleteCorporateLocation : Boolean = canDeleteCorporateLocation_.get
  def canDeletePhysicalLocation : Boolean = canDeletePhysicalLocation_.get

  //writing access
  def canEditOwnerComment: Boolean = canEditOwnerComment_.get
  def canAddComment : Boolean = canAddComment_.get
  def canDeleteComment: Boolean = canDeleteComment_.get
  def canAddTag : Boolean = canAddTag_.get
  def canDeleteTag : Boolean = canDeleteTag_.get
  def canAddImage : Boolean = canAddImage_.get
  def canDeleteImage : Boolean = canDeleteImage_.get
  def canAddWhereTag : Boolean = canAddWhereTag_.get
  def canSeeWhereTag : Boolean = canSeeWhereTag_.get
  def canDeleteWhereTag : Boolean = canDeleteWhereTag_.get
}

object ViewImpl extends ViewImpl with LongKeyedMetaMapper[ViewImpl]{
  override def dbIndexes = Index(permalink_, account):: super.dbIndexes
}