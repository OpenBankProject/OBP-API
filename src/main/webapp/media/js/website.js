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
	$('#main-faq .collapse').click(function() {
		var answer = $(this).find('h2').next();
		if ($(this).attr("class").indexOf("minus") >= 0) {
			answer.hide();
			$(this).removeClass("minus").addClass("plus");
		} else {
			answer.show();
			$(this).removeClass("plus").addClass("minus");
		}
	});
});
