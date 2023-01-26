package code.webhook

import java.io.IOException

import code.webhook.WebhookActor.WebhookRequestTrait
import okhttp3._

object OkHttpWebhookClient {

  private val client = new OkHttpClient
  
  @throws[Exception]
  def makeAsynchronousRequest(request: Request, webhookRequest: WebhookRequestTrait): Unit = {
    client.newCall(request).enqueue(new Callback() {
      def onFailure(call: Call, e: IOException): Unit = {
        WebhookAction.webhookFailure(e.getMessage, webhookRequest)
      }

      @throws[IOException]
      def onResponse(call: Call, response: Response): Unit = {
        val responseBody = response.body
        try {
          if (!response.isSuccessful) throw new IOException("Unexpected code " + response)
          org.scalameta.logger.elem(responseBody.string)
          WebhookAction.webhookResponse(response.code().toString, webhookRequest)
        } finally if (responseBody != null) responseBody.close()
      }
    })
  }
}
