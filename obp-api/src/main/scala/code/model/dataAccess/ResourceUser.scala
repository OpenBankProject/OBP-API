/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.model.dataAccess

import java.util.Date
import java.util.UUID.randomUUID

import code.api.Constant
import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieQueries}
import code.util.MappedUUID
import com.openbankproject.commons.model.{User, UserPrimaryKey}
import com.tesobe.CacheKeyFromArguments
import net.liftweb.mapper._

import scala.concurrent.duration._

/**
 * An O-R mapped "User" class that includes first name, last name, password
  *
  * 1 AuthUser : is used for authentication, only for webpage Login in stuff
  *   1) It is MegaProtoUser, has lots of methods for validation username, password, email ....
  *      Such as lost password, reset password ..... 
  *      Lift have some helper methods to make these things easily. 
  *   
  *  
  * 
  * 2 ResourceUser: is only a normal LongKeyedMapper 
  *   1) All the accounts, transactions ,roles, views, accountHolders, customers... should be linked to ResourceUser.userId_ field.
  *   2) The consumer keys, tokens are also belong ResourceUser
  *  
  * 
  * 3 RelationShips:
  *   1)When `Sign up` new user --> create AuthUser --> call AuthUser.save --> create ResourceUser user.
  *      They share the same username and email.
  *   2)AuthUser `user` field as the Foreign Key to link to Resource User. 
  *      one AuthUser <---> one ResourceUser 
  *
 */
class ResourceUser extends LongKeyedMapper[ResourceUser] with User with ManyToMany with OneToMany[Long, ResourceUser]{
  def getSingleton = ResourceUser
  def primaryKeyField = id

  object id extends MappedLongIndex(this)
  
  //this is the user_id!
  object userId_ extends MappedUUID(this)
  object email extends MappedEmail(this, 100){
    override def required_? = false
  }
  object name_ extends MappedString(this, 100){
    override def defaultValue = ""
  }
  object provider_ extends MappedString(this, 100){
    override def defaultValue: String = Constant.localIdentityProvider
  }

  /**
  * the id of the user at that provider --> now, this field will be the same as `providerId`
  */
  object providerId extends MappedString(this, 100){
    override def defaultValue = name_.get
  }
  object Company extends MappedString(this, 50)
  object CreatedByConsentId extends MappedString(this, 100)
  object CreatedByUserInvitationId extends MappedString(this, 100)
  object IsDeleted extends MappedBoolean(this) {
    override def defaultValue = false
  }
  object LastMarketingAgreementSignedDate extends MappedDate(this)
  object LastUsedLocale extends MappedString(this, 10) {
    override def defaultValue = null
  }
  object IsNaturalPerson extends MappedBoolean(this) {
    override def defaultValue = true
  }
  object PrincipalUserId extends MappedString(this, 100) {
    override def defaultValue = null
  }
  // Deliberately NOT unique — several users may share a number
  object MobilePhoneNumber extends MappedString(this, 50) {
    override def defaultValue = null
  }
  object MobilePhoneNumberIsValidated extends MappedBoolean(this) {
    override def defaultValue = false
  }
  object MobilePhoneNumberValidatedDate extends MappedDateTime(this)

  def emailAddress = {
    val e = email.get
    if(e != null) e else ""
  }

  def idGivenByProvider = providerId.get
  def userPrimaryKey = UserPrimaryKey(id.get)

  def userId = userId_.get

  def name : String = name_.get
  def provider = provider_.get
  def company: String = Company.get

  def toCaseClass: ResourceUserCaseClass =
    ResourceUserCaseClass(
      emailAddress = emailAddress,
      idGivenByProvider = idGivenByProvider,
      resourceUserId = userPrimaryKey.value,
      userId = userId,
      name = name,
      provider = provider
    )
  
  override def createdByConsentId = if(CreatedByConsentId.get == null) None else if (CreatedByConsentId.get.isEmpty) None else Some(CreatedByConsentId.get) //null --> None
  override def createdByUserInvitationId = if(CreatedByUserInvitationId.get == null) None else if (CreatedByUserInvitationId.get.isEmpty) None else Some(CreatedByUserInvitationId.get) //null --> None
  override def isDeleted: Option[Boolean] = if(IsDeleted.jdbcFriendly(IsDeleted.calcFieldName) == null) None else Some(IsDeleted.get) // null --> None
  override def lastMarketingAgreementSignedDate: Option[Date] = if(IsDeleted.jdbcFriendly(LastMarketingAgreementSignedDate.calcFieldName) == null) None else Some(LastMarketingAgreementSignedDate.get) // null --> None
  override def lastUsedLocale: Option[String] = if(LastUsedLocale.get == null) None else Some(LastUsedLocale.get) // null --> None
  override def isNaturalPerson: Boolean = IsNaturalPerson.get
  override def principalUserIdOption: Option[String] = if(PrincipalUserId.get == null) None else if (PrincipalUserId.get.isEmpty) None else Some(PrincipalUserId.get)
  override def mobilePhoneNumber: Option[String] = if(MobilePhoneNumber.get == null) None else if (MobilePhoneNumber.get.isEmpty) None else Some(MobilePhoneNumber.get)
  override def mobilePhoneNumberIsValidated: Option[Boolean] = if(MobilePhoneNumberIsValidated.jdbcFriendly(MobilePhoneNumberIsValidated.calcFieldName) == null) None else Some(MobilePhoneNumberIsValidated.get) // null --> None
  override def mobilePhoneNumberValidatedDate: Option[Date] = if(MobilePhoneNumberValidatedDate.get == null) None else Some(MobilePhoneNumberValidatedDate.get)
}

object ResourceUser extends ResourceUser with LongKeyedMetaMapper[ResourceUser]{
    // userId_ is deliberately NOT declared here: MigrationOfUserIdIndexes creates a stronger
    // UNIQUE index on it (resourceuser_userid_unique). CreatedByConsentId is the delegation
    // registry — consent-agent fan-down (/my/metrics, /my/banks) and accountableUserId join
    // through it; nothing else indexes it, which matters on consent-heavy instances where every
    // consent mints a user row.
    override def dbIndexes = UniqueIndex(provider_, providerId) :: Index(CreatedByConsentId) :: super.dbIndexes
  
  def getDistinctProviders: List[String] = {
    /**
     * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
     * is just a temporary value field with UUID values in order to prevent any ambiguity.
     * The real value will be assigned by Macro during compile time at this line of a code:
     * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
     */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    val cacheTTL = APIUtil.getPropsAsIntValue("getDistinctProviders.cache.ttl.seconds", 3600)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cacheTTL.seconds) {
        // Use Doobie for type-safe query with proper JDBC type handling (including SQL Server NVARCHAR)
        DoobieQueries.getDistinctProviders
      }
    }
  }
}

case class ResourceUserCaseClass(
                                  emailAddress: String,
                                  idGivenByProvider: String,
                                  resourceUserId: Long,
                                  userId: String,
                                  name: String,
                                  provider: String
                                )