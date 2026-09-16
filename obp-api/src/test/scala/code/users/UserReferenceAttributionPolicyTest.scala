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

package code.users

import bootstrap.liftweb.ToSchemify
import code.setup.ServerSetup
import net.liftweb.mapper.MetaMapper
import org.scalatest.Tag

/**
 * Frozen-style guard over the attribution policy table (`UserReference`), in the spirit of
 * `code.util.FrozenClassTest`: the policy file must name every user-reference column in the schema,
 * and must not name columns that do not exist.
 *
 * Why this exists. Attribution is enforced in the providers, one `UserReference` at a time
 * (ON_BEHALF_OF_USER_ID_PLAN.md Phase 2). Doing that by hand is only safe if the map of what needs
 * doing is provably complete — otherwise a table added next week grows a `UserId` column that
 * silently strands rows on consent users, and nothing anywhere says so. This test is that proof.
 * It does NOT check that a provider actually applies the policy; that is
 * `OnBehalfOfOwnershipSweepTest` (Phase 4 item 3).
 *
 * When this fails on a new table, the fix is to add a `UserReference` for the column with the right
 * policy — or, if the column is not a user reference at all, to list it in
 * `UserReference.notUserIdColumns` with the reason.
 */
class UserReferenceAttributionPolicyTest extends ServerSetup {

  object UserReferenceTag extends Tag("UserReferenceAttributionPolicy")

  /** Column names that look like a user reference. Matches the plan's pattern. */
  private val userReferenceNamePattern = "(?i).*(userid|createdby|grantedby|holder).*".r

  private def mapperClassName(meta: MetaMapper[_]): String =
    meta.getClass.getName.stripSuffix("$")

  /** (mapperClass, fieldName) for every schema field whose name looks like a user reference. */
  private lazy val schemaUserReferenceColumns: List[(String, String)] =
    for {
      meta <- ToSchemify.models
      field <- meta.mappedFields.toList
      if userReferenceNamePattern.pattern.matcher(field.name).matches()
    } yield (mapperClassName(meta), field.name)

  /** (mapperClass, fieldName) -> the references naming it. */
  private lazy val declaredColumns: Map[(String, String), List[UserReference]] =
    UserReference.all
      .flatMap(ref => ref.fields.map(field => (ref.mapperClass, field) -> ref))
      .groupBy(_._1)
      .map { case (key, pairs) => key -> pairs.map(_._2) }

  private lazy val excludedColumns: Set[(String, String)] =
    UserReference.notUserIdColumns.map { case (cls, field, _) => (cls, field) }.toSet

  private lazy val schemaColumnsByClass: Map[String, Set[String]] =
    ToSchemify.models.map(meta => mapperClassName(meta) -> meta.mappedFields.map(_.name).toSet).toMap

  feature("UserReference is a complete map of the user-reference columns in the schema") {

    scenario("every user-reference column in the schema has a policy, or a documented reason not to", UserReferenceTag) {
      val unclaimed = schemaUserReferenceColumns.distinct
        .filterNot(declaredColumns.contains)
        .filterNot(excludedColumns.contains)
        .sorted

      withClue(
        s"""|${unclaimed.size} column(s) look like a user reference but no UserReference names them.
            |Add a UserReference with the right attribution policy, or list the column in
            |UserReference.notUserIdColumns with the reason it is not a user id:
            |${unclaimed.map { case (c, f) => s"  $c.$f" }.mkString("\n")}
            |""".stripMargin) {
        unclaimed shouldBe empty
      }
    }

    scenario("every UserReference names a Mapper class that is in the schema", UserReferenceTag) {
      val unknownClasses = UserReference.all.map(_.mapperClass).distinct
        .filterNot(schemaColumnsByClass.contains)
        .sorted

      withClue(
        s"""|UserReference names ${unknownClasses.size} class(es) that are not in ToSchemify.models.
            |Either the class was renamed or removed (update UserReference), or the table is missing
            |from ToSchemify.models (which would mean it gets no schema at all):
            |${unknownClasses.map(c => s"  $c").mkString("\n")}
            |""".stripMargin) {
        unknownClasses shouldBe empty
      }
    }

    scenario("every UserReference names fields that exist on that Mapper", UserReferenceTag) {
      val unknownFields = for {
        ref <- UserReference.all
        fields <- schemaColumnsByClass.get(ref.mapperClass).toList
        field <- ref.fields
        if !fields.contains(field)
      } yield s"  ${ref.name}: ${ref.mapperClass}.$field"

      withClue(
        s"""|${unknownFields.size} UserReference field name(s) do not exist on their Mapper -- a rename
            |or a typo. The policy silently covers nothing until fixed:
            |${unknownFields.sorted.mkString("\n")}
            |""".stripMargin) {
        unknownFields shouldBe empty
      }
    }

    // MappedEntitlement.mUserId is the deliberate case: EntitlementUser (UseOnBehalfOfUserId) and
    // ConsentEntitlementUser (UseAuthenticatedUserId) name the same column, chosen per createdByProcess.
    scenario("a column named by more than one UserReference has references that differ by policy", UserReferenceTag) {
      val ambiguous = declaredColumns.toList
        .filter { case (_, refs) => refs.size > 1 && refs.map(_.policy).distinct.size == 1 }
        .map { case ((cls, field), refs) => s"  $cls.$field -> ${refs.map(_.name).sorted.mkString(", ")} (all ${refs.head.policy})" }
        .sorted

      withClue(
        s"""|${ambiguous.size} column(s) are named by several UserReferences with the SAME policy, so
            |which one a provider should pass is undecidable. Merge them, or make the distinction real:
            |${ambiguous.mkString("\n")}
            |""".stripMargin) {
        ambiguous shouldBe empty
      }
    }

    scenario("no column is both given a policy and listed as not-a-user-id", UserReferenceTag) {
      val contradictory = declaredColumns.keySet.intersect(excludedColumns).toList
        .map { case (cls, field) => s"  $cls.$field" }.sorted

      withClue(
        s"""|${contradictory.size} column(s) appear in BOTH UserReference.all and notUserIdColumns.
            |The exclusion list is for columns the name pattern catches by accident; a column with a
            |policy must not be in it:
            |${contradictory.mkString("\n")}
            |""".stripMargin) {
        contradictory shouldBe empty
      }
    }

    scenario("notUserIdColumns only lists columns that exist and that the pattern actually catches", UserReferenceTag) {
      val stale = UserReference.notUserIdColumns.flatMap { case (cls, field, _) =>
        schemaColumnsByClass.get(cls) match {
          case None => Some(s"  $cls.$field -- no such Mapper in ToSchemify.models")
          case Some(fields) if !fields.contains(field) => Some(s"  $cls.$field -- no such field")
          case _ if !userReferenceNamePattern.pattern.matcher(field).matches() =>
            Some(s"  $cls.$field -- the name pattern does not catch this, so the entry is dead weight")
          case _ => None
        }
      }.sorted

      withClue(
        s"""|${stale.size} notUserIdColumns entr(ies) are stale. An exclusion that matches nothing hides
            |nothing, and will not be noticed when the real column comes back:
            |${stale.mkString("\n")}
            |""".stripMargin) {
        stale shouldBe empty
      }
    }
  }
}
