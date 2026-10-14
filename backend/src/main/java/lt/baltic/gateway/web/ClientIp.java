package lt.baltic.gateway.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {
    private ClientIp() {}
    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
