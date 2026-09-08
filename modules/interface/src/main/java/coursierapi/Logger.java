package coursierapi;

import coursier.internal.api.ApiHelper;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public interface Logger {

    static Logger progressBars() {
        return ApiHelper.progressBarLogger(new OutputStreamWriter(System.err));
    }
    static Logger progressBars(OutputStream os) {
        return ApiHelper.progressBarLogger(new OutputStreamWriter(os));
    }
    static Logger progressBars(OutputStreamWriter writer) {
        return ApiHelper.progressBarLogger(writer);
    }
    /** Progress bars written to an arbitrary {@link Writer}, like a {@link java.io.PrintWriter} */
    static Logger progressBars(Writer writer) {
        return ApiHelper.progressBarLogger(writer);
    }

    static Logger nop() {
        return ApiHelper.nopLogger();
    }
}
