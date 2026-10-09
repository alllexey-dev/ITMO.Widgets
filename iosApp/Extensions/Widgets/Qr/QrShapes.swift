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

/// The spoiler's soft noise, as Android's `QrBitmapRenderer.renderNoise` draws it: small rounded squares in the
/// shades between the tile and the code colour. The pattern depends only on `seed`, so a timeline entry renders the
/// same every time.
struct QrNoisePattern {
    /// Squares per shade, in unit coordinates of the drawing area.
    let shades: [[CGRect]]

    /// Shades strictly between the tile colour and the code colour (Android's palette of 7 without its ends).
    static let shadeCount = 5

    init(seed: UInt64) {
        var generator = SplitMix64(seed: seed)
        var shades = Array(repeating: [CGRect](), count: Self.shadeCount)
        let minSide = 0.02, maxSide = 0.03, padding = 0.025
        let inset = maxSide + padding
        for _ in 0..<1000 {
            let side = Double.random(in: minSide..<maxSide, using: &generator)
            let centerX = Double.random(in: inset..<(1 - inset), using: &generator)
            let centerY = Double.random(in: inset..<(1 - inset), using: &generator)
            let shade = Int.random(in: 0..<Self.shadeCount, using: &generator)
            shades[shade].append(CGRect(x: centerX - side / 2, y: centerY - side / 2, width: side, height: side))
        }
        self.shades = shades
    }
}

/// The squares of one shade of a `QrNoisePattern`.
struct QrNoiseShape: Shape {
    let squares: [CGRect]

    func path(in rect: CGRect) -> Path {
        let side = min(rect.width, rect.height)
        let origin = CGPoint(x: rect.midX - side / 2, y: rect.midY - side / 2)
        var path = Path()
        for unit in squares {
            let square = CGRect(
                x: origin.x + unit.minX * side,
                y: origin.y + unit.minY * side,
                width: unit.width * side,
                height: unit.height * side
            )
            path.addRoundedRect(in: square, cornerSize: CGSize(width: square.width / 3, height: square.height / 3))
        }
        return path
    }
}

/// A small deterministic generator (SplitMix64), so the noise does not change between renderings of one entry.
struct SplitMix64: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
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
