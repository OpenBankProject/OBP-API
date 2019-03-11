package com.openbankproject.commons.dto

case class CallContextAkka(userId: Option[String] = None,
                           consumerId: Option[String] = None,
                           correlationId: String = "",
                           sessionId: Option[String] = None,
                           )
