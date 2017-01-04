//Remove the Div by its id from the page and save the cookie
function removeById(id){
  document.getElementById(id).style.display="none";
  addCookie('we-use-cookies-indicator','1',12);
}

//add the cookie with cookie name, value and expire time (hour)
function addCookie(objName,objValue,objHours) {
    var str = objName + "=" + escape(objValue);
    if(objHours > 0){
        var date = new Date();
        var ms = objHours*3600*1000;
        date.setTime(date.getTime() + ms);
        str += "; expires=" + date.toGMTString();
    }
    document.cookie = str;
}

//get the cookie value by name
function getCookie(objName) {
    var arrStr = document.cookie.split("; ");
    for(var i = 0;i < arrStr.length;i ++){
        var temp = arrStr[i].split("=");
        if(temp[0] == objName) return unescape(temp[1]);
    }
}

//show the cookie page when the cookie value is different with default value
function showUsesCookiePage(id)
{
    var cookieValue = getCookie('we-use-cookies-indicator');
    //if the value of 'we-use-cookies-indicator' is not the same as we set before (set it '1' before). 
    if (cookieValue != '1')
    {
        //show the cookie page
        document.getElementById(id).style.display="block";
    }
}