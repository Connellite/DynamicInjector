// Example script injected at the end of <body>
(function () {
    function nowTime() {
        return new Date().toLocaleTimeString();
    }

    console.log("[DynamicInjector] home.js injected at", nowTime());

    const marker = document.getElementById("di-marker");
    if (marker) {
        marker.textContent = "JS injected: " + nowTime();
    }
})();
