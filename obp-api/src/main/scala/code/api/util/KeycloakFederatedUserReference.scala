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
