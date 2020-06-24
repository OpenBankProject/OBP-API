$(document).ready(function() {
	//fallback for html5 placeholder
	if ( !("placeholder" in document.createElement("input")) ) {
		$("input[placeholder], textarea[placeholder]").each(function() {
			var val = $(this).attr("placeholder");
			if ( this.value == "" ) {
				this.value = val;
			}
			$(this).focus(function() {
				if ( this.value == val ) {
					this.value = "";
				}
			}).blur(function() {
				if ( $.trim(this.value) == "" ) {
					this.value = val;
				}
			})
		});

		// Clear default placeholder values on form submit
		$('form').submit(function() {
			$(this).find("input[placeholder], textarea[placeholder]").each(function() {
				if ( this.value == $(this).attr("placeholder") ) {
					this.value = "";
				}
			});
		});
	}

	// Enforce check of Terms and Conditions (if existing) on signup form
	// $('#signup form').submit(function() {
	// 	var agreeTerms = $('#signup #signup-agree-terms input');
	// 	if (agreeTerms.length > 0) {
	// 		if (!agreeTerms.prop('checked')) {
	// 			var msg = 'Please agree to the Terms & Conditions';
	// 			$('#signup #signup-error #error').html(msg);
	// 			$('#signup #signup-error').removeClass('hide');
	// 			return false;
	// 		}
	// 	}
	// 	return true;
	// });

	// Enforce check of Privacy Policy (if existing) on signup form
	$('#signup form').submit(function() {
		var agreePrivacyPolicy = $('#signup #signup-agree-privacy-policy input');
		if (agreePrivacyPolicy.length > 0) {
			if (!agreePrivacyPolicy.prop('checked')) {
				var msg = 'Please agree to the Privacy Policy';
				$('#signup #signup-error #error').html(msg);
				$('#signup #signup-error').removeClass('hide');
				return false;
			}
		}
		return true;
	});

	// Show sign up errors - FIXME: Change backend to (not) show errors
	var signupErrorFirstname = $('#signup #signup-error #authuser_firstName');
	var txtFirstName = $('#signup #txtFirstName');
	if (signupErrorFirstname.length > 0 && signupErrorFirstname.html().length > 0) {
		signupErrorFirstname.parent().removeClass('hide');
		txtFirstName.addClass("error-border")
	}
	var signupErrorLastname = $('#signup #signup-error #authuser_lastName');
	var txtLastName = $('#signup #txtLastName');
	if (signupErrorLastname.length > 0 && signupErrorLastname.html().length > 0) {
		signupErrorLastname.parent().removeClass('hide');
		txtLastName.addClass("error-border")
	}
	var signupError = $('#signup #signup-error #authuser_email');
	var txtEmail = $('#signup #txtEmail');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		txtEmail.addClass("error-border")
	}
	var signupErrorAuthuserName = $('#signup #signup-error #authuser_username');
	var txtUsername = $('#signup #txtUsername');
	if (signupErrorAuthuserName.length > 0 && signupErrorAuthuserName.html().length > 0) {
		signupErrorAuthuserName.parent().removeClass('hide');
		txtUsername.addClass("error-border")
	}
	var signupErrorPassword = $('#signup #signup-error #authuser_password');
	var signupErrorRepeat = $('#signup #signup-error #authuser_password_repeat');
	var textPassword = $('#signup #textPassword');
	var textPasswordRepeat = $('#signup #textPasswordRepeat');
	if (signupErrorPassword.length > 0 && signupErrorPassword.html().length > 0) {
		signupErrorPassword.parent().removeClass('hide');
		signupErrorRepeat.parent().removeClass('hide');
		textPassword.addClass("error-border")
		textPasswordRepeat.addClass("error-border")
	}

	var loginUsernameError = $('#authorise #login-form-username-error');
	var loginUsernameForm = $('#authorise #username');
	if (loginUsernameError.length > 0 && loginUsernameError.html().length > 0) {
		loginUsernameError.parent().removeClass('hide');
		loginUsernameForm.addClass("error-border")
	} else{
		loginUsernameError.parent().addClass('hide');
		loginUsernameForm.css("border","").css("background","")
	}

	var loginPasswordError = $('#authorise #login-form-password-error');
	var loginPasswordForm = $('#authorise #password');
	if (loginPasswordError.length > 0 && loginPasswordError.html().length > 0) {
		loginPasswordError.parent().removeClass('hide');
		loginPasswordForm.addClass("error-border")
	}else{
		loginPasswordError.parent().addClass('hide');
		loginPasswordForm.css("border","").css("background","")
	}

	var consumerRegistrationAppnameError = $('#register-consumer-input #consumer-registration-app-name-error');
	var consumerRegistrationAppnameForm = $('#register-consumer-input #appName');
	if (consumerRegistrationAppnameError.length > 0 && consumerRegistrationAppnameError.html().length > 0) {
		consumerRegistrationAppnameError.parent().removeClass('hide');
		consumerRegistrationAppnameForm.addClass("error-border")
	}else{
		consumerRegistrationAppnameError.parent().addClass('hide');
	}

	var consumerRegistrationAppDeveloperError = $('#register-consumer-input #consumer-registration-app-developer-error');
	var consumerRegistrationAppDeveloperForm = $('#register-consumer-input #appDev');
	if (consumerRegistrationAppDeveloperError.length > 0 && consumerRegistrationAppDeveloperError.html().length > 0) {
		consumerRegistrationAppDeveloperError.parent().removeClass('hide');
		consumerRegistrationAppDeveloperForm.addClass("error-border")
	}else{
		consumerRegistrationAppDeveloperError.parent().addClass('hide');
	}

	var consumerRegistrationAppDescError = $('#register-consumer-input #consumer-registration-app-description-error');
	var consumerRegistrationAppDescForm = $('#register-consumer-input #appDesc');
	if (consumerRegistrationAppDescError.length > 0 && consumerRegistrationAppDescError.html().length > 0) {
		consumerRegistrationAppDescError.parent().removeClass('hide');
		consumerRegistrationAppDescForm.addClass("error-border")
	}else{
		consumerRegistrationAppDescError.parent().addClass('hide');
	}

	var consumerRegistrationAppRedirectUrlError = $('#register-consumer-input #consumer-registration-app-description-error');
	var consumerRegistrationAppRedirectUrlForm = $('#register-consumer-input #appDesc');
	if (consumerRegistrationAppRedirectUrlError.length > 0 && consumerRegistrationAppRedirectUrlError.html().length > 0) {
		consumerRegistrationAppRedirectUrlError.parent().removeClass('hide');
		consumerRegistrationAppRedirectUrlForm.addClass("error-border")
	}else{
		consumerRegistrationAppRedirectUrlError.parent().addClass('hide');
	}

	var registerConsumerError = $('#register-consumer-input #register-consumer-errors');
	if (registerConsumerError.length > 0 && registerConsumerError.html().length > 0) {
		registerConsumerError.parent().removeClass('hide');
	}else{
		registerConsumerError.parent().addClass('hide');
	}
});
