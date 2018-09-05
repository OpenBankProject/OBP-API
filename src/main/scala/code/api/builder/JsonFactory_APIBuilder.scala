/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
 */

package code.api.builder

import code.api.util.APIUtil

case class TemplateJson( 
  template_id: String = "123 abcd",
  author: String = """Chinua Achebe""",
  pages: Int = 209,
  points: Double = 1.3
)

case class CreateTemplateJson( 
  author: String = """Chinua Achebe""",
  pages: Int = 209,
  points: Double = 1.3
)

object JsonFactory_APIBuilder
{
  
  val templateJson = TemplateJson()
  val templatesJson = List(templateJson)
  val createTemplateJson = CreateTemplateJson()
  
  def createTemplate(template: Template) = TemplateJson(template.templateId,template.author,template.pages,template.points)
  def createTemplates(templates: List[Template])= 
    templates.map(template => TemplateJson(template.templateId,template.author,template.pages,template.points))
  
  val allFields = for (
    v <- this.getClass.getDeclaredFields; 
    if APIUtil.notExstingBaseClass(v.getName())
  ) yield
    {
      v.setAccessible(true)
      v.get(this)
    }
}