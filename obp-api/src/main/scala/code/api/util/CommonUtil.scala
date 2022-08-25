package code.api.util

import java.util

// Introduced in order to replace library: https://mvnrepository.com/artifact/org.apache.commons/commons-collections4/4.4
// which contains vulnerabilities from dependencies:
// CVE-2020-15250
object CommonUtil {
  object Collections {
    def isEmpty(coll: util.Collection[_]): Boolean = coll == null || coll.isEmpty
    def isNotEmpty(coll: util.Collection[_]): Boolean = !isEmpty(coll)
  }
  object Map {
    def isEmpty(map: java.util.Map[_, _]): Boolean = map == null || map.isEmpty
    def isNotEmpty(map: java.util.Map[_, _]): Boolean = !isEmpty(map)
  }
}
