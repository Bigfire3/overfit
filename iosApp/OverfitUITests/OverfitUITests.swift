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

    /** Hittable element labelled exactly [label], else one whose label starts with it (merged rows, mini bar). */
    func find(_ label: String) -> XCUIElement? {
        for query in [elements(label), app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", label))] {
            // During a screen transition the old screen's element can still be in the tree, but not hittable.
            if let e = query.allElementsBoundByIndex.first(where: { $0.exists && $0.isHittable }) { return e }
        }
        return nil
    }

    /** Taps the element labelled [label]; scrolls up to four screens down if it is not visible. */
    func tap(_ label: String, file: StaticString = #filePath, line: UInt = #line) {
        _ = elements(label).firstMatch.waitForExistence(timeout: 3)
        for attempt in 0..<6 {
            if let e = find(label) { e.tap(); return }
            if attempt < 2 { sleep(1) } else { app.swipeUp(); sleep(1) } // first wait for animations, then scroll
        }
        waitFor(label, timeout: 5, file: file, line: line)
        failShot("tap-\(label)")
        XCTFail("Nicht antippbar: \"\(label)\"", file: file, line: line)
    }

    /**
     * Confirm buttons open a popup (question, "Abbrechen", action): tap the button, screenshot the popup,
     * then tap the popup's action (test tag `dialog:confirm`, accessibility identifier on iOS).
     */
    func confirm(_ label: String, shotName: String? = nil, file: StaticString = #filePath, line: UInt = #line) {
        tap(label, file: file, line: line)
        let action = app.descendants(matching: .any).matching(identifier: "dialog:confirm").firstMatch
        if !action.waitForExistence(timeout: 5) {
            failShot("dialog-\(label)")
            XCTFail("Popup für \"\(label)\" fehlt\n\(app.debugDescription)", file: file, line: line)
            return
        }
        usleep(400_000)
        if let shotName { save(XCUIScreen.main.screenshot(), name: shotName) }
        action.tap()
    }

    /** Confirms the system notification prompt that appears on the first workout start. */
    func allowNotificationsIfAsked() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for title in ["Allow", "Erlauben"] {
            let button = springboard.alerts.buttons[title]
            if button.waitForExistence(timeout: 2) { button.tap(); return }
        }
    }

    /** Taps a tab bar entry by its test tag (`tab:START`, `tab:COACH`, `tab:PHOTOS` = Kalender, `tab:PROFILE`). */
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
        save(XCUIScreen.main.screenshot(), name: name)
    }

    func save(_ screenshot: XCUIScreenshot, name: String) {
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
        // Pressing home twice shows the first home screen page, where new apps land.
        XCUIDevice.shared.press(.home)
        sleep(1)
        XCUIDevice.shared.press(.home)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let icon = springboard.icons["overfit"]
        _ = icon.waitForExistence(timeout: 5)
        suffix = "hell"
        shot("00-homescreen")
        guard icon.exists && icon.isHittable else { return } // icon on another page: launch screen not captured
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
        allowNotificationsIfAsked()
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
        confirm("Beenden", shotName: "07-beenden-bestaetigen")
        waitFor("Training beendet")
        shot("08-beenden")
        tap("Speichern")
        waitFor("Legs")

        // No History tab any more: calendar → today → the day's training → detail
        tab("PHOTOS")
        waitFor("1 Training")
        shot("09-kalender")
        tap("Tag \(Calendar.current.component(.day, from: Date())), Training")
        waitFor("Push")
        shot("09b-kalender-tag")
        tap("Push")
        waitFor("Dauer")
        shot("10-training-detail")
        tap("Zurück")
        tap("Zurück")
        tab("COACH")
        // First visit of the Coach tab: the story opens by itself (full screen, no tab bar). Show it, then close it.
        let storyClose = app.buttons["story:close"]
        if storyClose.waitForExistence(timeout: 10) {
            shot("10a-coach-story")
            storyClose.tap()
        }
        sleep(1)
        shot("10b-coach")
        waitFor("Nachricht an den Coach").tap()
        sleep(1)
        app.typeText("Wie trainiere ich morgen?")
        shot("10c-coach-tastatur")
        // While the text field has focus, Compose's accessibility snapshot lists the text buttons
        // below it without labels; restart with the same data instead of searching "Senden".
        app.terminate()
        launch(theme: theme, keepData: true)
        waitFor("Legs")
        tab("PHOTOS")
        waitFor("1 Training") // calendar content, not the always visible tab label
        shot("11-kalender")
        tab("PROFILE")
        shot("12-profil")
        tab("START")
        tap("Übungen")
        waitFor("Eigene Übung")
        shot("13-uebungskatalog")
        tap("Zurück")
        waitFor("Legs")
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
        allowNotificationsIfAsked()
        waitFor("Satz 1 abhaken").tap()
        waitFor("Überspringen")
        tap("Minimieren")
        waitFor("Zum laufenden Training")
        tap("Training öffnen")
        waitFor("Nächsten Satz loggen")
        confirm("Beenden")
        waitFor("Training beendet")
        tap("Speichern")
        // The finished training shows in the calendar (no History tab any more)
        tab("PHOTOS")
        waitFor("1 Training")
    }

    /** iOS edge swipe = Android back: minimizes the workout, then leaves a pushed screen. */
    func testEdgeSwipeBack() {
        launch(theme: "light")
        waitFor("Push").tap()
        tap("Starten")
        allowNotificationsIfAsked()
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
        allowNotificationsIfAsked()
        waitFor("Satz 1 abhaken").tap()
        app.terminate()
        launch(theme: "light", keepData: true)
        waitFor("Nächsten Satz loggen")
    }
}
