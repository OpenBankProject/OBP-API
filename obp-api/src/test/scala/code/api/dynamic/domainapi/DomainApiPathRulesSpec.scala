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

package code.api.dynamic.domainapi

import code.api.dynamic.domainapi.DomainApiPaths.ResourceDocPath
import org.scalatest.{FlatSpec, Matchers}

/**
 * This suite checks the rules that keep a space's paths unambiguous: the Dynamic Entities and Dynamic
 * Resource Docs of one space share one set of paths when a Domain API publishes them, and a Dynamic Entity
 * owns every path that starts with its name. Pure unit tests, with no server and no database.
 */
class DomainApiPathRulesSpec extends FlatSpec with Matchers {

  private def doc(path: String, verb: String = "GET", id: Option[String] = None, name: String = "doc") =
    ResourceDocPath(id, verb, path, name)

  "ambiguous" should "match paths of the same length whose segments are equal or a path variable at each position" in {
    DomainApiPaths.ambiguous(List("registry", "REGISTRY_ID"), List("registry", "summary")) shouldBe true
    DomainApiPaths.ambiguous(List("registry", "REGISTRY_ID"), List("registry", "SITE_ID")) shouldBe true
    DomainApiPaths.ambiguous(List("registry", "summary"), List("registry", "totals")) shouldBe false
    DomainApiPaths.ambiguous(List("registry"), List("registry", "summary")) shouldBe false
  }

  "resourceDocAmbiguities" should "accept a path that starts with a literal no entity or other doc uses" in {
    DomainApiPaths.resourceDocAmbiguities(doc("/registry/summary"), List("activity"), List(doc("/registry/totals"))) shouldBe empty
  }

  it should "refuse a path that starts with the name of one of the space's Dynamic Entities, ignoring case" in {
    DomainApiPaths.resourceDocAmbiguities(doc("/customers/summary"), List("customers"), Nil) should have size 1
    DomainApiPaths.resourceDocAmbiguities(doc("/Customers/summary"), List("customers"), Nil) should have size 1
  }

  it should "refuse a path that starts with the hyphenated form of a Dynamic Entity's name, and the reverse" in {
    DomainApiPaths.resourceDocAmbiguities(doc("/like-this/summary"), List("like_this"), Nil) should have size 1
    DomainApiPaths.resourceDocAmbiguities(doc("/like_this/summary"), List("like-this"), Nil) should have size 1
    DomainApiPaths.resourceDocAmbiguities(doc("/like-that/summary"), List("like_this"), Nil) shouldBe empty
  }

  it should "refuse a path that starts with a path variable, a reserved segment, or has no segment" in {
    DomainApiPaths.resourceDocAmbiguities(doc("/ITEM_ID/summary"), Nil, Nil) should have size 1
    List("my", "public", "community", "openapi.json", "openapi.yaml").foreach { reserved =>
      DomainApiPaths.resourceDocAmbiguities(doc(s"/$reserved/summary"), Nil, Nil) should have size 1
    }
    DomainApiPaths.resourceDocAmbiguities(doc("/"), Nil, Nil) should have size 1
  }

  it should "refuse a path another doc of the same verb would also match, and only of the same verb" in {
    DomainApiPaths.resourceDocAmbiguities(doc("/registry/summary"), Nil, List(doc("/registry/REGISTRY_ID"))) should have size 1
    DomainApiPaths.resourceDocAmbiguities(doc("/registry/SITE_ID"), Nil, List(doc("/registry/REGISTRY_ID"))) should have size 1
    DomainApiPaths.resourceDocAmbiguities(doc("/registry/summary", verb = "POST"), Nil, List(doc("/registry/REGISTRY_ID"))) shouldBe empty
  }

  it should "not compare a doc with itself when it is moved to a new path" in {
    val stored = doc("/registry/REGISTRY_ID", id = Some("doc-1"))
    DomainApiPaths.resourceDocAmbiguities(doc("/registry/SITE_ID", id = Some("doc-1")), Nil, List(stored)) shouldBe empty
  }

  "entityNameAmbiguities" should "refuse a name that a doc's path starts with, or a reserved segment" in {
    DomainApiPaths.entityNameAmbiguities("customers", List(doc("/customers/summary")), Nil) should have size 1
    DomainApiPaths.entityNameAmbiguities("customers", List(doc("/registry/customers")), Nil) shouldBe empty
    DomainApiPaths.entityNameAmbiguities("public", Nil, Nil) should have size 1
  }

  it should "refuse a name that a doc's path starts with in its hyphenated form" in {
    DomainApiPaths.entityNameAmbiguities("like_this", List(doc("/like-this/summary")), Nil) should have size 1
  }

  it should "refuse a name another entity has once hyphens are read as underscores, and only then" in {
    DomainApiPaths.entityNameAmbiguities("like_this", Nil, List("like-this")) should have size 1
    DomainApiPaths.entityNameAmbiguities("like_this", Nil, List("like_that", "other")) shouldBe empty
  }

  "withUnderscoredEntityName" should "turn the hyphens of the entity-name segment into underscores and leave every other segment alone" in {
    val recordId = "6f1c2a3e-1111-2222-3333-444455556666"
    DomainApiPaths.withUnderscoredEntityName(List("like-this")) shouldBe Some(List("like_this"))
    DomainApiPaths.withUnderscoredEntityName(List("like-this", recordId, "access")) shouldBe Some(List("like_this", recordId, "access"))
    List("my", "public", "community").foreach { prefix =>
      DomainApiPaths.withUnderscoredEntityName(List(prefix, "like-this", recordId)) shouldBe Some(List(prefix, "like_this", recordId))
    }
  }

  it should "give None when the entity-name segment has no hyphen" in {
    DomainApiPaths.withUnderscoredEntityName(List("like_this", "6f1c2a3e-1111")) shouldBe None
    DomainApiPaths.withUnderscoredEntityName(List("public", "like_this")) shouldBe None
    DomainApiPaths.withUnderscoredEntityName(Nil) shouldBe None
  }

  "publishedPath" should "publish a Dynamic Entity under the hyphenated form of its name, and a Dynamic Resource Doc as it is" in {
    DomainApiPaths.publishedPath("SYS", "/obp/v7.0.0/banks/SYS/dynamic-entities/like_this/LIKE_THIS_ID") shouldBe Some("/like-this/LIKE_THIS_ID")
    DomainApiPaths.publishedPath("SYS", "/banks/SYS/dynamic-entities/public/like_this") shouldBe Some("/public/like-this")
    DomainApiPaths.publishedPath("SYS", "/banks/SYS/dynamic-resource-doc/registry_summary/SITE_ID") shouldBe Some("/registry_summary/SITE_ID")
  }

  it should "publish a name that already has a hyphen as it is, since only a name not found as written is mapped" in {
    DomainApiPaths.publishedPath("SYS", "/banks/SYS/dynamic-entities/old-style_name") shouldBe Some("/old-style_name")
  }

  "ambiguitiesInSpace" should "list each ambiguity in a space once" in {
    val ambiguities = DomainApiPaths.ambiguitiesInSpace(List("customers"),
      List(doc("/customers/summary", name = "a"), doc("/registry/REGISTRY_ID", name = "b"), doc("/registry/summary", name = "c")))
    ambiguities should have size 2
    DomainApiPaths.ambiguitiesInSpace(List("customers"), List(doc("/registry/summary"))) shouldBe empty
  }

  it should "list two entities whose names differ only by hyphens for underscores" in {
    DomainApiPaths.ambiguitiesInSpace(List("like-this", "like_this", "other"), Nil) should have size 1
  }
}
