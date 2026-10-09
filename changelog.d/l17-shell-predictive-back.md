# Navigation

- The app opts into predictive back on Android 13-15
  (`android:enableOnBackInvokedCallback` on the application), as Android 16
  already does for targetSdk 36. On Android 14 and later the back gesture
  previews closing an overlay screen or a sheet and follows the finger until
  release; on the start tab with nothing open it shows the system's
  back-to-home animation. My ITMO in the browser goes back in the web history
  first and previews closing the screen once there is none.
