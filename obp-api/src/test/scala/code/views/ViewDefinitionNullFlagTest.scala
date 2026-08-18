package code.views

import code.api.util.DoobieUtil
import code.setup.ServerSetup
import code.views.system.ViewDefinition
import doobie.implicits._
import net.liftweb.common.Full
import net.liftweb.util.Helpers

/**
 * A NULL permission flag has to read back as false, the way Mapper read it.
 *
 * MappedBoolean looks like it falls back to the field's defaultValue, and the first version of
 * this store's read path assumed so - it read ISFIREHOSE_ as `getOrElse(true)` because the entity
 * declared `override def defaultValue = true`. That is not what the getter did. defaultValue only
 * seeds `data` for a NEW in-memory instance (MappedBoolean.scala:31); a NULL column sets
 * `data = Empty` on read (:141) and the getter is `i_is_! = data openOr false` (:85). So Lift read
 * a NULL flag as false whatever the declared default, and isFirehose_ is the one flag where the
 * two disagree.
 *
 * It matters because isFirehose is a permission bound: APIUtil's firehose path grants a
 * CanUseAccountFirehose holder access to a system view only `if (view.isFirehose)`. Reading a NULL
 * as true would turn every such row into a firehose-reachable view. The application never writes
 * NULL there, so this is about staying faithful rather than closing a live hole - but the flag is
 * the wrong place to guess.
 */
class ViewDefinitionNullFlagTest extends ServerSetup {

  feature("a view row whose boolean flags are NULL in the database") {

    scenario("reads every flag as false, isFirehose included") {
      val viewId = "null-flag-" + Helpers.randomString(8).toLowerCase
      // Written with raw SQL on purpose: the store's own writers always bind a non-null Boolean,
      // so this is the only way to produce the row an operator restore or import could leave.
      DoobieUtil.runUpdate(
        sql"""INSERT INTO viewdefinition
              (name_, description_, bank_id, account_id, view_id, composite_unique_key,
               metadataview_, issystem_, ispublic_, isfirehose_, useprivatealiasifoneexists_,
               usepublicaliasifoneexists_, hideotheraccountmetadataifalias_)
              VALUES ('null flags', 'row with NULL boolean columns', NULL, NULL, $viewId,
               ${ViewDefinition.getUniqueKey(null, null, viewId)}, '', NULL, NULL, NULL,
               NULL, NULL, NULL)"""
          .update.run)

      ViewDefinition.findByUniqueKey(null, null, viewId) match {
        case Full(view) =>
          view.isFirehose_ should equal(false)
          view.isSystem_ should equal(false)
          view.isPublic_ should equal(false)
          view.usePrivateAliasIfOneExists_ should equal(false)
          view.usePublicAliasIfOneExists_ should equal(false)
          view.hideOtherAccountMetadataIfAlias_ should equal(false)
        case other => fail(s"the row that was just inserted must be readable, got $other")
      }

      DoobieUtil.runUpdate(sql"DELETE FROM viewdefinition WHERE view_id = $viewId".update.run)
    }

    scenario("a freshly built view still carries the entity's own defaults") {
      // The other half of MappedBoolean's behaviour: a new instance does start from defaultValue,
      // and for isFirehose_ that is true.
      ViewDefinition().isFirehose_ should equal(true)
      ViewDefinition().isSystem_ should equal(false)
      ViewDefinition().isPublic_ should equal(false)
    }
  }
}
