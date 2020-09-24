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

object APIMethods_ASApi extends RestHelper {
    val apiVersion =  OBP_PAPI_2_1_1_1.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      authorize ::
      authorizeExt ::
      token ::
      Nil

            
     resourceDocs += ResourceDoc(
       authorize, 
       apiVersion, 
       nameOf(authorize),
       "POST", 
       "/auth/v2_1_1.1/authorize", 
       "Requests OAuth2 authorization code",
       s"""${mockedDataText(true)}
Requests OAuth2 authorization code""", 
       json.parse("""{
  "scope_details" : {
    "consentId" : "consentId",
    "scopeTimeLimit" : "2000-01-23T04:56:07.000+00:00",
    "throttlingPolicy" : "psd2Regulatory",
    "scopeGroupType" : "ais-accounts",
    "privilegeList" : [ {
      "pis:bundle" : {
        "typeOfTransfers" : "domestic",
        "scopeUsageLimit" : "single",
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
      },
      "ais:getAccount" : {
        "scopeUsageLimit" : "single"
      },
      "pis:cancelPayment" : {
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "ais:getTransactionsPending" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:getRecurringPayment" : {
        "tppRecurringPaymentId" : "tppRecurringPaymentId",
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      },
      "ais:getTransactionsDone" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionDetail" : {
        "scopeUsageLimit" : "single"
      },
      "ais:getHolds" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionsCancelled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "accountNumber" : { },
      "pis:domestic" : {
        "transferData" : "",
        "system" : "Elixir",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsScheduled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:nonEEA" : {
        "transferData" : "",
        "transferCharges" : "transferCharges",
        "system" : "Swift",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
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
        }
      },
      "pis:recurring" : {
        "recurrence" : {
          "endDate" : "2000-01-23",
          "startDate" : "2000-01-23",
          "frequency" : {
            "periodType" : "day",
            "periodValue" : 1
          },
          "dayOffOffsetType" : "before"
        },
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
        "scopeUsageLimit" : "single",
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
      },
      "pis:EEA" : {
        "transferData" : "",
        "system" : "SEPA",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          },
          "accountNumber" : { }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsRejected" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais-accounts:getAccounts" : {
        "scopeUsageLimit" : "single"
      },
      "pis:tax" : {
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
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "pis:getPayment" : {
        "tppTransactionId" : "tppTransactionId",
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single"
      },
      "pis:getBundle" : {
        "tppBundleId" : "tppBundleId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "pis:cancelRecurringPayment" : {
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      }
    }, {
      "pis:bundle" : {
        "typeOfTransfers" : "domestic",
        "scopeUsageLimit" : "single",
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
      },
      "ais:getAccount" : {
        "scopeUsageLimit" : "single"
      },
      "pis:cancelPayment" : {
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "ais:getTransactionsPending" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:getRecurringPayment" : {
        "tppRecurringPaymentId" : "tppRecurringPaymentId",
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      },
      "ais:getTransactionsDone" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionDetail" : {
        "scopeUsageLimit" : "single"
      },
      "ais:getHolds" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionsCancelled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "accountNumber" : { },
      "pis:domestic" : {
        "transferData" : "",
        "system" : "Elixir",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsScheduled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:nonEEA" : {
        "transferData" : "",
        "transferCharges" : "transferCharges",
        "system" : "Swift",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
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
        }
      },
      "pis:recurring" : {
        "recurrence" : {
          "endDate" : "2000-01-23",
          "startDate" : "2000-01-23",
          "frequency" : {
            "periodType" : "day",
            "periodValue" : 1
          },
          "dayOffOffsetType" : "before"
        },
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
        "scopeUsageLimit" : "single",
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
      },
      "pis:EEA" : {
        "transferData" : "",
        "system" : "SEPA",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          },
          "accountNumber" : { }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsRejected" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais-accounts:getAccounts" : {
        "scopeUsageLimit" : "single"
      },
      "pis:tax" : {
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
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "pis:getPayment" : {
        "tppTransactionId" : "tppTransactionId",
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single"
      },
      "pis:getBundle" : {
        "tppBundleId" : "tppBundleId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "pis:cancelRecurringPayment" : {
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      }
    } ]
  },
  "scope" : "scope",
  "response_type" : "response_type",
  "requestHeader" : "",
  "redirect_uri" : "redirect_uri",
  "state" : "state",
  "client_id" : "client_id"
}"""),
       json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "aspspRedirectUri" : "aspspRedirectUri"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AS") :: apiTagMockedData :: Nil
     )

     lazy val authorize : OBPEndpoint = {
       case "auth":: "v2_1_1.1":: "authorize" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "aspspRedirectUri" : "aspspRedirectUri"
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       authorizeExt, 
       apiVersion, 
       nameOf(authorizeExt),
       "POST", 
       "/auth/v2_1_1.1/authorizeExt", 
       "Requests OAuth2 authorization code based on One-time authorization code issued by External Authorization Tool",
       s"""${mockedDataText(true)}
Requests OAuth2 authorization code based One-time authorization code issued by External Authorization Tool. Authorization code will be delivered to TPP as callback request from ASPSP if PSU authentication is confirmed by EAT. Callback function must provide similar notification also in case of unsuccessful authentication or its abandonment.""", 
       json.parse("""{
  "eatType" : {
    "code" : "code",
    "description" : "description"
  },
  "scope_details" : {
    "consentId" : "consentId",
    "scopeTimeLimit" : "2000-01-23T04:56:07.000+00:00",
    "throttlingPolicy" : "psd2Regulatory",
    "scopeGroupType" : "ais-accounts",
    "privilegeList" : [ {
      "pis:bundle" : {
        "typeOfTransfers" : "domestic",
        "scopeUsageLimit" : "single",
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
      },
      "ais:getAccount" : {
        "scopeUsageLimit" : "single"
      },
      "pis:cancelPayment" : {
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "ais:getTransactionsPending" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:getRecurringPayment" : {
        "tppRecurringPaymentId" : "tppRecurringPaymentId",
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      },
      "ais:getTransactionsDone" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionDetail" : {
        "scopeUsageLimit" : "single"
      },
      "ais:getHolds" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionsCancelled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "accountNumber" : { },
      "pis:domestic" : {
        "transferData" : "",
        "system" : "Elixir",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsScheduled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:nonEEA" : {
        "transferData" : "",
        "transferCharges" : "transferCharges",
        "system" : "Swift",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
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
        }
      },
      "pis:recurring" : {
        "recurrence" : {
          "endDate" : "2000-01-23",
          "startDate" : "2000-01-23",
          "frequency" : {
            "periodType" : "day",
            "periodValue" : 1
          },
          "dayOffOffsetType" : "before"
        },
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
        "scopeUsageLimit" : "single",
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
      },
      "pis:EEA" : {
        "transferData" : "",
        "system" : "SEPA",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          },
          "accountNumber" : { }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsRejected" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais-accounts:getAccounts" : {
        "scopeUsageLimit" : "single"
      },
      "pis:tax" : {
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
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "pis:getPayment" : {
        "tppTransactionId" : "tppTransactionId",
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single"
      },
      "pis:getBundle" : {
        "tppBundleId" : "tppBundleId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "pis:cancelRecurringPayment" : {
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      }
    }, {
      "pis:bundle" : {
        "typeOfTransfers" : "domestic",
        "scopeUsageLimit" : "single",
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
      },
      "ais:getAccount" : {
        "scopeUsageLimit" : "single"
      },
      "pis:cancelPayment" : {
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "ais:getTransactionsPending" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:getRecurringPayment" : {
        "tppRecurringPaymentId" : "tppRecurringPaymentId",
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      },
      "ais:getTransactionsDone" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionDetail" : {
        "scopeUsageLimit" : "single"
      },
      "ais:getHolds" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionsCancelled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "accountNumber" : { },
      "pis:domestic" : {
        "transferData" : "",
        "system" : "Elixir",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsScheduled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:nonEEA" : {
        "transferData" : "",
        "transferCharges" : "transferCharges",
        "system" : "Swift",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
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
        }
      },
      "pis:recurring" : {
        "recurrence" : {
          "endDate" : "2000-01-23",
          "startDate" : "2000-01-23",
          "frequency" : {
            "periodType" : "day",
            "periodValue" : 1
          },
          "dayOffOffsetType" : "before"
        },
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
        "scopeUsageLimit" : "single",
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
      },
      "pis:EEA" : {
        "transferData" : "",
        "system" : "SEPA",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          },
          "accountNumber" : { }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsRejected" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais-accounts:getAccounts" : {
        "scopeUsageLimit" : "single"
      },
      "pis:tax" : {
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
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "pis:getPayment" : {
        "tppTransactionId" : "tppTransactionId",
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single"
      },
      "pis:getBundle" : {
        "tppBundleId" : "tppBundleId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "pis:cancelRecurringPayment" : {
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      }
    } ]
  },
  "scope" : "scope",
  "response_type" : "response_type",
  "requestHeader" : "",
  "eatCode" : "eatCode",
  "state" : "state",
  "client_id" : "client_id"
}"""),
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AS") :: apiTagMockedData :: Nil
     )

     lazy val authorizeExt : OBPEndpoint = {
       case "auth":: "v2_1_1.1":: "authorizeExt" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       token, 
       apiVersion, 
       nameOf(token),
       "POST", 
       "/auth/v2_1_1.1/token", 
       "Requests OAuth2 access token value",
       s"""${mockedDataText(true)}
Requests OAuth2 access token value""", 
       json.parse("""{
  "refresh_token" : "refresh_token",
  "user_ip" : "user_ip",
  "grant_type" : "grant_type",
  "scope_details" : {
    "consentId" : "consentId",
    "scopeTimeLimit" : "2000-01-23T04:56:07.000+00:00",
    "throttlingPolicy" : "psd2Regulatory",
    "scopeGroupType" : "ais-accounts",
    "privilegeList" : [ {
      "pis:bundle" : {
        "typeOfTransfers" : "domestic",
        "scopeUsageLimit" : "single",
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
      },
      "ais:getAccount" : {
        "scopeUsageLimit" : "single"
      },
      "pis:cancelPayment" : {
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "ais:getTransactionsPending" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:getRecurringPayment" : {
        "tppRecurringPaymentId" : "tppRecurringPaymentId",
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      },
      "ais:getTransactionsDone" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionDetail" : {
        "scopeUsageLimit" : "single"
      },
      "ais:getHolds" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionsCancelled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "accountNumber" : { },
      "pis:domestic" : {
        "transferData" : "",
        "system" : "Elixir",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsScheduled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:nonEEA" : {
        "transferData" : "",
        "transferCharges" : "transferCharges",
        "system" : "Swift",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
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
        }
      },
      "pis:recurring" : {
        "recurrence" : {
          "endDate" : "2000-01-23",
          "startDate" : "2000-01-23",
          "frequency" : {
            "periodType" : "day",
            "periodValue" : 1
          },
          "dayOffOffsetType" : "before"
        },
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
        "scopeUsageLimit" : "single",
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
      },
      "pis:EEA" : {
        "transferData" : "",
        "system" : "SEPA",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          },
          "accountNumber" : { }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsRejected" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais-accounts:getAccounts" : {
        "scopeUsageLimit" : "single"
      },
      "pis:tax" : {
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
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "pis:getPayment" : {
        "tppTransactionId" : "tppTransactionId",
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single"
      },
      "pis:getBundle" : {
        "tppBundleId" : "tppBundleId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "pis:cancelRecurringPayment" : {
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      }
    }, {
      "pis:bundle" : {
        "typeOfTransfers" : "domestic",
        "scopeUsageLimit" : "single",
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
      },
      "ais:getAccount" : {
        "scopeUsageLimit" : "single"
      },
      "pis:cancelPayment" : {
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "ais:getTransactionsPending" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:getRecurringPayment" : {
        "tppRecurringPaymentId" : "tppRecurringPaymentId",
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      },
      "ais:getTransactionsDone" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionDetail" : {
        "scopeUsageLimit" : "single"
      },
      "ais:getHolds" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais:getTransactionsCancelled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "accountNumber" : { },
      "pis:domestic" : {
        "transferData" : "",
        "system" : "Elixir",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsScheduled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "pis:nonEEA" : {
        "transferData" : "",
        "transferCharges" : "transferCharges",
        "system" : "Swift",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
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
        }
      },
      "pis:recurring" : {
        "recurrence" : {
          "endDate" : "2000-01-23",
          "startDate" : "2000-01-23",
          "frequency" : {
            "periodType" : "day",
            "periodValue" : 1
          },
          "dayOffOffsetType" : "before"
        },
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
        "scopeUsageLimit" : "single",
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
      },
      "pis:EEA" : {
        "transferData" : "",
        "system" : "SEPA",
        "sender" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "deliveryMode" : "ExpressD0",
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          },
          "accountNumber" : { }
        },
        "executionMode" : "Immediate"
      },
      "ais:getTransactionsRejected" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 117
      },
      "ais-accounts:getAccounts" : {
        "scopeUsageLimit" : "single"
      },
      "pis:tax" : {
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
        "scopeUsageLimit" : "single",
        "recipient" : {
          "nameAddress" : {
            "value" : [ "value", "value", "value", "value" ]
          }
        },
        "executionMode" : "Immediate"
      },
      "pis:getPayment" : {
        "tppTransactionId" : "tppTransactionId",
        "paymentId" : "paymentId",
        "scopeUsageLimit" : "single"
      },
      "pis:getBundle" : {
        "tppBundleId" : "tppBundleId",
        "scopeUsageLimit" : "single",
        "bundleId" : "bundleId"
      },
      "pis:cancelRecurringPayment" : {
        "recurringPaymentId" : "recurringPaymentId",
        "scopeUsageLimit" : "single"
      }
    } ]
  },
  "scope" : "scope",
  "is_user_session" : true,
  "exchange_token" : "exchange_token",
  "requestHeader" : "",
  "redirect_uri" : "redirect_uri",
  "Code" : "Code",
  "client_id" : "client_id",
  "user_agent" : "user_agent"
}"""),
       json.parse("""{
  "access_token" : "access_token",
  "refresh_token" : "refresh_token",
  "scope_details" : {
    "consentId" : "consentId",
    "resource" : {
      "accounts" : [ "accounts", "accounts" ]
    },
    "scopeTimeLimit" : "scopeTimeLimit",
    "throttlingPolicy" : "psd2Regulatory",
    "privilegeList" : {
      "ais:getTransactionsDone" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais:getHolds" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais:getTransactionsRejected" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais:getTransactionsCancelled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais:getTransactionsScheduled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais-accounts:getAccounts" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "pis:tax" : {
        "scopeUsageLimit" : "single"
      },
      "pis:getPayment" : {
        "scopeUsageLimit" : "single"
      },
      "pis:domestic" : {
        "scopeUsageLimit" : "single"
      },
      "ais:getTransactionDetail" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais:getTransactionsPending" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "pis:bundle" : {
        "scopeUsageLimit" : "single"
      }
    }
  },
  "scope" : "scope",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "token_type" : "token_type",
  "expires_in" : "expires_in"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AS") :: apiTagMockedData :: Nil
     )

     lazy val token : OBPEndpoint = {
       case "auth":: "v2_1_1.1":: "token" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "access_token" : "access_token",
  "refresh_token" : "refresh_token",
  "scope_details" : {
    "consentId" : "consentId",
    "resource" : {
      "accounts" : [ "accounts", "accounts" ]
    },
    "scopeTimeLimit" : "scopeTimeLimit",
    "throttlingPolicy" : "psd2Regulatory",
    "privilegeList" : {
      "ais:getTransactionsDone" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais:getHolds" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais:getTransactionsRejected" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais:getTransactionsCancelled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais:getTransactionsScheduled" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais-accounts:getAccounts" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "pis:tax" : {
        "scopeUsageLimit" : "single"
      },
      "pis:getPayment" : {
        "scopeUsageLimit" : "single"
      },
      "pis:domestic" : {
        "scopeUsageLimit" : "single"
      },
      "ais:getTransactionDetail" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "ais:getTransactionsPending" : {
        "scopeUsageLimit" : "single",
        "maxAllowedHistoryLong" : 880
      },
      "pis:bundle" : {
        "scopeUsageLimit" : "single"
      }
    }
  },
  "scope" : "scope",
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "token_type" : "token_type",
  "expires_in" : "expires_in"
}"""), callContext)
           }
         }
       }

}



