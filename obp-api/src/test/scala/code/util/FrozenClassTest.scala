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

import com.openbankproject.commons.util.ApiVersion
import code.setup.ServerSetup
import org.scalatest.Tag

class FrozenClassTest extends ServerSetup {

  object FrozenClassTag extends Tag("Frozen_Classes")

  val (persistedVersionToEndpointNames, persistedTypeNameToTypeValFields) = FrozenClassUtil.readPersistedFrozenApiInfo
  val (versionToEndpointNames, typeNameToTypeValFields) = FrozenClassUtil.getFrozenApiInfo

  feature("Frozen version apis not changed") {

    scenario(s"count of STABLE api versions should not be reduce, if pretty sure need modify it, please run ${FrozenClassUtil.sourceName}", FrozenClassTag) {

      val persistedStableVersions = persistedVersionToEndpointNames.map(_._1).toSet
      val currentStableVersions = versionToEndpointNames.map(_._1).toSet

      val increasedVersions = persistedStableVersions.diff(currentStableVersions)
      increasedVersions should equal(Set.empty[ApiVersion])
    }

    scenario(s"count of STABLE api versions should not be increased, if pretty sure need modify it, please run ${FrozenClassUtil.sourceName}", FrozenClassTag) {
      val persistedStableVersions = persistedVersionToEndpointNames.map(_._1).toSet
      val currentStableVersions = versionToEndpointNames.map(_._1).toSet

      val reducedVersions = currentStableVersions.diff(persistedStableVersions)
      reducedVersions should equal(Set.empty[ApiVersion])
    }

    scenario(s"api count of versions with STABLE versionStatus should not be reduce, if pretty sure need modify it, please run ${FrozenClassUtil.sourceName}", FrozenClassTag) {
      val reducedApis = for {
        (pVersion, pEndpointNames) <- persistedVersionToEndpointNames
        (version, endpointNames) <- versionToEndpointNames
        if (pVersion == version)
        reducedApisOfVersion = pEndpointNames.diff(endpointNames).mkString(",")
        if (reducedApisOfVersion.size > 0)
      } yield {
        s"$version reduced apis: $reducedApisOfVersion"
      }
      reducedApis should equal(Nil)
    }

    scenario(s"api count of versions with STABLE versionStatus should not be increased, if pretty sure need modify it, please run ${FrozenClassUtil.sourceName}", FrozenClassTag) {
      val increasedApis = for {
        (pVersion, pEndpointNames) <- persistedVersionToEndpointNames
        (version, endpointNames) <- versionToEndpointNames
        if (pVersion == version)
        increasedApis = endpointNames.diff(pEndpointNames).mkString(",")
        if (increasedApis.size > 0)
      } yield {
        s"$version increased apis: $increasedApis"
      }
       increasedApis should equal(Nil)
    }
  }

  feature("Frozen type structure not be modified") {
    scenario(s"frozen class structure should not be modified, if pretty sure need modify it, please run ${FrozenClassUtil.sourceName}", FrozenClassTag) {
          val changedTypes =  for {
            (pTypeName, pFields)  <- persistedTypeNameToTypeValFields.toList
            (typeName, fields) <- typeNameToTypeValFields.toList
            if(pTypeName == typeName && pFields != fields)
          } yield {
            val expectedStructure = pFields.map(pair => s"${pair._1}:${pair._2}").mkString("(", ", ", ")")
            val actualStructure = fields.map(pair => s"${pair._1}:${pair._2}").mkString("(", ", ", ")")

            s"""
               |{
               | typeName: $typeName
               | expectedStructure: $expectedStructure
               | actualStructure: $actualStructure
               |}
               |""".stripMargin
          }
        changedTypes should equal (Nil)
    }
  }
}
