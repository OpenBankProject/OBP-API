package code.snippet.BeerSnippet

/**
 * Created by IntelliJ IDEA.
 * User: simonredfern
 * Date: 10/17/11
 * Time: 1:21 PM
 * To change this template use File | Settings | File Templates.
 */

import net.liftweb.util.Helpers._
import xml.NodeSeq

class BeerSnippet {
  def count( xhtml: NodeSeq ) = {
    val consumption = ("Tuborg Beer",2) :: ("Harboe pilsner",2) :: ("Red wine",1) :: ("Pan Galactic Gargle Blaster",1) :: Nil
    def bindConsumption(template: NodeSeq): NodeSeq = {
      consumption.flatMap{ case (bev, count) => bind("beverage", template,"name" -> bev,"count" -> count)}
    }
    bind("person",xhtml, "name" -> "Mads", "consumption" -> bindConsumption _)
  }
}