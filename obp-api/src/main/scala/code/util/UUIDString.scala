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

package code.util

import code.api.util.APIUtil
import net.liftweb.mapper.{MappedString, Mapper}
import net.liftweb.util.Props

// TODO rename this file or move some classes? Its used for different string definitions.

/**
 * Enforces a default max length.
 */
class UUIDString [T <: Mapper[T]](override val fieldOwner : T) extends MappedString(fieldOwner, UUIDString.MaxLength)

object UUIDString {
  // We use 44 as a default because base64 encoding of sha256 is 44 characters long
  val MaxLength = APIUtil.getPropsAsIntValue("uuid_string.length", 44)
}


class MediumString [T <: Mapper[T]](override val fieldOwner : T) extends MappedString(fieldOwner, MediumString.MaxLength)

object MediumString {
  val MaxLength = APIUtil.getPropsAsIntValue("medium_string.length", 20)
}

class AccountIdString [T <: Mapper[T]](override val fieldOwner : T) extends MappedString(fieldOwner, AccountIdString.MaxLength)

object AccountIdString {
  val MaxLength = APIUtil.getPropsAsIntValue("account_id.length", 64)
}


/*
So we can store a time of day without the date e.g. 23:33 - but also go past midnight e.g. 26:33 if we want to represent the following morning.
 Being string gives us flexibility to store other unstructured code too.
 */
class TwentyFourHourClockString [T <: Mapper[T]](override val fieldOwner : T) extends MappedString(fieldOwner, TwentyFourHourClockString.MaxLength)

object TwentyFourHourClockString {
  val MaxLength = APIUtil.getPropsAsIntValue("time_string.length", 5)
}