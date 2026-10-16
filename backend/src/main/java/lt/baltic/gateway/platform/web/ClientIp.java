package lt.baltic.gateway.platform.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {
    private ClientIp() {}
    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
