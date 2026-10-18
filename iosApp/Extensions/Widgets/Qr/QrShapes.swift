import SwiftUI

/// The dark modules of a QR code, drawn as Android's `QrBitmapRenderer` draws them: a module's outer corner is
/// rounded where it has no dark neighbour on either side, and a light module's corner between three dark ones is
/// filled with a concave fillet. One path, so neighbouring modules show no seams.
struct QrModulesShape: Shape {
    /// Rows from the top, modules from the left; `true` is dark. Square (`QrPassSnapshot` rejects anything else).
    let matrix: [[Bool]]

    func path(in rect: CGRect) -> Path {
        let count = matrix.count
        guard count > 0 else { return Path() }
        let side = min(rect.width, rect.height)
        let module = side / CGFloat(count)
        let radius = module / 2
        let origin = CGPoint(x: rect.midX - side / 2, y: rect.midY - side / 2)

        func dark(_ x: Int, _ y: Int) -> Bool {
            y >= 0 && y < count && x >= 0 && x < matrix[y].count && matrix[y][x]
        }

        var path = Path()
        for y in 0..<count {
            for x in 0..<count {
                let cell = CGRect(
                    x: origin.x + CGFloat(x) * module,
                    y: origin.y + CGFloat(y) * module,
                    width: module,
                    height: module
                )
                let top = dark(x, y - 1), bottom = dark(x, y + 1), left = dark(x - 1, y), right = dark(x + 1, y)
                if dark(x, y) {
                    path.addRoundedRect(
                        in: cell,
                        cornerRadii: RectangleCornerRadii(
                            topLeading: !top && !left ? radius : 0,
                            bottomLeading: !bottom && !left ? radius : 0,
                            bottomTrailing: !bottom && !right ? radius : 0,
                            topTrailing: !top && !right ? radius : 0
                        )
                    )
                } else {
                    if top && left && dark(x - 1, y - 1) {
                        addFillet(to: &path, corner: CGPoint(x: cell.minX, y: cell.minY), dx: 1, dy: 1, radius: radius)
                    }
                    if top && right && dark(x + 1, y - 1) {
                        addFillet(to: &path, corner: CGPoint(x: cell.maxX, y: cell.minY), dx: -1, dy: 1, radius: radius)
                    }
                    if bottom && left && dark(x - 1, y + 1) {
                        addFillet(to: &path, corner: CGPoint(x: cell.minX, y: cell.maxY), dx: 1, dy: -1, radius: radius)
                    }
                    if bottom && right && dark(x + 1, y + 1) {
                        addFillet(to: &path, corner: CGPoint(x: cell.maxX, y: cell.maxY), dx: -1, dy: -1, radius: radius)
                    }
                }
            }
        }
        return path
    }

    /// The area between a cell's `corner` and a quarter circle of `radius` inside the cell; `dx`, `dy` point from the
    /// corner into the cell.
    private func addFillet(to path: inout Path, corner: CGPoint, dx: CGFloat, dy: CGFloat, radius: CGFloat) {
        let alongX = CGPoint(x: corner.x + dx * radius, y: corner.y)
        let alongY = CGPoint(x: corner.x, y: corner.y + dy * radius)
        path.move(to: alongY)
        path.addLine(to: corner)
        path.addLine(to: alongX)
        path.addArc(tangent1End: corner, tangent2End: alongY, radius: radius)
        path.closeSubpath()
    }
}

/// The code as holes in a light plate, for the tinted and clear home screens, where the system keeps only alpha: a
/// filled module would come out as light as its background. Fill it with `FillStyle(eoFill: true)`: the plate is one
/// layer, each module (fillets included) a second one inside it, so the modules stay empty and read dark on light.
struct QrPlateShape: Shape {
    let matrix: [[Bool]]

    /// The plate's margin around the code, in modules: the quiet zone the tile's background gives in full colour.
    static let quietZone = 2

    func path(in rect: CGRect) -> Path {
        let count = matrix.count
        guard count > 0 else { return Path() }
        let side = min(rect.width, rect.height)
        let module = side / CGFloat(count + 2 * Self.quietZone)
        let plate = CGRect(x: rect.midX - side / 2, y: rect.midY - side / 2, width: side, height: side)
        let margin = module * CGFloat(Self.quietZone)
        var path = Path(roundedRect: plate, cornerRadius: module)
        path.addPath(QrModulesShape(matrix: matrix).path(in: plate.insetBy(dx: margin, dy: margin)))
        return path
    }
}
