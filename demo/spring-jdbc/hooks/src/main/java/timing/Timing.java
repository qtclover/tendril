package timing;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.CallInfo;
import io.github.qtclover.tendril.Inject;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Times every SQL statement and every HTTP request of a running Spring Boot
 * application. The application is not rebuilt, restarted or reconfigured: this class
 * is loaded into it with
 *
 * <pre>java -jar tendril-agent-all.jar attach &lt;pid&gt; --hooks timing.Timing --classpath hooks.jar</pre>
 *
 * The targets are named as strings because this jar sits on the system class path,
 * while Spring Boot loads the application and its libraries through its own class
 * loader. Tendril does not mind; the hooks just cannot use Spring's types directly.
 */
public final class Timing {

    /** Which SQL each prepared statement was created from. */
    private static final Map<Object, String> SQL = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Long> STATEMENT_START = new ThreadLocal<>();
    private static final ThreadLocal<Long> REQUEST_START = new ThreadLocal<>();

    private Timing() {
    }

    // ---- SQL: the H2 JDBC driver --------------------------------------------

    @Inject(targetName = "org.h2.jdbc.JdbcConnection", method = "prepareStatement",
            descriptor = "(Ljava/lang/String;)", at = At.RETURN)
    static void remember(CallInfo call) {
        SQL.put(call.getResult(), call.arg(0));
    }

    @Inject(targetName = "org.h2.jdbc.JdbcPreparedStatement", method = "executeQuery", descriptor = "()")
    static void queryStarts() {
        STATEMENT_START.set(System.nanoTime());
    }

    @Inject(targetName = "org.h2.jdbc.JdbcPreparedStatement", method = "executeQuery", descriptor = "()",
            at = At.RETURN)
    static void queryEnds(CallInfo call) {
        report(call);
    }

    @Inject(targetName = "org.h2.jdbc.JdbcPreparedStatement", method = "executeUpdate", descriptor = "()")
    static void updateStarts() {
        STATEMENT_START.set(System.nanoTime());
    }

    @Inject(targetName = "org.h2.jdbc.JdbcPreparedStatement", method = "executeUpdate", descriptor = "()",
            at = At.RETURN)
    static void updateEnds(CallInfo call) {
        report(call);
    }

    private static void report(CallInfo call) {
        Long start = STATEMENT_START.get();
        if (start != null) {
            System.out.printf("[tendril]   sql  %6.2f ms  %s%n", (System.nanoTime() - start) / 1e6,
                    SQL.getOrDefault(call.self(), "?"));
        }
    }

    // ---- HTTP: Spring MVC ---------------------------------------------------

    @Inject(targetName = "org.springframework.web.servlet.DispatcherServlet", method = "doDispatch")
    static void requestStarts() {
        REQUEST_START.set(System.nanoTime());
    }

    @Inject(targetName = "org.springframework.web.servlet.DispatcherServlet", method = "doDispatch",
            at = At.RETURN)
    static void requestEnds(CallInfo call) throws Exception {
        Object request = call.arg(0);
        // Reflection, because HttpServletRequest is not visible from this class loader.
        Object uri = request.getClass().getMethod("getRequestURI").invoke(request);
        System.out.printf("[tendril] http  %6.2f ms  GET %s%n", (System.nanoTime() - REQUEST_START.get()) / 1e6, uri);
    }
}
