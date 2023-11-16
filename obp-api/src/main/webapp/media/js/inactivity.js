import * as countdownTimer from './inactivity-timer.js'

// holds the idle duration in ms (current value = 301 seconds)
var timeoutInterval = 5 * 60 * 1000 + 1000;
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

function resetTimeHook(event) {
    // this method replaces the current time hook with a new time hook
    destroyTimeHook();
    initializeTimeHook();
    countdownTimer.resetCountdownTimer(timeoutInterval / 1000);
    // show event type, element and coordinates of the click
    // console.log(event.type + " at " + event.currentTarget);
    // console.log("Coordinates: " + event.clientX + ":" + event.clientY);
    console.log("Reset inactivity of a user");
}

function setupListeners() {
    // here we setup the event listener for the mouse click operation
    document.addEventListener("click", resetTimeHook);
    document.addEventListener("mousemove", resetTimeHook);
    document.addEventListener("mousedown", resetTimeHook);
    document.addEventListener("keypress", resetTimeHook);
    document.addEventListener("touchmove", resetTimeHook);
    console.log("Listeners for user inactivity activated");
}

function destroyListeners() {
    // here we destroy event listeners for the mouse click operation
    document.removeEventListener("click", resetTimeHook);
    document.removeEventListener("mousemove", resetTimeHook);
    document.removeEventListener("mousedown", resetTimeHook);
    document.removeEventListener("keypress", resetTimeHook);
    document.removeEventListener("touchmove", resetTimeHook);
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