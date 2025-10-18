(function() {
    'use strict';

    const tokenUrl = 'https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/token';

    function processResponse(responseText) {
        try {
            const data = JSON.parse(responseText);

            if (window.AndroidApp) {
                console.log('Intercepted tokens response. Sending to Android app.');
                window.AndroidApp.postTokens(responseText);
            }
        } catch (e) {
            console.error('Interceptor: Error processing response text.', e);
        }
    }

    const originalXhrOpen = XMLHttpRequest.prototype.open;
    const originalXhrSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function(method, url, ...args) {
        this._url = url;
        return originalXhrOpen.apply(this, [method, url, ...args]);
    };

    XMLHttpRequest.prototype.send = function(...args) {
        this.addEventListener('load', function() {
            if (this._url === tokenUrl) {
                console.log('Interceptor: Matched XMLHttpRequest to:', this._url);
                processResponse(this.responseText);
            }
        });

        return originalXhrSend.apply(this, args);
    };

    console.log('XHR Interceptor is now active.');
})();