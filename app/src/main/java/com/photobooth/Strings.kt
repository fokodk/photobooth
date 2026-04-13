package com.photobooth

/**
 * Multi-language string resources.
 */
object Strings {

    data class Lang(
        val tapForPhoto: String,
        val tapAnywhere: String,
        val photosTaken: String,
        val getInPosition: String,
        val getReady: String,
        val smile: String,
        val takingPhoto: String,
        val saved: String,
        val scanToDownload: String,
        val connectHotspot: String,
        val shareNearby: String,
        val newPhoto: String,
        val done: String,
        val oops: String,
        val tryAgain: String,
        val connectCamera: String,
        val captureFailed: String,
        val settings: String,
        val language: String,
        val watermark: String,
        val watermarkText: String,
        val watermarkEnabled: String,
        val countdown: String,
        val prepareTime: String,
        val mirrorLiveView: String,
        val seconds: String,
        val back: String,
    )

    val da = Lang(
        tapForPhoto = "Tryk for foto",
        tapAnywhere = "Tryk hvor som helst",
        photosTaken = "billeder taget",
        getInPosition = "Find din plads!",
        getReady = "G\u00f8r dig klar...",
        smile = "Smiiil!",
        takingPhoto = "Tager billede...",
        saved = "Gemt!",
        scanToDownload = "Scan for at hente billedet",
        connectHotspot = "Forbind til tabletens hotspot f\u00f8rst",
        shareNearby = "Del via Nearby Share",
        newPhoto = "Nyt foto",
        done = "F\u00e6rdig",
        oops = "Ups!",
        tryAgain = "Pr\u00f8v igen",
        connectCamera = "Tilslut kameraet via USB og tryk pr\u00f8v igen",
        captureFailed = "Billedet kunne ikke tages",
        settings = "Indstillinger",
        language = "Sprog",
        watermark = "Vandm\u00e6rke",
        watermarkText = "Vandm\u00e6rke tekst",
        watermarkEnabled = "Vis vandm\u00e6rke",
        countdown = "Nedt\u00e6lling",
        prepareTime = "Forberedelsestid",
        mirrorLiveView = "Spejl live view",
        seconds = "sekunder",
        back = "Tilbage",
    )

    val en = Lang(
        tapForPhoto = "Tap for photo",
        tapAnywhere = "Tap anywhere to start",
        photosTaken = "photos taken",
        getInPosition = "Get in position!",
        getReady = "Get ready...",
        smile = "Smile!",
        takingPhoto = "Taking photo...",
        saved = "Saved!",
        scanToDownload = "Scan to download photo",
        connectHotspot = "Connect to tablet hotspot first",
        shareNearby = "Share via Nearby Share",
        newPhoto = "New photo",
        done = "Done",
        oops = "Oops!",
        tryAgain = "Try again",
        connectCamera = "Connect camera via USB and tap retry",
        captureFailed = "Could not take photo",
        settings = "Settings",
        language = "Language",
        watermark = "Watermark",
        watermarkText = "Watermark text",
        watermarkEnabled = "Show watermark",
        countdown = "Countdown",
        prepareTime = "Prepare time",
        mirrorLiveView = "Mirror live view",
        seconds = "seconds",
        back = "Back",
    )

    fun get(lang: String): Lang = when (lang) {
        "en" -> en
        else -> da
    }
}
