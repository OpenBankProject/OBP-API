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

  "resourceDocPathProblems" should "accept a path that starts with a literal no entity or other doc uses" in {
    DomainApiPaths.resourceDocPathProblems(doc("/registry/summary"), List("activity"), List(doc("/registry/totals"))) shouldBe empty
  }

  it should "refuse a path that starts with the name of one of the space's Dynamic Entities, ignoring case" in {
    DomainApiPaths.resourceDocPathProblems(doc("/customers/summary"), List("customers"), Nil) should have size 1
    DomainApiPaths.resourceDocPathProblems(doc("/Customers/summary"), List("customers"), Nil) should have size 1
  }

  it should "refuse a path that starts with a path variable, a reserved segment, or has no segment" in {
    DomainApiPaths.resourceDocPathProblems(doc("/ITEM_ID/summary"), Nil, Nil) should have size 1
    List("my", "public", "community", "openapi.json", "openapi.yaml").foreach { reserved =>
      DomainApiPaths.resourceDocPathProblems(doc(s"/$reserved/summary"), Nil, Nil) should have size 1
    }
    DomainApiPaths.resourceDocPathProblems(doc("/"), Nil, Nil) should have size 1
  }

  it should "refuse a path another doc of the same verb would also match, and only of the same verb" in {
    DomainApiPaths.resourceDocPathProblems(doc("/registry/summary"), Nil, List(doc("/registry/REGISTRY_ID"))) should have size 1
    DomainApiPaths.resourceDocPathProblems(doc("/registry/SITE_ID"), Nil, List(doc("/registry/REGISTRY_ID"))) should have size 1
    DomainApiPaths.resourceDocPathProblems(doc("/registry/summary", verb = "POST"), Nil, List(doc("/registry/REGISTRY_ID"))) shouldBe empty
  }

  it should "not compare a doc with itself when it is moved to a new path" in {
    val stored = doc("/registry/REGISTRY_ID", id = Some("doc-1"))
    DomainApiPaths.resourceDocPathProblems(doc("/registry/SITE_ID", id = Some("doc-1")), Nil, List(stored)) shouldBe empty
  }

  "entityNameProblems" should "refuse a name that a doc's path starts with, or a reserved segment" in {
    DomainApiPaths.entityNameProblems("customers", List(doc("/customers/summary"))) should have size 1
    DomainApiPaths.entityNameProblems("customers", List(doc("/registry/customers"))) shouldBe empty
    DomainApiPaths.entityNameProblems("public", Nil) should have size 1
  }

  "spaceProblems" should "list each ambiguity in a space once" in {
    val problems = DomainApiPaths.spaceProblems(List("customers"),
      List(doc("/customers/summary", name = "a"), doc("/registry/REGISTRY_ID", name = "b"), doc("/registry/summary", name = "c")))
    problems should have size 2
    DomainApiPaths.spaceProblems(List("customers"), List(doc("/registry/summary"))) shouldBe empty
  }
}
