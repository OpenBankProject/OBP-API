package com.openbankproject.commons.dto.rest

import com.openbankproject.commons.model.{AuthInfoBasic, BankCommons, BankId}


case class OutBoundBankRest (authInfo: AuthInfoBasic, bankId: BankId)
case class InBoundBankRest (authInfo: AuthInfoBasic, data: BankCommons)


