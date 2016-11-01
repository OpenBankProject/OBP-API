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


	// FAQ shenanigans
	$('#main-faq .collapse h2').click(function() {
		var answer = $(this).next();
		var listItem = $(this).parent();
		if (listItem.attr("class").indexOf("minus") >= 0) {
			answer.hide();
			listItem.removeClass("minus").addClass("plus");
		} else {
			answer.show();
			listItem.removeClass("plus").addClass("minus");
		}
	});


	// Enforce check of Terms and Conditions (if existing) on register form
	$('.signupSection #signupForm').submit(function() {
		var agreeTerms = $('.signupSection #signupForm #agree-terms-input');
		if (agreeTerms.length > 0) {
			if (!agreeTerms.attr('checked')) {
				var msg = 'Please agree to the Terms & Conditions';
				$('.signupSection .signup-error #signup').html(msg);
				return false;
			}
		}
		return true;
	});
});
