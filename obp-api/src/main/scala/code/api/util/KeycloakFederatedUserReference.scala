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

package code.api.util

import java.util.UUID
import scala.util.Try

final case class KeycloakFederatedUserReference(
                                                 prefix: Char,
                                                 storageProviderId: UUID,   // Keycloak component UUID
                                                 externalId: UUID           // unique user id in external DB
                                               )

object KeycloakFederatedUserReference {
  // Pattern: f:<storageProviderId>:<externalId>
  private val Pattern =
    "^([A-Za-z]):([0-9a-fA-F-]{8}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{12}):([0-9a-fA-F-]{8}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{12})$".r

  /** Safe parser */
  def parse(s: String): Either[String, KeycloakFederatedUserReference] =
    s match {
      case Pattern(p, providerIdStr, externalIdStr) if p == "f" =>
        for {
          providerId <- Try(UUID.fromString(providerIdStr))
            .toEither.left.map(_ => s"Invalid storageProviderId: $providerIdStr")
          externalId <- Try(UUID.fromString(externalIdStr))
            .toEither.left.map(_ => s"Invalid externalId: $externalIdStr")
        } yield KeycloakFederatedUserReference('f', providerId, externalId)

      case Pattern(p, _, _) =>
        Left(s"Invalid prefix: '$p'. Expected 'f'.")

      case _ =>
        Left("Invalid format. Expected: f:<storageProviderId>:<externalId>")
    }

  def unsafe(s: String): KeycloakFederatedUserReference =
    parse(s).fold(err => throw new IllegalArgumentException(err), identity)
}
