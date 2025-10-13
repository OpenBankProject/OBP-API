package code.bankconnectors.ethereum

import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

class DecodeRawTxTest extends FeatureSpec with Matchers with GivenWhenThen {

  feature("Decode raw Ethereum transaction to case class") {
    scenario("Decode a legacy signed raw transaction successfully") {
      Given("a sample legacy signed raw transaction hex string")

//      {
//        "hash": "0x9d1afb5bd997d69a0fb2cb1bf1cf159f3448e7968fa25df1c26b368d9030b0c3",
//        "type": 0,
//        "chainId": 2025,
//        "nonce": 23,
//        "gasPrice": "0x03e8",
//        "gas": "0x5208",
//        "to": "0x627306090abab3a6e1400e9345bc60c78a8bef57",
//        "value": "0x0de0b6b3a7640000",
//        "input": "0x",
//        "from": "0xf17f52151ebef6c7334fad080c5704d77216b732",
//        "v": "0xff6",
//        "r": "0x16878a008fb817df6d771749336fa0c905ec5b7fafcd043f0d9e609a2b5e41e0",
//        "s": "0x611dbe0f2ee2428360c72f4287a2996cb0d45cb8995cc23eb6ba525cb9580e02",
//        "estimatedFee": "0x01406f40"
//      }
      val rawTx = "0xf86b178203e882520894627306090abab3a6e1400e9345bc60c78a8bef57880de0b6b3a764000080820ff6a016878a008fb817df6d771749336fa0c905ec5b7fafcd043f0d9e609a2b5e41e0a0611dbe0f2ee2428360c72f4287a2996cb0d45cb8995cc23eb6ba525cb9580e02"

      When("we decode it to DecodedTxResponse case class")
      val response = DecodeRawTx.decodeRawTxToJson(rawTx)

      Then("the response should contain the expected transaction fields")
      response.hash shouldBe defined
      response.hash.get should startWith ("0x")
      response.`type` shouldBe Some(0)
      response.nonce shouldBe Some(23)
      response.gasPrice shouldBe Some("0x3e8")
      response.gas shouldBe Some("0x5208")
      response.from shouldBe Some("0xf17f52151ebef6c7334fad080c5704d77216b732")
      response.to shouldBe Some("0x627306090abab3a6e1400e9345bc60c78a8bef57")
      response.value shouldBe Some("1")
      response.input shouldBe defined
      response.input.get should (be ("0x") or be (""))
      response.v shouldBe Some("0xff6")
      response.r shouldBe defined
      response.r.get should startWith ("0x")
      response.s shouldBe defined
      response.s.get should startWith ("0x")
      response.estimatedFee shouldBe defined
      response.estimatedFee.get should startWith ("0x")
    }
  }
}