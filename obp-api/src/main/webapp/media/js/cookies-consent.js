//remove the Div by its id from the page and save the 'we-use-cookies-indicator' cookie
function removeByIdAndSaveIndicatorCookie(id){
  document.getElementById(id).style.display="none";
  //set the 'we-use-cookies-indicator' cookie expire time to one year 
  addCookie('we-use-cookies-indicator','1',1);
}

//add the cookie with cookie name, value and expiration interval time (year)
function addCookie(cookieName, cookieValue, expirationIntervalYears) {
    var cookieCompleteString = cookieName + "=" + escape(cookieValue);
    if(expirationIntervalYears > 0){
        var date = new Date();
        var expirationInterval = expirationIntervalYears*3600*1000*24*365;
        date.setTime(date.getTime() + expirationInterval);
        cookieCompleteString += "; expires=" + date.toGMTString();
    }
    document.cookie = cookieCompleteString;
}

//get the cookie value by name
function getCookieValue(cookieName) {
    var cookies = document.cookie.split("; ");
    for(var i = 0;i < cookies.length;i ++){
        var cookieFields = cookies[i].split("=");
        if(cookieFields[0] == cookieName) return unescape(cookieFields[1]);
    }
}

//show the cookie page when the cookie value is different with default value
function showIndicatorCookiePage(id)
{
    var cookieValue = getCookieValue('we-use-cookies-indicator');
    //if the value of 'we-use-cookies-indicator' is not the same as we set before (set it '1' before). 
    if (cookieValue != '1')
    {
        //show the cookie page
        document.getElementById(id).style.display="block";
    }
}