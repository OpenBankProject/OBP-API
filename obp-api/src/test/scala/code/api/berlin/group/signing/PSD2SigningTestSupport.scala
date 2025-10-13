package code.api.berlin.group.signing

import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.util.Props
import org.scalatest.{BeforeAndAfterEach, Suite}

import java.nio.file.Path
import scala.util.{Failure, Success}

/**
 * Test support trait that automatically generates and configures PSD2 certificates on the fly
 * This eliminates the need for external certificate files in tests
 */
trait PSD2SigningTestSupport extends BeforeAndAfterEach with PSD2SigningSupport { self: Suite =>
  
  // Generated certificate data
  private var _certificateData: Option[TestCertificateGenerator.CertificateData] = None
  private var _p12Path: Option[Path] = None
  
  // Test configuration
  protected def tppSignaturePassword: String = "testpassword123"
  protected def tppSignatureAlias: String = "test-tpp-alias"
  protected def tppCommonName: String = "Berlin Group Test TPP"
  protected def tppOrganization: String = "Test Bank Organization"
  
  override def beforeEach(): Unit = {
    super.beforeEach()
    
    // Generate certificates on the fly
    TestCertificateGenerator.generateTestCertificateWithTempFiles(
      commonName = tppCommonName,
      organizationName = tppOrganization,
      password = tppSignaturePassword,
      alias = tppSignatureAlias
    ) match {
      case Success((certData, tempP12Path)) =>
        _certificateData = Some(certData)
        _p12Path = Some(tempP12Path)
        
        // Set up properties for the test
        setPropsValues(
          "truststore.path.tpp_signature" -> tempP12Path.toString,
          "truststore.password.tpp_signature" -> tppSignaturePassword,
          "truststore.alias.tpp_signature" -> tppSignatureAlias,
          "use_tpp_signature_revocation_list" -> "false"
        )
        
        println(s"Generated test certificate for: $tppCommonName")
        println(s"Created temporary P12 keystore at: $tempP12Path")
        
      case Failure(exception) =>
        throw new RuntimeException(s"Failed to generate test certificates: ${exception.getMessage}", exception)
    }
  }
  
  override def afterEach(): Unit = {
    // Clean up temporary files
    _p12Path.foreach { path =>
      try {
        java.nio.file.Files.deleteIfExists(path)
      } catch {
        case _: Exception => // Ignore cleanup errors
      }
    }
    _p12Path = None
    _certificateData = None
    
    super.afterEach()
  }
  
  // Implementation of PSD2SigningSupport
  override def berlinGroupPrivateKey: String = {
    _certificateData
      .map(_.privateKeyPem)
      .getOrElse(throw new IllegalStateException("Certificate data not initialized. Make sure beforeEach() is called."))
  }
  
  override def berlinGroupCertificate: String = {
    _certificateData
      .map(_.certificatePem)
      .getOrElse(throw new IllegalStateException("Certificate data not initialized. Make sure beforeEach() is called."))
  }
  
  override def berlinGroupKeyId: String = {
    _certificateData match {
      case Some(certData) =>
        val serialNumber = certData.serialNumber.toString
        s"SN=$serialNumber, CA=CN=$tppCommonName, O=$tppOrganization"
      case None =>
        throw new IllegalStateException("Certificate data not initialized. Make sure beforeEach() is called.")
    }
  }
  
  /**
   * This method should be provided by the parent test class that extends PropsReset
   * We assume it's available from the test setup
   */
  protected def setPropsValues(keyValuePairs: (String, String)*): Unit
  
  /**
   * Get the generated certificate data for advanced test scenarios
   */
  protected def getCertificateData: Option[TestCertificateGenerator.CertificateData] = _certificateData
  
  /**
   * Get the temporary P12 file path
   */
  protected def getP12Path: Option[Path] = _p12Path
  
  /**
   * Validate that all necessary properties are set
   */
  protected def validateTestSetup(): Unit = {
    val requiredProps = List(
      "truststore.path.tpp_signature",
      "truststore.password.tpp_signature", 
      "truststore.alias.tpp_signature"
    )
    
    requiredProps.foreach { prop =>
      val value = APIUtil.getPropsValue(prop).getOrElse("")
      if (value.isEmpty) {
        throw new IllegalStateException(s"Required property '$prop' is not set")
      }
    }
    
    // Verify certificate data is available
    if (_certificateData.isEmpty) {
      throw new IllegalStateException("Certificate data is not initialized")
    }
    
    println("Test setup validation passed")
  }
}

