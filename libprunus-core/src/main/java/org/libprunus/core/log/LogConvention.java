package org.libprunus.core.log;

import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.UUID;
import org.libprunus.core.log.annotation.DirectToStringWhitelist;
import org.libprunus.core.log.annotation.LogBasePackages;
import org.libprunus.core.log.annotation.LogClassNameFormat;
import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxObjectDepth;
import org.libprunus.core.log.annotation.MaxObjectLength;
import org.libprunus.core.log.annotation.MethodLoggingProfile;
import org.libprunus.core.log.annotation.ToStringProfile;
import org.libprunus.core.log.runtime.LogLevel;

/**
 * Default logging convention registry.
 *
 * <p>To customize, create your own {@code @LogRegistry}-annotated class and select it as the
 * registry input for the build. All routing and formatting rules, along with any runtime
 * source/field definitions, are co-located in that single class.
 *
 * <p>Example of a custom registry with Spring runtime values:
 *
 * <pre>{@code
 * @LogRegistry
 * @MethodLoggingProfile(includePackages = {"com.example.web"}, includeClassSuffixes = {"Controller"}, fields = {"userId", "traceId"}, entryLevel = LogLevel.DEBUG, exitLevel = LogLevel.DEBUG)
 * @MethodLoggingProfile(includePackages = {"com.example.app"}, includeClassSuffixes = {"Service"}, fields = {"traceId"}, entryLevel = LogLevel.DEBUG, exitLevel = LogLevel.DEBUG)
 * @LogBasePackages({"com.example"})
 * public class AppLoggingConvention {
 *
 *     @MethodLoggingField("userId")
 *     public static String userId() {
 *         return MDC.get("userId");
 *     }
 *
 *     @MethodLoggingField("traceId")
 *     public static String traceId() {
 *         return MDC.get("traceId");
 *     }
 * }
 * }</pre>
 *
 * <p>In the example above:
 *
 * <ul>
 *   <li>{@code @MethodLoggingProfile} is {@code @Repeatable}: multiple profiles can be declared
 *       directly on the registry class without an explicit {@code @MethodLoggingProfiles} wrapper.
 *       Classes under {@code com.example.web} ending with {@code Controller} include both {@code
 *       userId} and {@code traceId}; classes under {@code com.example.app} ending with {@code
 *       Service} only include {@code traceId}. The field names correspond directly to the names
 *       declared on {@code @MethodLoggingField} methods. Profiles are evaluated in declaration
 *       order.
 *   <li>{@code userId()} and {@code traceId()} are direct field extractors called with no
 *       arguments.
 *   <li>All methods are static and live directly in the {@code @LogRegistry} class — no Spring
 *       container or {@code @Configuration} class is involved.
 * </ul>
 */
@LogRegistry
@LogBasePackages({"org.libprunus"})
@MethodLoggingProfile(
        includePackages = {"org.libprunus"},
        includeClassSuffixes = {"Controller", "Service"},
        entryLevel = LogLevel.INFO,
        exitLevel = LogLevel.INFO)
@ToStringProfile(
        includePackages = {"org.libprunus"},
        includeClassSuffixes = {"Dto", "Entity", "Request", "Response"},
        classNameFormat = LogClassNameFormat.SIMPLE)
@MaxObjectLength(512)
@MaxObjectDepth(5)
@DirectToStringWhitelist({
    Enum.class,
    Number.class,
    CharSequence.class,
    TemporalAccessor.class,
    Boolean.class,
    Character.class,
    UUID.class,
    Date.class,
    Class.class
})
public final class LogConvention {

    /** Prevents instantiation of this utility class. */
    private LogConvention() {}
}
