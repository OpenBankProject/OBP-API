import * as countdownTimer from './inactivity-timer.js'

// holds the idle duration in ms (current value = 5 minutes)
var timeoutInterval = 5 * 60 * 1000;
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
    countdownTimer.resetCountdownTimer(timeoutInterval / 1000);
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
    destroyListeners();
    countdownTimer.destroyCountdownTimer();
    console.log("Logging you out due to inactivity..");
    location.href = '/user_mgt/logout';
}

// self executing function to trigger the operation on page load
(function () {
    const elem = document.getElementById("loggedIn-username");
    if(elem) {
        // to prevent any lingering timeout handlers preventing memory leaks
        destroyTimeHook();
        // setup a fresh time hook
        initializeTimeHook();
        // setup initial event listeners
        setupListeners();
        // Reset countdown timer
        countdownTimer.resetCountdownTimer(timeoutInterval / 1000);
    }
})();