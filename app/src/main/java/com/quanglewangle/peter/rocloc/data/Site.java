package com.quanglewangle.peter.rocloc.data;

public class Site {
    public String call_sign;
    public String name;
    public double lat;
    public double lon;
    public double qnf;
    public double qnh;
    public String site_type;

    public boolean isPin() { return "pin".equals(site_type); }

    public String displayCallsign() {
        return (call_sign != null && !call_sign.isEmpty()) ? call_sign : name;
    }
}
