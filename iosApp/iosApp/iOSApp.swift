//
// iOSApp.swift
//
// SwiftUI application entry point for the Juggling Lab iOS target.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

import SwiftUI
import FirebaseCore

@main
struct iOSApp: App {
    init() {
        // Firebase configuration file name
        let plistName = Bundle.main.bundleIdentifier == "org.jugglinglab.iosApp.debug"
            ? "GoogleService-Info-Debug"
            : "GoogleService-Info-Release"
        
        // Locate the file in the main app bundle
        guard let filePath = Bundle.main.path(forResource: plistName, ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: filePath) else {
            fatalError("Could not locate or load \(plistName).plist")
        }
        
        FirebaseApp.configure(options: options)
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
