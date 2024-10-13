package com.smat.webrtc;

import java.util.Map;
/* loaded from: input.aar:classes.jar:org/webrtc/RTCStatsReport.class */
public class RTCStatsReport {
    private final long timestampUs;
    private final Map<String, RTCStats> stats;

    public RTCStatsReport(long timestampUs, Map<String, RTCStats> stats) {
        this.timestampUs = timestampUs;
        this.stats = stats;
    }

    public double getTimestampUs() {
        return this.timestampUs;
    }

    public Map<String, RTCStats> getStatsMap() {
        return this.stats;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{ timestampUs: ").append(this.timestampUs).append(", stats: [\n");
        boolean first = true;
        for (RTCStats stat : this.stats.values()) {
            if (!first) {
                builder.append(",\n");
            }
            builder.append(stat);
            first = false;
        }
        builder.append(" ] }");
        return builder.toString();
    }

    @CalledByNative
    private static RTCStatsReport create(long timestampUs, Map stats) {
        return new RTCStatsReport(timestampUs, stats);
    }
}
