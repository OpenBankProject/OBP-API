package code.api.Polish.v2_1_1_1

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global

object APIMethods_PISApi extends RestHelper {
    val apiVersion =  OBP_PAPI_2_1_1_1.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      bundle ::
      cancelPayments ::
      cancelRecurringPayment ::
      domestic ::
      eEA ::
      getBundle ::
      getMultiplePayments ::
      getPayment ::
      getRecurringPayment ::
      nonEEA ::
      recurring ::
      tax ::
      Nil

            
     resourceDocs += ResourceDoc(
       bundle, 
       apiVersion, 
       nameOf(bundle),
       "POST", 
       "/payments/v2_1_1.1/bundle", 
       "Initiate many transfers as bundle",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "tppBundleId" : "tppBundleId",
  "typeOfTransfers" : "domestic",
  "taxTransfers" : [ {
    "transferData" : "",
    "usInfo" : {
      "periodId" : "periodId",
      "periodType" : "periodType",
      "payerInfo" : {
        "payorId" : "payorId",
        "payorIdType" : "N"
      },
      "formCode" : "formCode",
      "year" : 6026,
      "obligationId" : "obligationId"
    },
    "tppTransactionId" : "tppTransactionId",
    "system" : "Elixir",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "executionMode" : "Immediate",
    "hold" : true
  }, {
    "transferData" : "",
    "usInfo" : {
      "periodId" : "periodId",
      "periodType" : "periodType",
      "payerInfo" : {
        "payorId" : "payorId",
        "payorIdType" : "N"
      },
      "formCode" : "formCode",
      "year" : 6026,
      "obligationId" : "obligationId"
    },
    "tppTransactionId" : "tppTransactionId",
    "system" : "Elixir",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "executionMode" : "Immediate",
    "hold" : true
  } ],
  "requestHeader" : "",
  "domesticTransfers" : [ {
    "transferData" : "",
    "tppTransactionId" : "tppTransactionId",
    "system" : "Elixir",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "executionMode" : "Immediate",
    "hold" : true
  }, {
    "transferData" : "",
    "tppTransactionId" : "tppTransactionId",
    "system" : "Elixir",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "executionMode" : "Immediate",
    "hold" : true
  } ],
  "EEATransfers" : [ {
    "transferData" : "",
    "tppTransactionId" : "tppTransactionId",
    "system" : "SEPA",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      },
      "accountNumber" : { }
    },
    "executionMode" : "Immediate",
    "hold" : true
  }, {
    "transferData" : "",
    "tppTransactionId" : "tppTransactionId",
    "system" : "SEPA",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      },
      "accountNumber" : { }
    },
    "executionMode" : "Immediate",
    "hold" : true
  } ],
  "transfersTotalAmount" : "transfersTotalAmount",
  "nonEEATransfers" : [ {
    "transferData" : "",
    "transferCharges" : "transferCharges",
    "tppTransactionId" : "tppTransactionId",
    "system" : "Swift",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      },
      "accountNumber" : { }
    },
    "executionMode" : "Immediate",
    "recipientBank" : {
      "code" : "code",
      "address" : "",
      "bicOrSwift" : "bicOrSwift",
      "countryCode" : "countryCode",
      "name" : "name"
    },
    "hold" : true
  }, {
    "transferData" : "",
    "transferCharges" : "transferCharges",
    "tppTransactionId" : "tppTransactionId",
    "system" : "Swift",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      },
      "accountNumber" : { }
    },
    "executionMode" : "Immediate",
    "recipientBank" : {
      "code" : "code",
      "address" : "",
      "bicOrSwift" : "bicOrSwift",
      "countryCode" : "countryCode",
      "name" : "name"
    },
    "hold" : true
  } ]
}"""),
       json.parse("""{
  "payments" : [ {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  }, {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  } ],
  "bundleId" : "bundleId",
  "bundleDetailedStatus" : "bundleDetailedStatus",
  "bundleStatus" : "inProgress"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val bundle : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "bundle" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "payments" : [ {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  }, {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  } ],
  "bundleId" : "bundleId",
  "bundleDetailedStatus" : "bundleDetailedStatus",
  "bundleStatus" : "inProgress"
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       cancelPayments, 
       apiVersion, 
       nameOf(cancelPayments),
       "POST", 
       "/payments/v2_1_1.1/cancelPayments", 
       "Cancelation of future dated payment",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "paymentId" : "paymentId",
  "bundleId" : "bundleId",
  "requestHeader" : ""
}"""),
       json.parse("""{
  "payments" : [ {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  }, {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  } ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val cancelPayments : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "cancelPayments" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "payments" : [ {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  }, {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  } ]
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       cancelRecurringPayment, 
       apiVersion, 
       nameOf(cancelRecurringPayment),
       "POST", 
       "/payments/v2_1_1.1/cancelRecurringPayment", 
       "Cancelation of recurring payment",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "recurringPaymentId" : "recurringPaymentId",
  "requestHeader" : ""
}"""),
       json.parse("""{
  "tppRecurringPaymentId" : "tppRecurringPaymentId",
  "recurringPaymentId" : "recurringPaymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "recurringPaymentStatus" : "submitted",
  "recurringPaymentDetailedStatus" : "recurringPaymentDetailedStatus"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val cancelRecurringPayment : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "cancelRecurringPayment" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "tppRecurringPaymentId" : "tppRecurringPaymentId",
  "recurringPaymentId" : "recurringPaymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "recurringPaymentStatus" : "submitted",
  "recurringPaymentDetailedStatus" : "recurringPaymentDetailedStatus"
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       domestic, 
       apiVersion, 
       nameOf(domestic),
       "POST", 
       "/payments/v2_1_1.1/domestic", 
       "Initiate domestic transfer",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "transferData" : "",
  "tppTransactionId" : "tppTransactionId",
  "system" : "Elixir",
  "sender" : {
    "nameAddress" : {
      "value" : [ "value", "value", "value", "value" ]
    }
  },
  "deliveryMode" : "ExpressD0",
  "recipient" : {
    "nameAddress" : {
      "value" : [ "value", "value", "value", "value" ]
    }
  },
  "executionMode" : "Immediate",
  "requestHeader" : "",
  "hold" : true
}"""),
       json.parse("""{
  "generalStatus" : { },
  "detailedStatus" : "detailedStatus",
  "paymentId" : "paymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val domestic : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "domestic" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "generalStatus" : { },
  "detailedStatus" : "detailedStatus",
  "paymentId" : "paymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       eEA, 
       apiVersion, 
       nameOf(eEA),
       "POST", 
       "/payments/v2_1_1.1/EEA", 
       "Initiate SEPA foreign transfers",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "transferData" : "",
  "tppTransactionId" : "tppTransactionId",
  "system" : "SEPA",
  "sender" : {
    "nameAddress" : {
      "value" : [ "value", "value", "value", "value" ]
    }
  },
  "deliveryMode" : "ExpressD0",
  "recipient" : {
    "nameAddress" : {
      "value" : [ "value", "value", "value", "value" ]
    },
    "accountNumber" : { }
  },
  "executionMode" : "Immediate",
  "requestHeader" : "",
  "hold" : true
}"""),
       json.parse("""{
  "generalStatus" : { },
  "detailedStatus" : "detailedStatus",
  "paymentId" : "paymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val eEA : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "EEA" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "generalStatus" : { },
  "detailedStatus" : "detailedStatus",
  "paymentId" : "paymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getBundle, 
       apiVersion, 
       nameOf(getBundle),
       "POST", 
       "/payments/v2_1_1.1/getBundle", 
       "Get the status of bundle of payments",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "tppBundleId" : "tppBundleId",
  "transactionsIncluded" : true,
  "bundleId" : "bundleId",
  "requestHeader" : ""
}"""),
       json.parse("""{
  "tppBundleId" : "tppBundleId",
  "payments" : [ {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  }, {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  } ],
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "bundleId" : "bundleId",
  "bundleDetailedStatus" : "bundleDetailedStatus",
  "bundleStatus" : "inProgress"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val getBundle : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "getBundle" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "tppBundleId" : "tppBundleId",
  "payments" : [ {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  }, {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  } ],
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "bundleId" : "bundleId",
  "bundleDetailedStatus" : "bundleDetailedStatus",
  "bundleStatus" : "inProgress"
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getMultiplePayments, 
       apiVersion, 
       nameOf(getMultiplePayments),
       "POST", 
       "/payments/v2_1_1.1/getMultiplePayments", 
       "Get the status of multiple payments",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "payments" : [ {
    "tppTransactionId" : "tppTransactionId",
    "paymentId" : "paymentId",
    "accessToken" : "accessToken"
  }, {
    "tppTransactionId" : "tppTransactionId",
    "paymentId" : "paymentId",
    "accessToken" : "accessToken"
  } ],
  "requestHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "ipAddress" : "ipAddress",
    "tppId" : "tppId",
    "userAgent" : "userAgent"
  }
}"""),
       json.parse("""{
  "payments" : [ {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  }, {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  } ],
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val getMultiplePayments : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "getMultiplePayments" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "payments" : [ {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  }, {
    "generalStatus" : { },
    "tppTransactionId" : "tppTransactionId",
    "detailedStatus" : "detailedStatus",
    "paymentId" : "paymentId",
    "executionMode" : "Immediate"
  } ],
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPayment, 
       apiVersion, 
       nameOf(getPayment),
       "POST", 
       "/payments/v2_1_1.1/getPayment", 
       "Get the status of payment",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "tppTransactionId" : "tppTransactionId",
  "paymentId" : "paymentId",
  "requestHeader" : ""
}"""),
       json.parse(""""""""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val getPayment : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "getPayment" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse(""""""""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getRecurringPayment, 
       apiVersion, 
       nameOf(getRecurringPayment),
       "POST", 
       "/payments/v2_1_1.1/getRecurringPayment", 
       "Get the status of recurring payment",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "tppRecurringPaymentId" : "tppRecurringPaymentId",
  "recurringPaymentId" : "recurringPaymentId",
  "requestHeader" : ""
}"""),
       json.parse("""{
  "tppRecurringPaymentId" : "tppRecurringPaymentId",
  "recurringPaymentId" : "recurringPaymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "recurringPaymentStatus" : "submitted",
  "recurringPaymentDetailedStatus" : "recurringPaymentDetailedStatus"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val getRecurringPayment : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "getRecurringPayment" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "tppRecurringPaymentId" : "tppRecurringPaymentId",
  "recurringPaymentId" : "recurringPaymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "recurringPaymentStatus" : "submitted",
  "recurringPaymentDetailedStatus" : "recurringPaymentDetailedStatus"
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       nonEEA, 
       apiVersion, 
       nameOf(nonEEA),
       "POST", 
       "/payments/v2_1_1.1/nonEEA", 
       "Initiate non SEPA foreign transfers",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "transferData" : "",
  "transferCharges" : "transferCharges",
  "tppTransactionId" : "tppTransactionId",
  "system" : "Swift",
  "sender" : {
    "nameAddress" : {
      "value" : [ "value", "value", "value", "value" ]
    }
  },
  "deliveryMode" : "ExpressD0",
  "recipient" : {
    "nameAddress" : {
      "value" : [ "value", "value", "value", "value" ]
    },
    "accountNumber" : { }
  },
  "executionMode" : "Immediate",
  "requestHeader" : "",
  "recipientBank" : {
    "code" : "code",
    "address" : "",
    "bicOrSwift" : "bicOrSwift",
    "countryCode" : "countryCode",
    "name" : "name"
  },
  "hold" : true
}"""),
       json.parse("""{
  "generalStatus" : { },
  "detailedStatus" : "detailedStatus",
  "paymentId" : "paymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val nonEEA : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "nonEEA" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "generalStatus" : { },
  "detailedStatus" : "detailedStatus",
  "paymentId" : "paymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       recurring, 
       apiVersion, 
       nameOf(recurring),
       "POST", 
       "/payments/v2_1_1.1/recurring", 
       "Defines new recurring payment",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "recurrence" : {
    "endDate" : "2000-01-23",
    "startDate" : "2000-01-23",
    "frequency" : {
      "periodType" : "day",
      "periodValue" : 1
    },
    "dayOffOffsetType" : "before"
  },
  "tppRecurringPaymentId" : "tppRecurringPaymentId",
  "nonEEAPayment" : {
    "transferData" : "",
    "transferCharges" : "transferCharges",
    "system" : "Swift",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      },
      "accountNumber" : { }
    },
    "recipientBank" : {
      "code" : "code",
      "address" : "",
      "bicOrSwift" : "bicOrSwift",
      "countryCode" : "countryCode",
      "name" : "name"
    },
    "hold" : true
  },
  "domesticPayment" : {
    "transferData" : "",
    "system" : "Elixir",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "hold" : true
  },
  "requestHeader" : "",
  "EEAPayment" : {
    "transferData" : "",
    "system" : "SEPA",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      },
      "accountNumber" : { }
    },
    "hold" : true
  },
  "taxPayment" : {
    "transferData" : "",
    "usInfo" : {
      "periodId" : "periodId",
      "periodType" : "periodType",
      "payerInfo" : {
        "payorId" : "payorId",
        "payorIdType" : "N"
      },
      "formCode" : "formCode",
      "year" : 6026,
      "obligationId" : "obligationId"
    },
    "system" : "Elixir",
    "sender" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "deliveryMode" : "ExpressD0",
    "recipient" : {
      "nameAddress" : {
        "value" : [ "value", "value", "value", "value" ]
      }
    },
    "hold" : true
  },
  "typeOfPayment" : "domestic"
}"""),
       json.parse("""{
  "recurrence" : {
    "endDate" : "2000-01-23",
    "startDate" : "2000-01-23",
    "frequency" : {
      "periodType" : "day",
      "periodValue" : 1
    },
    "dayOffOffsetType" : "before"
  },
  "recurringPaymentId" : "recurringPaymentId",
  "recurringPaymentStatus" : "submitted",
  "recurringPaymentDetailedStatus" : "recurringPaymentDetailedStatus"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val recurring : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "recurring" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "recurrence" : {
    "endDate" : "2000-01-23",
    "startDate" : "2000-01-23",
    "frequency" : {
      "periodType" : "day",
      "periodValue" : 1
    },
    "dayOffOffsetType" : "before"
  },
  "recurringPaymentId" : "recurringPaymentId",
  "recurringPaymentStatus" : "submitted",
  "recurringPaymentDetailedStatus" : "recurringPaymentDetailedStatus"
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       tax, 
       apiVersion, 
       nameOf(tax),
       "POST", 
       "/payments/v2_1_1.1/tax", 
       "Initiate tax transfer",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "transferData" : "",
  "usInfo" : {
    "periodId" : "periodId",
    "periodType" : "periodType",
    "payerInfo" : {
      "payorId" : "payorId",
      "payorIdType" : "N"
    },
    "formCode" : "formCode",
    "year" : 6026,
    "obligationId" : "obligationId"
  },
  "tppTransactionId" : "tppTransactionId",
  "system" : "Elixir",
  "sender" : {
    "nameAddress" : {
      "value" : [ "value", "value", "value", "value" ]
    }
  },
  "deliveryMode" : "ExpressD0",
  "recipient" : {
    "nameAddress" : {
      "value" : [ "value", "value", "value", "value" ]
    }
  },
  "executionMode" : "Immediate",
  "requestHeader" : "",
  "hold" : true
}"""),
       json.parse("""{
  "generalStatus" : { },
  "detailedStatus" : "detailedStatus",
  "paymentId" : "paymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PIS") :: apiTagMockedData :: Nil
     )

     lazy val tax : OBPEndpoint = {
       case "payments":: "v2_1_1.1":: "tax" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "generalStatus" : { },
  "detailedStatus" : "detailedStatus",
  "paymentId" : "paymentId",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""), callContext)
           }
         }
       }

}



