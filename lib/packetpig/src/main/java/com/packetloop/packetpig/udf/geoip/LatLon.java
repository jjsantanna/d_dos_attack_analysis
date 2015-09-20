package com.packetloop.packetpig.udf.geoip;

import com.maxmind.geoip.Location;
import com.maxmind.geoip.LookupService;
import org.apache.pig.EvalFunc;
import org.apache.pig.data.Tuple;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LatLon extends EvalFunc<String> {

    private LookupService cl;

    public LatLon() throws IOException {
        try {
            cl = new LookupService("data/GeoLiteCity.dat", LookupService.GEOIP_MEMORY_CACHE);
        } catch (FileNotFoundException ignored) {
            cl = new LookupService("GeoLiteCity.dat", LookupService.GEOIP_MEMORY_CACHE);
        }
    }

    public List<String> getCacheFiles() {
        List<String> s = new ArrayList<String>();
        s.add("hdfs:///packetpig/GeoIP.dat#GeoIP.dat");
        return s;
    }

    @Override
    public String exec(Tuple input) throws IOException {
        if (input.size() > 0) {
            String ipaddr = (String)input.get(0);
            Location location = cl.getLocation(ipaddr);

            if (location == null)
                return null;

            return location.latitude + " " + location.longitude;
        }

        return null;
    }
}
