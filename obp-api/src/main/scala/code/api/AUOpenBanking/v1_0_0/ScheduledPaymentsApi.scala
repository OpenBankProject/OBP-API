package code.api.AUOpenBanking.v1_0_0

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

object APIMethods_ScheduledPaymentsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      listScheduledPayments ::
      listScheduledPaymentsBulk ::
      listScheduledPaymentsSpecificAccounts ::
      Nil

            
     resourceDocs += ResourceDoc(
       listScheduledPayments, 
       apiVersion, 
       nameOf(listScheduledPayments),
       "GET", 
       "/banking/accounts/ACCOUNT_ID/payments/scheduled", 
       "Get Scheduled Payments for Account",
       s"""${mockedDataText(true)}
            Obtain scheduled, outgoing payments for a specific account

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Banking") ::ApiTag("Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val listScheduledPayments : OBPEndpoint = {
       case "banking":: "accounts" :: accountId:: "payments":: "scheduled" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       listScheduledPaymentsBulk, 
       apiVersion, 
       nameOf(listScheduledPaymentsBulk),
       "GET", 
       "/banking/payments/scheduled", 
       "Get Scheduled Payments Bulk",
       s"""${mockedDataText(true)}
            Obtain scheduled payments for multiple, filtered accounts that are the source of funds for the payments

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Banking") ::ApiTag("Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val listScheduledPaymentsBulk : OBPEndpoint = {
       case "banking":: "payments":: "scheduled" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       listScheduledPaymentsSpecificAccounts, 
       apiVersion, 
       nameOf(listScheduledPaymentsSpecificAccounts),
       "POST", 
       "/banking/payments/scheduled", 
       "Get Scheduled Payments For Specific Accounts",
       s"""${mockedDataText(true)}
            Obtain scheduled payments for a specified list of accounts

            """,
       json.parse("""{
  "data" : {
    "accountIds" : [ "accountIds", "accountIds" ]
  },
    "meta" : " ",
}"""),
       json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Banking") ::ApiTag("Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val listScheduledPaymentsSpecificAccounts : OBPEndpoint = {
       case "banking":: "payments":: "scheduled" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""), callContext)
           }
         }
       }

}



