import XCTest

/**
 * Drives the real app in the simulator. Compose Multiplatform exposes its semantics as
 * accessibility elements, so texts and content descriptions are found by their label.
 *
 * Screenshots: every `shot(_:)` is attached to the test result (kept) and, if the environment
 * variable SCREENSHOT_DIR is set (xcodebuild: TEST_RUNNER_SCREENSHOT_DIR=…), written there as PNG.
 * The app runs in German (`-AppleLanguages (de)`), like the Android screenshots.
 */
class OverfitUITestCase: XCTestCase {
    var app: XCUIApplication!
    var suffix = "hell"

    override func setUp() {
        continueAfterFailure = false
    }

    func launch(theme: String, skipOnboarding: Bool = true, keepData: Bool = false) {
        suffix = theme == "dark" ? "dunkel" : "hell"
        app = XCUIApplication()
        app.launchArguments = ["-overfitUITest", "-overfitTheme", theme, "-AppleLanguages", "(de)", "-AppleLocale", "de_DE"]
        if skipOnboarding { app.launchArguments.append("-overfitSkipOnboarding") }
        if keepData { app.launchArguments.append("-overfitKeepData") }
        app.launch()
    }

    // MARK: Finding elements

    func elements(_ label: String) -> XCUIElementQuery {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label))
    }

    /** First element whose label is [label] (or starts with it), waiting up to [timeout] seconds. */
    @discardableResult
    func waitFor(_ label: String, timeout: TimeInterval = 20, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let exact = elements(label).firstMatch
        if exact.waitForExistence(timeout: timeout) { return exact }
        let prefix = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch
        if prefix.exists { return prefix }
        failShot("missing-\(label)")
        XCTFail("Nicht gefunden: \"\(label)\"\n\(app.debugDescription)", file: file, line: line)
        return exact
    }

    func exists(_ label: String) -> Bool { elements(label).firstMatch.exists }

    /** Element labelled exactly [label], else one whose label starts with it (merged rows, mini bar). */
    func find(_ label: String) -> XCUIElement {
        let exact = elements(label).firstMatch
        if exact.exists { return exact }
        return app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch
    }

    /** Taps the element labelled [label]; scrolls up to four screens down if it is not visible. */
    func tap(_ label: String, file: StaticString = #filePath, line: UInt = #line) {
        _ = elements(label).firstMatch.waitForExistence(timeout: 3)
        for _ in 0..<4 {
            let e = find(label)
            if e.exists && e.isHittable { e.tap(); return }
            app.swipeUp()
            sleep(1)
        }
        waitFor(label, timeout: 5, file: file, line: line).tap()
    }

    /** Taps a tab bar entry by its test tag (`tab:START`, `tab:HISTORY`, `tab:COACH`, `tab:PHOTOS`, `tab:PROFILE`). */
    func tab(_ id: String, file: StaticString = #filePath, line: UInt = #line) {
        let button = app.buttons["tab:\(id)"]
        for _ in 0..<3 where !button.waitForExistence(timeout: 5) {
            // Tab bar hidden on a pushed screen: go back first.
            if exists("Zurück") { elements("Zurück").firstMatch.tap() }
        }
        guard button.exists else { failShot("tab-\(id)"); XCTFail("Tab \(id) fehlt", file: file, line: line); return }
        button.tap()
    }

    // MARK: Screenshots

    func shot(_ name: String) {
        sleep(1) // let animations settle
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = "\(name)-\(suffix)"
        attachment.lifetime = .keepAlways
        add(attachment)
        if let dir = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"], !dir.isEmpty {
            try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
            try? screenshot.pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name)-\(suffix).png"))
        }
    }

    func failShot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "FEHLER-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

/** Screenshots of the main screens, light and dark (compare with docs/screenshots/after from Android). */
final class ScreenshotTests: OverfitUITestCase {
    func testOnboardingLight() { onboarding("light") }
    func testOnboardingDark() { onboarding("dark") }
    func testScreensLight() { screens("light") }
    func testScreensDark() { screens("dark") }

    /** App icon on the home screen and the launch screen (captured right after tapping the icon). */
    func testHomeScreenAndLaunch() {
        launch(theme: "light")
        waitFor("Legs")
        XCUIDevice.shared.press(.home)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let icon = springboard.icons["overfit"]
        for _ in 0..<4 where !(icon.exists && icon.isHittable) {
            springboard.swipeLeft()
            sleep(1)
        }
        suffix = "hell"
        shot("00-homescreen")
        guard icon.exists else { return }
        app.terminate() // cold start, so the launch screen shows
        icon.tap()
        usleep(150_000)
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "00-launch"
        attachment.lifetime = .keepAlways
        add(attachment)
        if let dir = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"], !dir.isEmpty {
            try? XCUIScreen.main.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("00-launch.png"))
        }
    }

    private func onboarding(_ theme: String) {
        launch(theme: theme, skipOnboarding: false)
        waitFor("Weiter")
        shot("00a-onboarding-name")
        app.typeText("Fabi")
        tap("Weiter")
        app.typeText("34")
        shot("00b-onboarding-alter")
        tap("Weiter")
        app.typeText("80")
        tap("Weiter")
        waitFor("Später einrichten")
        shot("00c-onboarding-ki")
        tap("Später einrichten")
        waitFor("Push") // sample workouts are there on first start
    }

    private func screens(_ theme: String) {
        launch(theme: theme)
        waitFor("Legs")
        shot("02-training")

        waitFor("Push").tap()
        waitFor("Starten")
        shot("03-workout-detail")
        tap("Teilen")
        waitFor("Link kopieren")
        shot("04-teilen")
        tap("Link kopieren") // for the import preview at the end
        tap("Zurück")

        tap("Starten")
        waitFor("Nächsten Satz loggen")
        shot("05-training-laufend")
        waitFor("Satz 1 abhaken").tap()
        waitFor("Überspringen")
        shot("06-training-pause")
        tap("Minimieren")
        waitFor("Zum laufenden Training")
        shot("06b-mini-leiste")
        tap("Training öffnen")
        tap("Überspringen")
        tap("Beenden")
        shot("07-beenden-bestaetigen")
        tap("Beenden? Nochmal tippen")
        waitFor("Training beendet")
        shot("08-beenden")
        tap("Speichern")
        waitFor("Duo · bald")

        tab("HISTORY")
        waitFor("Push")
        shot("09-verlauf")
        elements("Push").firstMatch.tap()
        waitFor("Teilen")
        shot("10-verlauf-detail")
        tap("Zurück")
        tab("COACH")
        sleep(1)
        shot("10b-coach")
        tap("Neuer Chat")
        waitFor("Nachricht an den Coach").tap()
        sleep(1)
        app.typeText("Wie trainiere ich morgen?")
        shot("10c-coach-chat-tastatur")
        tap("Zurück")
        tab("PHOTOS")
        waitFor("Foto-Tagebuch")
        shot("11-fotos")
        tab("PROFILE")
        shot("12-profil")
        tab("START")
        tap("Übungen")
        waitFor("Eigene Übung")
        shot("13-uebungskatalog")
        tap("Zurück")
        waitFor("Duo · bald")
        tap("Neues Workout")
        waitFor("Workout importieren")
        shot("14-neues-workout-menue")
        tap("Workout importieren")
        waitFor("Datei auswählen")
        shot("15-import")
        tap("Aus Zwischenablage importieren")
        waitFor("Übernehmen")
        shot("16-import-vorschau")
    }
}

/** Smoke test: start a workout, tick a set, minimize, reopen, finish. Runs in every iOS CI build. */
final class SmokeTests: OverfitUITestCase {
    func testWorkoutFlow() {
        launch(theme: "light")
        waitFor("Push").tap()
        tap("Starten")
        waitFor("Satz 1 abhaken").tap()
        waitFor("Überspringen")
        tap("Minimieren")
        waitFor("Zum laufenden Training")
        tap("Training öffnen")
        waitFor("Nächsten Satz loggen")
        tap("Beenden")
        tap("Beenden? Nochmal tippen")
        waitFor("Training beendet")
        tap("Speichern")
        tab("HISTORY")
        waitFor("Push")
    }

    /** iOS edge swipe = Android back: minimizes the workout, then leaves a pushed screen. */
    func testEdgeSwipeBack() {
        launch(theme: "light")
        waitFor("Push").tap()
        tap("Starten")
        waitFor("Nächsten Satz loggen")
        swipeFromLeftEdge()
        waitFor("Zum laufenden Training")
        tap("Übungen")
        waitFor("Eigene Übung")
        swipeFromLeftEdge()
        waitFor("Zum laufenden Training")
    }

    private func swipeFromLeftEdge() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    /** The running workout survives the app being killed. */
    func testWorkoutSurvivesRelaunch() {
        launch(theme: "light")
        waitFor("Push").tap()
        tap("Starten")
        waitFor("Satz 1 abhaken").tap()
        app.terminate()
        launch(theme: "light", keepData: true)
        waitFor("Nächsten Satz loggen")
    }
}
