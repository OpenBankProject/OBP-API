$('#manual-redirect').hide(); //don't need the manual link if js is enabled
window.location.assign($('#redirect-link').attr('href'));