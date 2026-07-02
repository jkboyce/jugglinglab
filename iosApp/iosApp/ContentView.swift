//
// ContentView.swift
//
// SwiftUI hosting view wrapping the Compose Multiplatform UIViewController.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Avoid drawing issues during keyboard shifts
            .onOpenURL { url in
                handleIncomingURL(url)
            }
            .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { userActivity in
                if let url = userActivity.webpageURL {
                    handleIncomingURL(url)
                }
            }
    }

    private func handleIncomingURL(_ url: URL) {
        if url.isFileURL {
            let isAccessing = url.startAccessingSecurityScopedResource()
            defer {
                if isAccessing {
                    url.stopAccessingSecurityScopedResource()
                }
            }
            do {
                let data = try Data(contentsOf: url)
                if let content = String(data: data, encoding: .utf8) {
                    MainViewControllerKt.handleJmlContent(content: content)
                } else {
                    MainViewControllerKt.handleImportError(message: "Failed to decode JML file as UTF-8.")
                }
            } catch {
                MainViewControllerKt.handleImportError(message: error.localizedDescription)
            }
        } else {
            MainViewControllerKt.handleUniversalLink(url: url.absoluteString)
        }
    }
}
