# Demo: timing a running Spring Boot application

An ordinary Spring Boot 4 application ([`app/`](app)) with a REST endpoint, `JdbcTemplate`
and an H2 database. It does not depend on Tendril and is not started with any agent.

[`hooks/`](hooks/src/main/java/timing/Timing.java) is forty lines of `@Inject` methods
that time every SQL statement (in the H2 JDBC driver) and every HTTP request (in Spring's
`DispatcherServlet`).

```bash
./demo.sh
```

The script starts the application from its fat jar, sends a request, then loads the
hooks into the running process by its pid, sends two more requests, removes the hooks
and sends a last one:

![recording of the demo](../../docs/demo.svg)

What this shows beyond the unit tests:

- The application runs from a Spring Boot fat jar, so its classes and libraries are
  loaded by Spring's own class loader, not the system one. The hook jar is added to the
  system class path and cannot see those classes. The hooks still work, because woven
  code only ever calls the bridge on the bootstrap class path.
- Nothing about the application changes: no dependency, no flag, no restart. After
  `detach` its bytecode is the original again.

`./record.py ../../docs/demo.svg` regenerates the recording from a fresh run. This is a
separate Gradle build so that the library's own build does not download Spring Boot.
