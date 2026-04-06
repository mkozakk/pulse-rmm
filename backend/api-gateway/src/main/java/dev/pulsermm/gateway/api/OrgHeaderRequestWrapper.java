package dev.pulsermm.gateway.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

// Hides any client-supplied X-User-Org-Id and replaces it with the trusted value derived from the JWT.
// A null org (global admin) means the header is removed entirely so downstream services apply no filter.
public class OrgHeaderRequestWrapper extends HttpServletRequestWrapper {

    static final String HEADER = "X-User-Org-Id";

    private final String orgId;

    public OrgHeaderRequestWrapper(HttpServletRequest request, String orgId) {
        super(request);
        this.orgId = orgId;
    }

    @Override
    public String getHeader(String name) {
        if (HEADER.equalsIgnoreCase(name)) {
            return orgId;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (HEADER.equalsIgnoreCase(name)) {
            return orgId == null ? Collections.emptyEnumeration()
                : Collections.enumeration(List.of(orgId));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = new ArrayList<>();
        Enumeration<String> original = super.getHeaderNames();
        while (original.hasMoreElements()) {
            String name = original.nextElement();
            if (!HEADER.equalsIgnoreCase(name)) {
                names.add(name);
            }
        }
        if (orgId != null) {
            names.add(HEADER);
        }
        return Collections.enumeration(names);
    }
}
