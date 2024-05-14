package code.util

import code.api.util.ApiVersionUtils
import code.api.util.ApiVersionUtils.versions
import code.api.v4_0_0.V400ServerSetup

class ApiVersionUtilsTest extends V400ServerSetup {
  feature("test ApiVersionUtils.valueOf ") {
    scenario("support both fullyQualifiedVersion and apiShortVersion") {
    ApiVersionUtils.valueOf("v4.0.0")
    ApiVersionUtils.valueOf("OBPv4.0.0")

    ApiVersionUtils.valueOf("v1.3")
    ApiVersionUtils.valueOf("BGv1.3")
    ApiVersionUtils.valueOf("dynamic-endpoint")
    ApiVersionUtils.valueOf("dynamic-entity")

    
    versions.map(version => ApiVersionUtils.valueOf(version.apiShortVersion))
    versions.map(version => ApiVersionUtils.valueOf(version.fullyQualifiedVersion))

    //NOTE, when we added the new version, better fix this number manually. and also check the versions
    versions.length shouldBe(24)
  }}
}