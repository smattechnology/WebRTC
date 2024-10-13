package com.smat.webrtc;

import android.support.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.webrtc.audio.WebRtcAudioRecord;
/* loaded from: input.aar:classes.jar:org/webrtc/Logging.class */
public class Logging {
    private static final Logger fallbackLogger = createFallbackLogger();
    private static volatile boolean loggingEnabled;
    @Nullable
    private static Loggable loggable;
    private static Severity loggableSeverity;

    /* loaded from: input.aar:classes.jar:org/webrtc/Logging$Severity.class */
    public enum Severity {
        LS_VERBOSE,
        LS_INFO,
        LS_WARNING,
        LS_ERROR,
        LS_NONE
    }

    private static native void nativeEnableLogToDebugOutput(int i);

    private static native void nativeEnableLogThreads();

    private static native void nativeEnableLogTimeStamps();

    private static native void nativeLog(int i, String str, String str2);

    private static Logger createFallbackLogger() {
        Logger fallbackLogger2 = Logger.getLogger("org.webrtc.Logging");
        fallbackLogger2.setLevel(Level.ALL);
        return fallbackLogger2;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void injectLoggable(Loggable injectedLoggable, Severity severity) {
        if (injectedLoggable != null) {
            loggable = injectedLoggable;
            loggableSeverity = severity;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void deleteInjectedLoggable() {
        loggable = null;
    }

    @Deprecated
    /* loaded from: input.aar:classes.jar:org/webrtc/Logging$TraceLevel.class */
    public enum TraceLevel {
        TRACE_NONE(0),
        TRACE_STATEINFO(1),
        TRACE_WARNING(2),
        TRACE_ERROR(4),
        TRACE_CRITICAL(8),
        TRACE_APICALL(16),
        TRACE_DEFAULT(255),
        TRACE_MODULECALL(32),
        TRACE_MEMORY(256),
        TRACE_TIMER(512),
        TRACE_STREAM(1024),
        TRACE_DEBUG(2048),
        TRACE_INFO(4096),
        TRACE_TERSEINFO(8192),
        TRACE_ALL(65535);
        
        public final int level;

        TraceLevel(int level) {
            this.level = level;
        }
    }

    public static void enableLogThreads() {
        nativeEnableLogThreads();
    }

    public static void enableLogTimeStamps() {
        nativeEnableLogTimeStamps();
    }

    @Deprecated
    public static void enableTracing(String path, EnumSet<TraceLevel> levels) {
    }

    public static synchronized void enableLogToDebugOutput(Severity severity) {
        if (loggable != null) {
            throw new IllegalStateException("Logging to native debug output not supported while Loggable is injected. Delete the Loggable before calling this method.");
        }
        nativeEnableLogToDebugOutput(severity.ordinal());
        loggingEnabled = true;
    }

    public static void log(Severity severity, String tag, String message) {
        Level level;
        if (tag == null || message == null) {
            throw new IllegalArgumentException("Logging tag or message may not be null.");
        }
        if (loggable != null) {
            if (severity.ordinal() < loggableSeverity.ordinal()) {
                return;
            }
            loggable.onLogMessage(message, severity, tag);
        } else if (loggingEnabled) {
            nativeLog(severity.ordinal(), tag, message);
        } else {
            switch (AnonymousClass1.$SwitchMap$org$webrtc$Logging$Severity[severity.ordinal()]) {
                case 1:
                    level = Level.SEVERE;
                    break;
                case WebRtcAudioRecord.DEFAULT_AUDIO_FORMAT /* 2 */:
                    level = Level.WARNING;
                    break;
                case 3:
                    level = Level.INFO;
                    break;
                default:
                    level = Level.FINE;
                    break;
            }
            fallbackLogger.log(level, tag + ": " + message);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: org.webrtc.Logging$1  reason: invalid class name */
    /* loaded from: input.aar:classes.jar:org/webrtc/Logging$1.class */
    public static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$org$webrtc$Logging$Severity = new int[Severity.values().length];

        static {
            try {
                $SwitchMap$org$webrtc$Logging$Severity[Severity.LS_ERROR.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$org$webrtc$Logging$Severity[Severity.LS_WARNING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$org$webrtc$Logging$Severity[Severity.LS_INFO.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    public static void d(String tag, String message) {
        log(Severity.LS_INFO, tag, message);
    }

    public static void e(String tag, String message) {
        log(Severity.LS_ERROR, tag, message);
    }

    public static void w(String tag, String message) {
        log(Severity.LS_WARNING, tag, message);
    }

    public static void e(String tag, String message, Throwable e) {
        log(Severity.LS_ERROR, tag, message);
        log(Severity.LS_ERROR, tag, e.toString());
        log(Severity.LS_ERROR, tag, getStackTraceString(e));
    }

    public static void w(String tag, String message, Throwable e) {
        log(Severity.LS_WARNING, tag, message);
        log(Severity.LS_WARNING, tag, e.toString());
        log(Severity.LS_WARNING, tag, getStackTraceString(e));
    }

    public static void v(String tag, String message) {
        log(Severity.LS_VERBOSE, tag, message);
    }

    private static String getStackTraceString(Throwable e) {
        if (e == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
