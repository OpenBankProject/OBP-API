/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

package code.platformapp

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

/**
 * A Platform App is a Consumer an installation runs as part of its own deployment (the Portal, the
 * API Manager, Opey, a bank's own services), calling OBP with its own application token. An
 * administrator marks the Consumer; the app then declares, as itself, the Scopes it needs and what for.
 * The declaration is refused for a Consumer nobody has marked, so an arbitrary Consumer cannot put
 * itself on the list of apps an administrator is asked to grant Scopes to.
 */
object PlatformApps extends SimpleInjector {
  val platformAppProvider = new Inject(() => buildOne) {}
  def buildOne: PlatformAppProvider = PlatformAppDbProvider
}

trait PlatformAppTrait {
  /** The Consumer's consumer_id (its public id, not its key). */
  def consumerId: String
  def label: String
  def markedByUserId: String
  def markedAt: Date
  /** When the app last declared its required Scopes; None until it has. */
  def declaredAt: Option[Date]
  /** The version the app reported with its declaration, if any. */
  def declaredVersion: Option[String]
}

trait PlatformAppRequiredScopeTrait {
  def consumerId: String
  def roleName: String
  /** A bank id, SYS for the system space, or "" for a system Role. */
  def bankId: String
  /** The features that depend on the Scope, as the administrator would recognise them. */
  def neededFor: String
  def isOptional: Boolean
}

/** One Scope in an app's declaration. */
case class PlatformAppRequiredScopeInput(roleName: String, bankId: String, neededFor: String, isOptional: Boolean)

trait PlatformAppProvider {
  def createPlatformApp(consumerId: String, label: String, markedByUserId: String): Box[PlatformAppTrait]
  def getPlatformApp(consumerId: String): Box[PlatformAppTrait]
  def getPlatformApps(): Box[List[PlatformAppTrait]]
  /** Unmarks the Consumer and forgets its declaration. */
  def deletePlatformApp(consumerId: String): Box[Boolean]
  /** Replaces the app's declared Scopes with these. */
  def declareRequiredScopes(consumerId: String, version: Option[String], scopes: List[PlatformAppRequiredScopeInput]): Box[PlatformAppTrait]
  def getRequiredScopes(consumerId: String): Box[List[PlatformAppRequiredScopeTrait]]
}
