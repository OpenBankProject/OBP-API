@Grab("groovy-json")
import groovy.json.JsonSlurper

@RestController
class RemoteEndpointApi {

    @GetMapping(value="/banks")
    def getBanks() {
        parseJson(
         """[
            {
              "bankId":"bankId001",
              "shortName":"The Royal Bank of Scotland",
              "fullName":"The Royal Bank of Scotland",
              "logoUrl":"http://www.red-bank-shoreditch.com/logo.gif",
              "websiteUrl":"http://www.red-bank-shoreditch.com",
              "bankRoutingScheme":"OBP",
              "bankRoutingAddress":"rbs"
            },
             {
              "bankId":"bankId002",
              "shortName":"The Royal Bank of Scotland",
              "fullName":"The Royal Bank of Scotland",
              "logoUrl":"http://www.red-bank-shoreditch.com/logo.gif",
              "websiteUrl":"http://www.red-bank-shoreditch.com",
              "bankRoutingScheme":"OBP",
              "bankRoutingAddress":"rbs"
            }
            ]
        """
        )
    }

    @GetMapping(value="/banks/{BANK_ID}")
    def getBankById(@PathVariable("BANK_ID") bankId) {
        parseJson(
                """
                {
                  "bankId":"${bankId}",
                  "shortName":"The Royal Bank of Scotland",
                  "fullName":"The Royal Bank of Scotland",
                  "logoUrl":"http://www.red-bank-shoreditch.com/logo.gif",
                  "websiteUrl":"http://www.red-bank-shoreditch.com",
                  "bankRoutingScheme":"OBP",
                  "bankRoutingAddress":"rbs"
                }
                """
        )
    }
    @GetMapping(value="/banks/{BANK_ID}/accounts")
    def getAccountByBankId(@PathVariable("BANK_ID") bankId) {
        parseJson(
                """{
            "accounts":[
                {
                    "id":"8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
                    "label":"NoneLabel",
                    "bank_id":"${bankId}",
                    "number":"this is account number"
                }
                ]    
            }
        """
        )
    }

    def parseJson(jsonStr) {
        new JsonSlurper().parseText(jsonStr)
    }
}