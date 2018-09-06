package code.api.builder
import code.api.util.APIUtil
case class CreateTemplateJson(author: String = """Chinua Achebe""", pages: Int = 209, points: Double = 1.3)
case class TemplateJson(template_id: String = """11231231312""", author: String = """Chinua Achebe""", pages: Int = 209, points: Double = 1.3)
object JsonFactory_APIBuilder {
  val templateJson = TemplateJson()
  val templatesJson = List(templateJson)
  val createTemplateJson = CreateTemplateJson()
  def createTemplate(template: Template) = TemplateJson(template.templateId, template.author, template.pages, template.points)
  def createTemplates(templates: List[Template]) = templates.map(template => TemplateJson(template.templateId, template.author, template.pages, template.points))
  val allFields = for (v <- this.getClass.getDeclaredFields; if APIUtil.notExstingBaseClass(v.getName())) yield {
    v.setAccessible(true)
    v.get(this)
  }
}