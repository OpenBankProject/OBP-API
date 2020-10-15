//This function to make sure in the big screen, to close the left_side_small_screen div, then we can show the api_list 
var flag = true;
$(window).resize(function() {
	if(screen.width < 759 && !flag){
		flag = true
	}
	if(screen.width >= 759 && flag){
		closeNav()
		flag =false
	}
});

function checkclick(){
	if($("#agree").is(':checked') ){
		$("#agree").attr("checked","unchecked");
		$("#checkimg").css("background-image","none");
	} else{
		$("#agree").attr("checked","checked");
		$("#checkimg").css("background-image","url(/media/images/icons/status_yes_thick_00847F.svg)");
		$("#checkimg").css("background-size","18px");
		$("#checkimg").css("background-repeat","no-repeat");
	}
}
function openNav() {
	$("#obp-sidebar").css("display","block");
	$("#main").css("display","none");
	logOnButton = $("#small-nav-log-on-button").text().indexOf("Log on")
	if (logOnButton < 0){
		$("#register-link").css("display","none")
	}
	
}

function closeNav() {
	$("#obp-sidebar").css("display","none");
	$("#main").css("display","block");
}

function mouseClick(element) {
	// Check to see if the button is pressed
	var pressed = (element.getAttribute("aria-expanded") === "true");
	// Change aria-pressed to the opposite state
	element.setAttribute("aria-expanded", !pressed);
}

function mouseClickMainMaq(element) {
	
	// Check to see if the button is pressed
	var pressed = (element.firstElementChild.getAttribute("aria-expanded") === "true");
	// Change aria-pressed to the opposite state
	if(pressed){
		element.firstElementChild.children[3].setAttribute("src","/media/images/icons/chevron_down_thick.svg")
	}else{
		element.firstElementChild.children[3].setAttribute("src","/media/images/icons/chevron_up_thick.svg")
	}
}


function EnterKeyPressed(element) {
	// Check to see if space or enter were pressed
	if (event.key === "Enter" ) {
		// Check to see if the button is pressed
		var pressed = (element.getAttribute("aria-expanded") === "false");
		// Change aria-pressed to the opposite state
		console.log( !pressed)
		element.setAttribute("aria-expanded", !pressed);
	}
}

$(document).ready(function() {

	//if the small-nav-log-on-button do not contains any value, just set it to none
	if($("#small-nav-log-on-button").text().length < 15){
		$("#small-nav-log-on-button").css("width","24px");
		$("#small-screen-navbar #small-nav-log-on-button").css("width","24px");
	}
	//if the small-nav-log-on-button do not contains any value, just set it to none
	if($("#small-nav-log-on-button").text().length > 15){
		$("#small-nav-log-on-button").css("width","63px");
		$("#small-screen-navbar #small-nav-log-on-button").css("width","63px");
	}
	
	$(".main-support-item .support-platform-link").text("chat.openbankproject.com");
	
	
	var htmlTitle = $(document).find("title").text();

	if (htmlTitle.indexOf("Get API") > -1){
		$("#get-api-key-link").css("border-bottom","4px #53C4EF solid").css("padding-bottom","26px");
		$("#obp-sidebar #sideba-api-key-div").css("border-left","4px #53C4EF solid").css("font-weight", "bold");
	}else if(htmlTitle.indexOf("INTRODUCTION") >-1){
		$("#sandbox-introduction-link").css("border-bottom","4px #53C4EF solid").css("padding-bottom","26px");
		$("#obp-sidebar #sandbox-introduction-link").parent().css("border-left","4px #53C4EF solid").css("font-weight", "bold");
	}else{
		;
	}
	
    $('.js-example-basic-single').select2();
	$("#select2-appType-container").attr("aria-labelledby","appTypeLabel");
	$("#appType").removeAttr("tabindex").removeAttr("aria-hidden");
    
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

	var agreeTermsCheckbox= $("#signup #signup-agree-terms #check_border")
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
				$('#signup #signup-general-error #error').html(msg);
				$('#signup #signup-general-error').removeClass('hide');
				return false;
			}
		}
		return true;
	});

	// Show sign up errors - FIXME: Change backend to (not) show errors
	var signupError = $('#signup #signup-error #authuser_firstName');
	var txtFirstName = $('#signup #txtFirstName');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		txtFirstName.css("border","1px solid #A8000B").css("background","#F9F2F3")
		agreeTermsCheckbox.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}
	var signupError = $('#signup #signup-error #authuser_lastName');
	var txtLastName = $('#signup #txtLastName');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		txtLastName.css("border","1px solid #A8000B").css("background","#F9F2F3")
		agreeTermsCheckbox.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}
	var signupError = $('#signup #signup-error #authuser_email');
	var txtEmail = $('#signup #txtEmail');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		txtEmail.css("border","1px solid #A8000B").css("background","#F9F2F3")
		agreeTermsCheckbox.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}
	var signupError = $('#signup #signup-error #authuser_username');
	var txtUsername = $('#signup #txtUsername');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		txtUsername.css("border","1px solid #A8000B").css("background","#F9F2F3")
		agreeTermsCheckbox.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}
	var signupError = $('#signup #signup-error #authuser_password');
	var signupErrorRepeat = $('#signup #signup-error #authuser_password_repeat');
	var textPassword = $('#signup #textPassword');
	var textPasswordRepeat = $('#signup #textPasswordRepeat');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		signupErrorRepeat.parent().removeClass('hide');
		textPassword.css("border","1px solid #A8000B").css("background","#F9F2F3")
		textPasswordRepeat.css("border","1px solid #A8000B").css("background","#F9F2F3")
		agreeTermsCheckbox.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}

	var loginUsernameError = $('#authorise #login-form-username-error');
	var loginUsernameForm = $('#authorise #username');
	if (loginUsernameError.length > 0 && loginUsernameError.html().length > 0) {
		loginUsernameError.parent().removeClass('hide');
		loginUsernameForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	} else{
		loginUsernameError.parent().addClass('hide');
		loginUsernameForm.css("border","").css("background","")
	}

	var loginPasswordError = $('#authorise #login-form-password-error');
	var loginPasswordForm = $('#authorise #password');
	if (loginPasswordError.length > 0 && loginPasswordError.html().length > 0) {
		loginPasswordError.parent().removeClass('hide');
		loginPasswordForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}else{
		loginPasswordError.parent().addClass('hide');
		loginPasswordForm.css("border","").css("background","")
	}

	var consumerRegistrationAppnameError = $('#register-consumer-input #consumer-registration-app-name-error');
	var consumerRegistrationAppnameForm = $('#register-consumer-input #appName');
	if (consumerRegistrationAppnameError.length > 0 && consumerRegistrationAppnameError.html().length > 0) {
		consumerRegistrationAppnameError.parent().removeClass('hide');
		consumerRegistrationAppnameForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}else{
		consumerRegistrationAppnameError.parent().addClass('hide');
		consumerRegistrationAppnameForm.css("border","").css("background","")
	}

	var consumerRegistrationAppDeveloperError = $('#register-consumer-input #consumer-registration-app-developer-error');
	var consumerRegistrationAppDeveloperForm = $('#register-consumer-input #appDev');
	if (consumerRegistrationAppDeveloperError.length > 0 && consumerRegistrationAppDeveloperError.html().length > 0) {
		consumerRegistrationAppDeveloperError.parent().removeClass('hide');
		consumerRegistrationAppDeveloperForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}else{
		consumerRegistrationAppDeveloperError.parent().addClass('hide');
		consumerRegistrationAppDeveloperForm.css("border","").css("background","")
	}

	var consumerRegistrationAppDescError = $('#register-consumer-input #consumer-registration-app-description-error');
	var consumerRegistrationAppDescForm = $('#register-consumer-input #appDesc');
	if (consumerRegistrationAppDescError.length > 0 && consumerRegistrationAppDescError.html().length > 0) {
		consumerRegistrationAppDescError.parent().removeClass('hide');
		consumerRegistrationAppDescForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}else{
		consumerRegistrationAppDescError.parent().addClass('hide');
		consumerRegistrationAppDescForm.css("border","").css("background","")
	}

	var consumerRegistrationAppRedirectUrlError = $('#register-consumer-input #consumer-registration-app-description-error');
	var consumerRegistrationAppRedirectUrlForm = $('#register-consumer-input #appDesc');
	if (consumerRegistrationAppRedirectUrlError.length > 0 && consumerRegistrationAppRedirectUrlError.html().length > 0) {
		consumerRegistrationAppRedirectUrlError.parent().removeClass('hide');
		consumerRegistrationAppRedirectUrlForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}else{
		consumerRegistrationAppRedirectUrlError.parent().addClass('hide');
		consumerRegistrationAppRedirectUrlForm.css("border","").css("background","")
	}

	var registerConsumerError = $('#register-consumer-input #register-consumer-errors');
	if (registerConsumerError.length > 0 && registerConsumerError.html().length > 0) {
		registerConsumerError.parent().removeClass('hide');
	}else{
		registerConsumerError.parent().addClass('hide');
	}
	
	showIndicatorCookiePage('cookies-consent');
});
