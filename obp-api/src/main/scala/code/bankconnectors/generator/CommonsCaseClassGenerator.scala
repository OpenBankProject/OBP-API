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

package code.bankconnectors.generator

import code.bankconnectors.generator.ConnectorBuilderUtil._
import scala.reflect.runtime.{universe => ru}

object CommonsCaseClassGenerator extends App {

  //We will copy the output to the `obp-commons/src/main/scala/com/openbankproject/commons/model/CommonModel.scala`
  //We need to check if the classCommons is existing or not.
  val allExistingClasses = getClassesFromPackage("com.openbankproject.commons.model")
    .filter(it =>it.getName.endsWith("Commons"))
  
  
  val missingReturnModels: Set[ru.Type] = connectorDeclsMethodsReturnOBPRequiredType
    .map(it => extractReturnModel(it.asMethod.returnType)) // OBPReturnType[Box[IbanChecker]] --> IbanChecker
    .filter(it => {
      val symbol = it.typeSymbol
      val isAbstract = symbol.isAbstract
      isAbstract && //We only need the commons classes for abstract class, eg: ProductAttributeCommons instead of ProductAttribute
        !it.toString.equals("Boolean") //Boolean is also abstract class, so we need to remove it.
    })
    .filterNot(it =>
      allExistingClasses.find(thisClass=> thisClass.toString.contains(s"${it.typeSymbol.name}Commons")).isDefined
    ) //filter what we have implemented 
    .toSet
  
  missingReturnModels.map(_.typeSymbol.fullName).foreach(it => println(s"import $it"))

  def mkClass(tp: ru.Type) = {
    val varibles = tp.decls.map(it => s"${it.name} :${it.typeSignature.typeSymbol.name}").mkString(", \n    ")

      s"""
         |case class ${tp.typeSymbol.name}Commons(
         |    $varibles) extends ${tp.typeSymbol.name}
         |object ${tp.typeSymbol.name}Commons extends Converter[${tp.typeSymbol.name}, ${tp.typeSymbol.name}Commons]    
       """.stripMargin
  }
 // private val str: String = ru.typeOf[Bank].decls.map(it => s"${it.name} :${it.typeSignature.typeSymbol.name}").mkString(", \n")
  private val caseClassStrings: Set[String] = missingReturnModels.map(mkClass)
  println("#################################Started########################################################################")
  caseClassStrings.foreach {
    println
  }

  println("#################################Finished########################################################################")
  println("Please copy and compair the result to obp-commons/src/main/scala/com/openbankproject/commons/model/CommonModel.scala")

  System.exit(0)
}
