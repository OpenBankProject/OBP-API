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

package code.api.sweep

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.util.ApiRole
import code.api.util.http4s.Http4sApp
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.openbankproject.commons.util.JsonAliases.parse
import fs2.Stream
import net.liftweb.mapper.By
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.json4s.JValue
import org.json4s.JsonAST.JObject
import org.typelevel.ci.CIString

/**
 * Shared setup for sweeps that call the API with a fully-entitled caller.
 *
 * FailureSweepTest and SuccessSweepTest each grew an identical "grant every role, then build a
 * DirectLogin header" construction, and AuthSweepTest, FailureSweepTest and SuccessSweepTest each
 * looked up the fixture bank independently. One shared definition here, called from all three,
 * means a future change to either only has one site to update.
 */
trait SweepFixtures { self: DefaultUsers =>

  /** The first sandbox bank the fixtures created, if any. */
  def realBankId: Option[String] =
    code.bankconnectors.LocalMappedConnector.getBanksLegacy(None)
      .map(_._1).getOrElse(Nil).headOption.map(_.bankId.value)

  /**
   * The first account the fixtures created at `bankId`, if any.
   *
   * Read from the Mapper rather than over HTTP for the same reason realBankId is: these sweeps
   * are in-process, and a round trip per lookup would be the only slow part of them.
   */
  def realAccountId(bankId: String): Option[String] =
    code.model.dataAccess.MappedBankAccount
      .find(By(code.model.dataAccess.MappedBankAccount.bank, bankId))
      .map(_.accountId.value)

  /**
   * A caller holding every role in the system.
   *
   * Granted directly through the Entitlement provider rather than over the API -- the same thing
   * 161 existing test files do -- because the goal is to get PAST authorisation, not to test it.
   */
  def omniscientCaller: Map[String, String] = {
    ApiRole.availableRoles.foreach { role =>
      // Bank-scoped roles need a bank; system-wide ones must be granted with an empty bankId.
      // valueOf throws on a name it does not recognise, and availableRoles includes dynamic
      // roles whose backing entity may not exist in this database -- a grant that cannot be
      // made is not a reason to abandon the other several hundred.
      try {
        if (ApiRole.valueOf(role).requiresBankId) {
          Entitlement.entitlement.vend.addEntitlement(realBankId.getOrElse(""), resourceUser1.userId, role)
          // Dynamic Entity endpoints that name no bank check their Roles at the system space, SYS,
          // so a caller holding every role must hold the bank-scoped ones there as well.
          Entitlement.entitlement.vend.addEntitlement(
            code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser1.userId, role)
        } else {
          Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role)
        }
      } catch { case _: Exception => () }
    }
    Map("DirectLogin" -> s"token=${token1.value}")
  }

  /**
   * One in-process request against the whole http4s app. No TCP, no server startup.
   *
   * Shared rather than copied per sweep: AuthSweepTest, FailureSweepTest and SuccessSweepTest
   * each carried their own near-identical copy, differing only in whether they could send a
   * body. Those three still hold theirs; a new sweep must not add a fourth.
   */
  /** Built once: Http4sApp.httpApp is a def that assembles the whole route chain on each call. */
  private lazy val sweepHttpApp = Http4sApp.httpApp

  def callApi(verb: String, path: String, headers: Map[String, String], body: String = "")
             (implicit runtime: IORuntime): (Int, JValue) = {
    val method = Method.fromString(verb.toUpperCase).getOrElse(Method.GET)
    val hdrs   = if (body.nonEmpty) headers + ("Content-Type" -> "application/json") else headers
    val req = Request[IO](
      method  = method,
      uri     = Uri.unsafeFromString(path),
      headers = Headers(hdrs.map { case (k, v) => Header.Raw(CIString(k), v) }.toList),
      body    = if (body.nonEmpty) Stream.emits(body.getBytes("UTF-8")).covary[IO] else Stream.empty
    )
    val resp    = sweepHttpApp.run(req).unsafeRunSync()
    val bodyStr = resp.bodyText.compile.string.unsafeRunSync()
    val json = try { if (bodyStr.trim.isEmpty) JObject(Nil) else parse(bodyStr) }
               catch { case _: Exception => JObject(Nil) }
    (resp.status.code, json)
  }
}
