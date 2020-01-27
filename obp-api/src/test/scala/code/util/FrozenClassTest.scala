package code.util

import com.openbankproject.commons.util.ApiVersion
import code.setup.ServerSetup
import org.scalatest.Tag

class FrozenClassTest extends ServerSetup {

  object FrozenClassTag extends Tag("Frozen_Classes")

  val (persistedVersionToEndpointNames, persistedTypeNameToTypeValFields) = FrozenClassUtil.readPersistedFrozenApiInfo
  val (versionToEndpointNames, typeNameToTypeValFields) = FrozenClassUtil.getFrozenApiInfo

  feature("Frozen version apis not changed") {

    scenario(s"count of STABLE OBPAPIxxxx should not be reduce, if pretty sure need modify it, please run ${FrozenClassUtil.sourceName}", FrozenClassTag) {

      val persistedStableVersions = persistedVersionToEndpointNames.map(_._1).toSet
      val currentStableVersions = versionToEndpointNames.map(_._1).toSet

      val increasedVersions = persistedStableVersions.diff(currentStableVersions)
      increasedVersions should equal(Set.empty[ApiVersion])
    }

    scenario(s"count of STABLE OBPAPIxxxx should not be increased, if pretty sure need modify it, please run ${FrozenClassUtil.sourceName}", FrozenClassTag) {
      val persistedStableVersions = persistedVersionToEndpointNames.map(_._1).toSet
      val currentStableVersions = versionToEndpointNames.map(_._1).toSet

      val reducedVersions = currentStableVersions.diff(persistedStableVersions)
      reducedVersions should equal(Set.empty[ApiVersion])
    }

    scenario(s"api count of STABLE value of OBPAPIxxxx#versionStatus should not be reduce, if pretty sure need modify it, please run ${FrozenClassUtil.sourceName}", FrozenClassTag) {
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

    scenario(s"api count of STABLE value of OBPAPIxxxx#versionStatus should not be increased, if pretty sure need modify it, please run ${FrozenClassUtil.sourceName}", FrozenClassTag) {
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
