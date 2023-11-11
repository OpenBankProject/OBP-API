// holds the idle duration in ms (current value = 2 minutes)
var timeoutInterval = 120000;
// holds the timeout variables for easy destruction and reconstruction of the setTimeout hooks
var timeHook = null;

function initializeTimeHook() {
    // this method has the purpose of creating our timehooks and scheduling the call to our logout function when the idle time has been reached
    if (timeHook == null) {
        timeHook = setTimeout( function () { destroyTimeHook(); logout(); }.bind(this), timeoutInterval);
    }
}

function destroyTimeHook() {
    // this method has the sole purpose of destroying any time hooks we might have created
    clearTimeout(timeHook);
    timeHook = null;
}

function resetTimeHook() {
    // this method replaces the current time hook with a new time hook
    destroyTimeHook();
    initializeTimeHook();
    console.log("Reset inactivity of a user");
}

function setupListeners() {
    // here we setup the event listener for the mouse click operation
    document.addEventListener("click", function () { resetTimeHook(); }.bind(this));
    document.addEventListener("mousemove", function () { resetTimeHook(); }.bind(this));
    document.addEventListener("mousedown", function () { resetTimeHook(); }.bind(this));
    document.addEventListener("keypress", function () { resetTimeHook(); }.bind(this));
    document.addEventListener("touchmove", function () { resetTimeHook(); }.bind(this));
    console.log("Listeners for user inactivity activated");
}

function destroyListeners() {
    // here we destroy event listeners for the mouse click operation
    document.removeEventListener("click", function () { resetTimeHook(); }.bind(this));
    document.removeEventListener("mousemove", function () { resetTimeHook(); }.bind(this));
    document.removeEventListener("mousedown", function () { resetTimeHook(); }.bind(this));
    document.removeEventListener("keypress", function () { resetTimeHook(); }.bind(this));
    document.removeEventListener("touchmove", function () { resetTimeHook(); }.bind(this));
    console.log("Listeners for user inactivity deactivated");
}

function logout() {
    const elem = document.getElementById("loggedIn-username");
    if(elem) {
        location.href = '/user_mgt/logout';
        destroyListeners();
        console.log("Logging you out due to inactivity..");
    }
}

// self executing function to trigger the operation on page load
(function () {
    // to prevent any lingering timeout handlers preventing memory leaks
    destroyTimeHook();
    // setup a fresh time hook
    initializeTimeHook();
    // setup initial event listeners
    setupListeners();
})();