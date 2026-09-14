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

package code.nonce

import java.util.Date

import code.api.util.APIUtil
import code.model.{MappedNonceProvider, Nonce}
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future


object Nonces extends SimpleInjector {

  val nonces = new Inject(() => buildOne) {}

  def buildOne: NoncesProvider = MappedNonceProvider
  
}

trait NoncesProvider {
   def createNonce(id: Option[Long],
                   consumerKey: Option[String],
                   tokenKey: Option[String],
                   timestamp: Option[Date],
                   value: Option[String]): Box[Nonce]
  def deleteExpiredNonces(currentDate: Date): Boolean
  def countNonces(consumerKey: String,
                  tokenKey: String,
                  timestamp: Date,
                  value: String): Long
  def countNoncesFuture(consumerKey: String,
                        tokenKey: String,
                        timestamp: Date,
                        value: String): Future[Long]
}