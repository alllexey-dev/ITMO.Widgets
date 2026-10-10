import Foundation

/// One HTTP exchange of the notification service: the status and the body, or a thrown transport failure (no answer).
protocol PushTransport {
    func send(_ request: URLRequest) async throws -> (status: Int, body: Data)
}

/// URLSession without cookies, cache or redirects, as MyItmoApi's clients run (SP-16a): every request carries its
/// own bearer token, and nothing of the exchange stays on disk.
final class URLSessionPushTransport: NSObject, PushTransport, URLSessionTaskDelegate {
    private lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCache = nil
        configuration.timeoutIntervalForRequest = 10
        return URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
    }()

    func send(_ request: URLRequest) async throws -> (status: Int, body: Data) {
        let (body, response) = try await session.data(for: request)
        return ((response as? HTTPURLResponse)?.statusCode ?? 0, body)
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest
    ) async -> URLRequest? {
        nil
    }
}

extension URLRequest {
    /// A request of `method` to `url` with a JSON or form `body`; never logged, it may carry a token.
    static func push(_ method: String, _ url: URL, body: Data? = nil, contentType: String? = nil) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        return request
    }
}
