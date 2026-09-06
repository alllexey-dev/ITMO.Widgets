(function() {
    'use strict';

    const tokenUrl = 'https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/token';

    function processResponse(responseText) {
        try {
            const data = JSON.parse(responseText);

            if (window.ItmoAuthBridge && data.access_token && data.refresh_token && data.id_token) {
                window.ItmoAuthBridge.postTokens(responseText);
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
                processResponse(this.responseText);
            }
        });

        return originalXhrSend.apply(this, args);
    };

})();
