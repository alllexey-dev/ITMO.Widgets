import AVFoundation
import SwiftUI
import VisionKit

/// The QR scanner of the web sign-in: VisionKit's `DataScannerViewController`, QR codes only, full screen under a
/// close button and `ios_web_login_scan_hint`. Unlike Android's Play services scanner it needs the camera permission
/// (`NSCameraUsageDescription`, `ios_web_login_camera_usage`); a refused permission, a device without the scanner
/// (the simulator) or a scanner that does not start reads as `web_login_scanner_unavailable`, and typing still works.
enum WebLoginScanner {
    /// Asks for the camera once; true when the scanner can run now.
    @MainActor
    static func requestAccess(_ completion: @escaping @MainActor (Bool) -> Void) {
        guard DataScannerViewController.isSupported else {
            completion(false)
            return
        }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            completion(DataScannerViewController.isAvailable)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                Task { @MainActor in completion(granted && DataScannerViewController.isAvailable) }
            }
        default:
            completion(false)
        }
    }
}

/// The scanner above the sheet. `scanned` gets the first QR's text, whatever it holds: the ViewModel decides whether
/// it is a sign-in link.
struct WebLoginScannerScreen: View {
    let scanned: (String) -> Void
    let unavailable: () -> Void
    let close: () -> Void

    var body: some View {
        NavigationStack {
            WebLoginDataScanner(scanned: scanned, unavailable: unavailable)
                .ignoresSafeArea()
                .overlay(alignment: .bottom) {
                    Text(verbatim: AppStrings.string("ios_web_login_scan_hint"))
                        .font(.itmo(.bodyLarge))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white)
                        .padding(ItmoSpacing.content)
                        .background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: ItmoCorner.medium))
                        .padding(ItmoSpacing.section)
                }
                .navigationTitle(Text(verbatim: AppStrings.string("web_login_scan")))
                .navigationBarTitleDisplayMode(.inline)
                .toolbarBackground(.visible, for: .navigationBar)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(action: close) {
                            AppSymbol.close.image
                        }
                        .accessibilityLabel(Text(verbatim: AppStrings.string("common_close")))
                        .accessibilityIdentifier("webLogin.scanner.close")
                    }
                }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("webLogin.scanner")
    }
}

/// `DataScannerViewController` in SwiftUI; it starts scanning once on screen and reports the first QR once.
private struct WebLoginDataScanner: UIViewControllerRepresentable {
    let scanned: (String) -> Void
    let unavailable: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(scanned: scanned, unavailable: unavailable)
    }

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isPinchToZoomEnabled: true,
            isGuidanceEnabled: true,
            isHighlightingEnabled: true
        )
        scanner.delegate = context.coordinator
        return scanner
    }

    func updateUIViewController(_ scanner: DataScannerViewController, context: Context) {
        guard !scanner.isScanning, !context.coordinator.finished else { return }
        do {
            try scanner.startScanning()
        } catch {
            context.coordinator.fail()
        }
    }

    static func dismantleUIViewController(_ scanner: DataScannerViewController, coordinator: Coordinator) {
        scanner.stopScanning()
        scanner.delegate = nil
    }

    @MainActor
    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        private let scanned: (String) -> Void
        private let unavailable: () -> Void
        private(set) var finished = false

        init(scanned: @escaping (String) -> Void, unavailable: @escaping () -> Void) {
            self.scanned = scanned
            self.unavailable = unavailable
        }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            didAdd addedItems: [RecognizedItem],
            allItems: [RecognizedItem]
        ) {
            guard !finished else { return }
            for item in addedItems {
                if case let .barcode(barcode) = item, let payload = barcode.payloadStringValue {
                    finished = true
                    dataScanner.stopScanning()
                    scanned(payload)
                    return
                }
            }
        }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            becameUnavailableWithError error: DataScannerViewController.ScanningUnavailable
        ) {
            fail()
        }

        func fail() {
            guard !finished else { return }
            finished = true
            unavailable()
        }
    }
}
