import MapLibre
import Shared
import SwiftUI

/// The native plan (MapLibre iOS) of a `MapScene`: the shared style is written to a file and loaded once per building
/// and appearance (the dark plan in dark mode), the floor switches by the shared layer filters, the route and the
/// selection replace their GeoJSON, and a tap reports the plan room under it.
struct IndoorMapView: UIViewRepresentable {
    let scene: MapScene
    let tap: (KotlinLong?) -> Void
    @Environment(\.colorScheme) private var colorScheme

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MLNMapView {
        let view = MLNMapView(frame: .zero)
        // Until the first plan style loads the view would show MapLibre's default map or an empty white frame; the
        // screen's background shows through instead.
        view.alpha = 0
        view.delegate = context.coordinator
        view.minimumZoomLevel = 16
        view.maximumZoomLevel = 22
        // The plan's source is in the licences, not on the map (docs/features/map.md).
        view.logoView.isHidden = true
        view.attributionButton.isHidden = true
        // The plan turns with the building's bearing; a compass would sit under the search, and itmo-map-web has none.
        view.compassView.compassVisibility = .hidden
        let gesture = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tapped(_:)))
        view.gestureRecognizers?.compactMap { $0 as? UITapGestureRecognizer }.forEach { gesture.require(toFail: $0) }
        view.addGestureRecognizer(gesture)
        return view
    }

    func updateUIView(_ view: MLNMapView, context: Context) {
        context.coordinator.tap = tap
        context.coordinator.apply(scene, dark: colorScheme == .dark, to: view)
    }

    @MainActor
    final class Coordinator: NSObject, MLNMapViewDelegate {
        var tap: (KotlinLong?) -> Void = { _ in }
        private var styleKey: String?
        private var style: MLNStyle?
        private var scene: MapScene?
        private var focused: MapPoint?
        private var framed: [KotlinDouble]?
        /// Another building's style is loading: its camera is set again once it has loaded.
        private var fitPending = false
        private var fitted: Int32 = 0

        func apply(_ scene: MapScene, dark: Bool, to view: MLNMapView) {
            self.scene = scene
            let key = "\(scene.tilesPath)|\(dark)"
            if key != styleKey {
                let building = styleKey?.hasPrefix("\(scene.tilesPath)|") != true
                styleKey = key
                style = nil
                let url = FileManager.default.temporaryDirectory
                    .appendingPathComponent("campus-map-\(scene.buildingId)\(dark ? "-dark" : "").json")
                try? CampusMapStyle.shared.json(scene: scene, dark: dark).write(to: url, atomically: true, encoding: .utf8)
                view.styleURL = url
                // A theme switch keeps the camera; only another building is framed anew.
                if building {
                    fitPending = true
                    fit(scene, in: view)
                }
            }
            if let style { update(style) }
            if scene.fit != fitted {
                fitted = scene.fit
                focused = nil
                fit(scene, in: view, animated: true)
            }
            moveCamera(scene, in: view)
        }

        /// The route step's frame, else the focused room; each once, so a pan is not undone by the next update.
        private func moveCamera(_ scene: MapScene, in view: MLNMapView) {
            if let frame = scene.frame, frame != framed, frame.count == 4 {
                framed = frame
                let edges = frame.map { $0.doubleValue }
                let box = MLNCoordinateBounds(
                    sw: CLLocationCoordinate2D(latitude: edges[1], longitude: edges[0]),
                    ne: CLLocationCoordinate2D(latitude: edges[3], longitude: edges[2])
                )
                let camera = view.cameraThatFitsCoordinateBounds(box, edgePadding: UIEdgeInsets(top: 160, left: 64, bottom: 260, right: 64))
                camera.heading = scene.bearing
                // A short step (a stair) would zoom into one room with nothing around it: no closer than zoom 20,
                // as on Android.
                let closest = MLNAltitudeForZoomLevel(20, camera.pitch, camera.centerCoordinate.latitude, view.bounds.size)
                camera.altitude = max(camera.altitude, closest)
                view.setCamera(camera, withDuration: 0.4, animationTimingFunction: nil)
            } else if scene.frame == nil, let focus = scene.focus, focus != focused {
                focused = focus
                let camera = view.camera
                camera.centerCoordinate = CLLocationCoordinate2D(latitude: focus.latitude, longitude: focus.longitude)
                camera.heading = scene.bearing
                // One animation for the centre and the zoom: a second one (setZoomLevel) cancelled the move.
                let near = MLNAltitudeForZoomLevel(19.5, camera.pitch, focus.latitude, view.bounds.size)
                camera.altitude = min(camera.altitude, near)
                view.setCamera(camera, withDuration: 0.4, animationTimingFunction: nil)
            }
        }

        nonisolated func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            MainActor.assumeIsolated {
                self.style = style
                mapView.alpha = 1
                addSprites(to: style)
                update(style)
                // Loading another building's style may reset the camera: frame it and its room or step again.
                if fitPending, let scene {
                    fitPending = false
                    fit(scene, in: mapView)
                    focused = nil
                    framed = nil
                    moveCamera(scene, in: mapView)
                }
            }
        }

        @objc func tapped(_ gesture: UITapGestureRecognizer) {
            guard let view = gesture.view as? MLNMapView else { return }
            let layers = Set(CampusMapStyle.shared.TAP_LAYERS)
            let feature = view.visibleFeatures(at: gesture.location(in: view), styleLayerIdentifiers: layers).first
            let id = (feature?.attribute(forKey: "id") as? String).flatMap(Int64.init)
            tap(id.map { KotlinLong(value: $0) })
        }

        private func fit(_ scene: MapScene, in view: MLNMapView, animated: Bool = false) {
            let bounds = scene.bounds.map { $0.doubleValue }
            guard bounds.count == 4 else { return }
            let box = MLNCoordinateBounds(
                sw: CLLocationCoordinate2D(latitude: bounds[1], longitude: bounds[0]),
                ne: CLLocationCoordinate2D(latitude: bounds[3], longitude: bounds[2])
            )
            let camera = view.cameraThatFitsCoordinateBounds(box, edgePadding: UIEdgeInsets(top: 48, left: 48, bottom: 48, right: 48))
            camera.heading = scene.bearing
            view.setCamera(camera, animated: animated)
        }

        private func update(_ style: MLNStyle) {
            guard let scene else { return }
            let selected = scene.selectedRoomId
            for (id, filter) in CampusMapStyle.shared.filters(level: scene.level, selectedRoomId: selected) {
                guard let layer = style.layer(withIdentifier: id) as? MLNVectorStyleLayer,
                      let data = filter.data(using: .utf8),
                      let json = try? JSONSerialization.jsonObject(with: data) else { continue }
                layer.predicate = NSPredicate(mglJSONObject: json)
            }
            shape(scene.route, of: CampusMapStyle.shared.ROUTE_SOURCE, in: style)
            shape(scene.selection, of: CampusMapStyle.shared.SELECTION_SOURCE, in: style)
        }

        /// The campus app's icons from the bundle's `map_sprites`, drawn for a pixel ratio of 3.
        private func addSprites(to style: MLNStyle) {
            let scale = CGFloat(CampusMapStyle.shared.SPRITE_PIXEL_RATIO)
            for name in CampusMapStyle.shared.SPRITES {
                guard let url = Bundle.main.url(forResource: name, withExtension: "png", subdirectory: "map_sprites"),
                      let data = try? Data(contentsOf: url),
                      let image = UIImage(data: data, scale: scale) else { continue }
                style.setImage(image, forName: name)
            }
        }

        private func shape(_ geoJson: String, of source: String, in style: MLNStyle) {
            guard let source = style.source(withIdentifier: source) as? MLNShapeSource,
                  let shape = try? MLNShape(data: Data(geoJson.utf8), encoding: String.Encoding.utf8.rawValue) else { return }
            source.shape = shape
        }
    }
}
