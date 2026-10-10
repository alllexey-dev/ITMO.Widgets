package dev.alllexey.itmowidgets.core.platform

/**
 * What iOS offers of [PlatformCapabilities]: a feature turns on with the IO card that ships it, so until then its
 * entry points stay hidden (App Review 2.1). Never on iOS: the quick settings tile, Android's battery and Xiaomi
 * background screens, the GitHub update channel, the animated QR widget and the custom spoiler image.
 *
 * - [PlatformCapabilities.recordbook]: on since IO-09d2 (the tab, the subject page and `Мои баллы`);
 *   [PlatformCapabilities.marks]: on since IO-09d3 (the background mark check, the settings recordbook page and the
 *   new-marks home card).
 * - [PlatformCapabilities.calendarExport]: on since IO-15b, the settings calendar rows over EventKit and the `.ics`
 *   sheet.
 * - [PlatformCapabilities.reviews]: on since IO-09f, the teacher reviews on the profile with their editor and report,
 *   the subject page's links with all links, the link editor, a link's actions and its report, and the teacher tones.
 */
val IosPlatformCapabilities = PlatformCapabilities(
    quickSettingsTile = false,
    backgroundWorkSettings = false,
    updateChannel = false,
    calendarExport = true,
    recordbook = true,
    marks = true,
    reviews = true,
    qrWidgetAnimation = false,
    qrCustomSpoiler = false,
    wallpaperColors = false,
)
