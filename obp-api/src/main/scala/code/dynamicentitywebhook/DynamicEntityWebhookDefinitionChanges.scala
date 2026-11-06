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

package code.dynamicentitywebhook

import code.dynamicEntity.DynamicEntityT
import code.util.Helper.MdcLoggable

/**
 * This object keeps a Dynamic Entity's webhooks in step with its definition. The definition provider
 * calls it after it saves or deletes a definition, in the same transaction, so every way of changing a
 * definition is covered, whichever endpoint or version made the change.
 *
 * A change to the definition always goes ahead. When it removes a field that an active webhook's field
 * conditions name, or changes such a field's type, the webhook is turned off with `deactivated_reason`
 * ENTITY_FIELD_REMOVED or ENTITY_FIELD_TYPE_CHANGED: its conditions no longer mean what its owner wrote,
 * so it stops firing until the owner fixes them and turns it back on. A webhook that is already off keeps
 * the reason it has. A renamed entity's name is updated on its webhooks, which find records by it, and a
 * deleted entity's webhooks are deleted with their undelivered requests.
 */
object DynamicEntityWebhookDefinitionChanges extends MdcLoggable {

  private def provider = DynamicEntityWebhooks.dynamicEntityWebhookProvider.vend

  /**
   * Called after an existing definition is saved. `fieldTypesBefore` are its fields and their types as
   * they were before the save (see DynamicEntityWebhookConditions.fieldTypes).
   */
  def afterUpdate(saved: DynamicEntityT, fieldTypesBefore: Map[String, String]): Unit =
    saved.dynamicEntityId.filter(_.nonEmpty).foreach { dynamicEntityId =>
      provider.renameEntity(dynamicEntityId, saved.entityName).openOrThrowException("rename the entity on its webhooks")
      provider.getByDynamicEntityId(dynamicEntityId).openOrThrowException("the entity's webhooks")
        .filter(webhook => webhook.isActive && webhook.fieldConditions.nonEmpty)
        .foreach { webhook =>
          DynamicEntityWebhookConditions.deactivationReasonFor(webhook.fieldConditions, saved, fieldTypesBefore).foreach { reason =>
            provider.deactivate(webhook.webhookId, reason).openOrThrowException("deactivate the webhook")
            logger.info(s"afterUpdate says: webhook ${webhook.webhookId} of ${saved.entityName} turned off, $reason")
          }
        }
    }

  /** Called before a definition is deleted. */
  def beforeDelete(dynamicEntityId: String): Unit = {
    val deleted = provider.deleteByDynamicEntityId(dynamicEntityId).openOrThrowException("delete the entity's webhooks")
    if (deleted > 0) logger.info(s"beforeDelete says: deleted $deleted webhook(s) of Dynamic Entity $dynamicEntityId")
  }
}
