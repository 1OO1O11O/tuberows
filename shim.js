/* TizenTube Home Test build: routes PublishHomeScreen through the patched ShowAlertDialog,
   standing in for the native method a real build adds. Not part of the TizenTube PR. */
(function () {
    try {
        var h = window.h5vcc, tt = h && h.tizentube;
        if (!tt || tt.PublishHomeScreen || !tt.HasSystemFeature || !tt.HasSystemFeature('tizentube.home_screen')) return;
        var shim = Object.create(tt);
        shim.PublishHomeScreen = function (json) { tt.ShowAlertDialog('tizentube:home', json); };
        ['InstallAppFromURL', 'GetVersion', 'GetArchitecture', 'SetFrameRate', 'EnterPIP', 'SetUserAgent', 'HasSystemFeature', 'ShowAlertDialog']
            .forEach(function (k) { if (typeof tt[k] === 'function') shim[k] = tt[k].bind(tt); });
        try { Object.defineProperty(h, 'tizentube', { value: shim, configurable: true }); } catch (e) { }
        if (h.tizentube !== shim) tt.PublishHomeScreen = shim.PublishHomeScreen;
        console.info('[HomeScreen] test shim active:', !!window.h5vcc.tizentube.PublishHomeScreen);
    } catch (e) { console.warn('[HomeScreen] test shim failed', e); }
})();
