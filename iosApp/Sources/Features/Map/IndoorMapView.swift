import MapLibre
import Shared
import SwiftUI

/// The native plan (MapLibre iOS) of a `MapScene`: the shared style is written to a file and loaded once per building
/// and appearance, the floor switches by the shared layer filters, the route and the selection replace their GeoJSON,
/// and a tap reports the plan room under it.
struct IndoorMapView: UIViewRepresentable {
    let scene: MapScene
    let tap: (KotlinLong?) -> Void

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MLNMapView {
        let view = MLNMapView(frame: .zero)
        view.delegate = context.coordinator
        view.minimumZoomLevel = 16
        view.maximumZoomLevel = 22
        // The plan's source is in the licences, not on the map (docs/features/map.md).
        view.logoView.isHidden = true
        view.attributionButton.isHidden = true
        let gesture = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tapped(_:)))
        view.gestureRecognizers?.compactMap { $0 as? UITapGestureRecognizer }.forEach { gesture.require(toFail: $0) }
        view.addGestureRecognizer(gesture)
        return view
    }

    func updateUIView(_ view: MLNMapView, context: Context) {
        context.coordinator.tap = tap
        context.coordinator.apply(scene, to: view)
    }

    @MainActor
    final class Coordinator: NSObject, MLNMapViewDelegate {
        var tap: (KotlinLong?) -> Void = { _ in }
        private var styleKey: String?
        private var style: MLNStyle?
        private var scene: MapScene?
        private var focused: MapPoint?
        private var framed: [KotlinDouble]?

        func apply(_ scene: MapScene, to view: MLNMapView) {
            self.scene = scene
            let key = scene.tilesPath
            if key != styleKey {
                styleKey = key
                style = nil
                let url = FileManager.default.temporaryDirectory.appendingPathComponent("campus-map-\(scene.buildingId).json")
                try? CampusMapStyle.shared.json(scene: scene).write(to: url, atomically: true, encoding: .utf8)
                view.styleURL = url
                fit(scene, in: view)
            }
            if let style { update(style) }
            if let frame = scene.frame, frame != framed, frame.count == 4 {
                framed = frame
                let edges = frame.map { $0.doubleValue }
                let box = MLNCoordinateBounds(
                    sw: CLLocationCoordinate2D(latitude: edges[1], longitude: edges[0]),
                    ne: CLLocationCoordinate2D(latitude: edges[3], longitude: edges[2])
                )
                let camera = view.cameraThatFitsCoordinateBounds(box, edgePadding: UIEdgeInsets(top: 160, left: 64, bottom: 260, right: 64))
                camera.heading = scene.bearing
                view.setCamera(camera, withDuration: 0.4, animationTimingFunction: nil)
            } else if scene.frame == nil, let focus = scene.focus, focus != focused {
                focused = focus
                let camera = view.camera
                camera.centerCoordinate = CLLocationCoordinate2D(latitude: focus.latitude, longitude: focus.longitude)
                camera.heading = scene.bearing
                view.setCamera(camera, withDuration: 0.4, animationTimingFunction: nil)
                if view.zoomLevel < 19.5 { view.setZoomLevel(19.5, animated: true) }
            }
        }

        nonisolated func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            MainActor.assumeIsolated {
                self.style = style
                addSprites(to: style)
                update(style)
            }
        }

        @objc func tapped(_ gesture: UITapGestureRecognizer) {
            guard let view = gesture.view as? MLNMapView else { return }
            let layers = Set(CampusMapStyle.shared.TAP_LAYERS)
            let feature = view.visibleFeatures(at: gesture.location(in: view), styleLayerIdentifiers: layers).first
            let id = (feature?.attribute(forKey: "id") as? String).flatMap(Int64.init)
            tap(id.map { KotlinLong(value: $0) })
        }

        private func fit(_ scene: MapScene, in view: MLNMapView) {
            let bounds = scene.bounds.map { $0.doubleValue }
            guard bounds.count == 4 else { return }
            let box = MLNCoordinateBounds(
                sw: CLLocationCoordinate2D(latitude: bounds[1], longitude: bounds[0]),
                ne: CLLocationCoordinate2D(latitude: bounds[3], longitude: bounds[2])
            )
            let camera = view.cameraThatFitsCoordinateBounds(box, edgePadding: UIEdgeInsets(top: 48, left: 48, bottom: 48, right: 48))
            camera.heading = scene.bearing
            view.setCamera(camera, animated: false)
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
