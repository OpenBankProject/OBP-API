package code.api

import code.api.util.CustomJsonFormats
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Formats

/**
  * this RestHelper is just for fix bugs of net.liftweb.http.rest.RestHelper
  */
trait RestHelperX extends RestHelper{
  // note, because RestHelper have a implicit Formats, it is not correct for OBP, so here override it
  protected implicit override abstract def formats: Formats = CustomJsonFormats.formats
}
