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

import org.scalatest.{FlatSpec, Matchers}

/** Links in the Glossary to the API Explorer's own pages are served fully qualified. */
class GlossaryLinksTest extends FlatSpec with Matchers {

  private val explorer = Glossary.apiExplorerUrl

  "qualifyExplorerLinks" should "prefix links to the API Explorer's pages with its address" in {
    Glossary.qualifyExplorerLinks("see [here](/glossary#Platform%20Apps) and [it](/resource-docs/OBPv7.0.0?operationid=x)") shouldBe
      s"see [here]($explorer/glossary#Platform%20Apps) and [it]($explorer/resource-docs/OBPv7.0.0?operationid=x)"
    Glossary.qualifyExplorerLinks("""<a href="/message-docs/akka">docs</a> and [bank](/?version=OBPv4.0.0)""") shouldBe
      s"""<a href="$explorer/message-docs/akka">docs</a> and [bank]($explorer/?version=OBPv4.0.0)"""
  }

  it should "leave external and already qualified links alone" in {
    val text = s"[akka](https://akka.io/) and [again]($explorer/glossary#API) and [anchor](#Onboarding)"
    Glossary.qualifyExplorerLinks(text) shouldBe text
  }

  it should "leave a path that is not one of the API Explorer's pages alone" in {
    Glossary.qualifyExplorerLinks("[x](/glossaryish) [y](/banks)") shouldBe "[x](/glossaryish) [y](/banks)"
  }

  "getGlossaryItemLink" should "expand to a fully qualified link, with spaces encoded" in {
    Glossary.expandGlossaryPlaceholders(Glossary.getGlossaryItemLink("Platform Apps")) shouldBe
      s"[here]($explorer/glossary#Platform%20Apps)"
  }

  "every served Glossary Item" should "have no site-relative link to the API Explorer" in {
    val relative = Glossary.glossaryItems.toList.filter(i => """\]\(/(glossary|index|resource-docs|message-docs|operationid)\b""".r
      .findFirstIn(i.description()).isDefined).map(_.title)
    relative shouldBe empty
  }
}
