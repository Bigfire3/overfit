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
    var suffix = "dunkel"

    override func setUp() {
        continueAfterFailure = false
    }

    /** The app is always dark (no light mode); the simulator itself stays in its default light appearance. */
    func launch(skipOnboarding: Bool = true, keepData: Bool = false) {
        suffix = "dunkel"
        app = XCUIApplication()
        app.launchArguments = ["-overfitUITest", "-AppleLanguages", "(de)", "-AppleLocale", "de_DE"]
        if skipOnboarding { app.launchArguments.append("-overfitSkipOnboarding") }
        if keepData { app.launchArguments.append("-overfitKeepData") }
        app.launch()
    }

    // MARK: Finding elements

    func elements(_ label: String) -> XCUIElementQuery {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label))
    }

    /**
     * Elements labelled [label] or starting with it. Merged rows and buttons read as one label: the main workout button is
     * "Nächsten Satz loggen" plus its second line, the calendar figures "1 Training · 1 Woche in Folge".
     */
    func labelled(_ label: String) -> XCUIElementQuery {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR label BEGINSWITH %@", label, label))
    }

    /** Waits until [element] matches [format] (an NSPredicate on the element, e.g. "isEnabled == true"). */
    @discardableResult
    func wait(_ element: XCUIElement, until format: String, timeout: TimeInterval) -> Bool {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: format), object: element)
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
    }

    /**
     * First element whose label is [label] or starts with it, waiting up to [timeout] seconds for either (one query for
     * the whole time, so a merged label does not cost the full timeout first). The exact label wins when both exist.
     */
    @discardableResult
    func waitFor(_ label: String, timeout: TimeInterval = 20, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let match = labelled(label).firstMatch
        if match.waitForExistence(timeout: timeout) {
            let exact = elements(label).firstMatch
            return exact.exists ? exact : match
        }
        failShot("missing-\(label)")
        XCTFail("Nicht gefunden: \"\(label)\"\n\(app.debugDescription)", file: file, line: line)
        return match
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
        _ = labelled(label).firstMatch.waitForExistence(timeout: 3)
        for attempt in 0..<6 {
            if let e = find(label) { e.tap(); return }
            if attempt < 2 { sleep(1) } else { app.swipeUp(); sleep(1) } // first wait for animations, then scroll
        }
        waitFor(label, timeout: 5, file: file, line: line)
        failShot("tap-\(label)")
        XCTFail("Nicht antippbar: \"\(label)\"", file: file, line: line)
    }

    /**
     * Taps [label] once it is enabled. "Starten" is on screen from the first frame, but disabled until the workout has
     * loaded; a tap in that moment would be lost.
     */
    func tapWhenEnabled(_ label: String, file: StaticString = #filePath, line: UInt = #line) {
        let element = waitFor(label, file: file, line: line)
        if !wait(element, until: "isEnabled == true", timeout: 20) {
            failShot("disabled-\(label)")
            XCTFail("Bleibt deaktiviert: \"\(label)\"", file: file, line: line)
            return
        }
        tap(label, file: file, line: line)
    }

    /**
     * The armed confirm button: accessibility identifier `confirm:armed`; as a second sign its accessibility value, the
     * state description "zum Bestätigen erneut tippen" (both are only set while it is armed).
     */
    var armedConfirm: XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == %@ OR value == %@", "confirm:armed", "zum Bestätigen erneut tippen"))
            .firstMatch
    }

    /**
     * Confirm buttons use two taps: the first arms the button (it fills red/green and gets the identifier
     * `confirm:armed`), a second tap runs the action. No blind double tap: wait for the armed element, tap exactly
     * that element, then check that it is gone (the action ran).
     *
     * Under `-overfitUITest` the button stays armed for 30 s instead of 3 s (LaunchOptions.confirmArmedMillis): each
     * look at the accessibility tree takes XCUITest one to two seconds on CI, and Compose hands a changed tree to iOS
     * with a delay, so with 3 s the armed button was on screen but never seen by the test (iOS CI, 17.11.).
     */
    func confirm(_ label: String, shotName: String? = nil, file: StaticString = #filePath, line: UInt = #line) {
        tap(label, file: file, line: line)
        let armed = armedConfirm
        if !armed.waitForExistence(timeout: 15) {
            failShot("confirm-\(label)")
            XCTFail("\"\(label)\" wurde nicht scharf geschaltet\n\(app.debugDescription)", file: file, line: line)
            return
        }
        if let shotName { usleep(200_000); save(XCUIScreen.main.screenshot(), name: shotName) }
        armed.tap()
        if wait(armed, until: "exists == false", timeout: 8) { return }
        // Still armed after 8 s although it stays armed far longer: the tap did not arrive, tap it once more.
        if armed.exists { armed.tap() }
        if !wait(armed, until: "exists == false", timeout: 8) {
            failShot("confirm-still-armed-\(label)")
            XCTFail("\"\(label)\" wurde nicht bestätigt", file: file, line: line)
        }
    }

    /** Confirms the system notification prompt that appears on the first workout start. */
    func allowNotificationsIfAsked() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for title in ["Allow", "Erlauben"] {
            let button = springboard.alerts.buttons[title]
            if button.waitForExistence(timeout: 2) { button.tap(); return }
        }
    }

    /**
     * Taps a tab bar entry by its test tag (`tab:START`, `tab:COACH`, `tab:PHOTOS` = Kalender, `tab:PROFILE`) and checks
     * that it took: the tab is selected afterwards, or its bar is gone (the Coach tab opens its story on the first
     * visit). A tap that was lost (e.g. during a screen change) is repeated.
     */
    func tab(_ id: String, file: StaticString = #filePath, line: UInt = #line) {
        let button = app.buttons["tab:\(id)"]
        for _ in 0..<3 where !button.waitForExistence(timeout: 5) {
            // Tab bar hidden on a pushed screen: go back first.
            if exists("Zurück") { elements("Zurück").firstMatch.tap() }
        }
        guard button.exists else { failShot("tab-\(id)"); XCTFail("Tab \(id) fehlt", file: file, line: line); return }
        for _ in 0..<3 {
            button.tap()
            if wait(button, until: "exists == false OR isSelected == true", timeout: 3) { return }
        }
        failShot("tab-\(id)")
        XCTFail("Tab \(id) wurde nicht gewählt", file: file, line: line)
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

/** Screenshots of the main screens, dark only (compare with docs/screenshots/after from Android). */
final class ScreenshotTests: OverfitUITestCase {
    func testOnboardingDark() { onboarding() }
    func testScreensDark() { screens() }

    /** App icon on the home screen and the launch screen (captured right after tapping the icon). */
    func testHomeScreenAndLaunch() {
        launch()
        waitFor("Legs")
        // Pressing home twice shows the first home screen page, where new apps land.
        XCUIDevice.shared.press(.home)
        sleep(1)
        XCUIDevice.shared.press(.home)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let icon = springboard.icons["overfit"]
        _ = icon.waitForExistence(timeout: 5)
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

    private func onboarding() {
        launch(skipOnboarding: false)
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

    private func screens() {
        launch()
        waitFor("Legs")
        shot("02-training")

        waitFor("Push").tap()
        waitFor("Starten")
        shot("03-workout-detail")
        // Share sits in the ⋮ of the header since the UI audit (12.11.).
        tap("Weitere Optionen")
        tap("Teilen")
        waitFor("Link kopieren")
        shot("04-teilen")
        tap("Link kopieren") // for the import preview at the end
        tap("Zurück")

        tapWhenEnabled("Starten")
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
        // Coach tab = chat: three suggestions, input block at the bottom (placeholder "Frag deinen Coach …").
        waitFor("Wie lief mein Training?")
        waitFor("Frag deinen Coach").tap()
        sleep(1)
        app.typeText("Wie trainiere ich morgen?")
        shot("10c-coach-tastatur")
        // While the text field has focus, Compose's accessibility snapshot lists the text buttons
        // below it without labels; restart with the same data instead of searching "Senden".
        app.terminate()
        launch(keepData: true)
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
        waitFor("Speichern")
        shot("16-import-editor")
    }
}

/** Smoke test: start a workout, tick a set, minimize, reopen, finish. Runs in every iOS CI build. */
final class SmokeTests: OverfitUITestCase {
    func testWorkoutFlow() {
        launch()
        waitFor("Push").tap()
        tapWhenEnabled("Starten")
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
        launch()
        waitFor("Push").tap()
        tapWhenEnabled("Starten")
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
        launch()
        waitFor("Push").tap()
        tapWhenEnabled("Starten")
        allowNotificationsIfAsked()
        waitFor("Satz 1 abhaken").tap()
        app.terminate()
        launch(keepData: true)
        waitFor("Nächsten Satz loggen")
    }
}
