package org.libprunus.core.log.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.libprunus.core.config.CoreRuntimeConfig;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LogRuntime {

    static final String CALLSITE_BINDING_RESOURCE = CallsiteBindingProtocol.RESOURCE_PATH;

    private static volatile AtomicReference<CoreRuntimeConfig> ACTIVE_CONFIG_REF =
            new AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true)));

    private static volatile AbstractLogConfig boundConfig = AbstractLogConfig.DEFAULT;
    private static volatile int boundMaxMessageLength = AbstractLogConfig.DEFAULT.getMaxMessageLength();
    private static volatile boolean bindingInitialized;

    private LogRuntime() {
        throw new UnsupportedOperationException();
    }

    /**
     * Publishes the compile-time binding (max message length, type whitelist) for the JVM. Called
     * once at bootstrap by the AOT-generated callsite's {@code bind()} body.
     *
     * <p>Once-only: a second call throws {@link IllegalStateException}. After a successful return,
     * {@link #getGlobalMaxMessageLength()} and {@link #globalConfigBinding()} are stable for the
     * JVM lifetime.
     *
     * <p>The synchronized block publishes the three binding fields
     * ({@link #boundConfig}, {@link #boundMaxMessageLength}, {@link #bindingInitialized})
     * via their {@code volatile} declarations to all subsequent readers, including
     * lock-free fast-path readers such as {@link #isEnabled()} and
     * {@link #getGlobalMaxMessageLength()}.
     *
     * @param bindingConfig non-null binding; {@code getMaxMessageLength()} must be in
     *                      {@code [16, 1048576]} (see {@link MaxMessageLength}). An out-of-range
     *                      value throws {@link IllegalArgumentException} without mutating state.
     * @throws NullPointerException     if {@code bindingConfig} is {@code null}.
     * @throws IllegalStateException    if a previous call already succeeded.
     * @throws IllegalArgumentException if the binding reports an out-of-range max length.
     */
    public static synchronized void initializeBinding(AbstractLogConfig bindingConfig) {
        Objects.requireNonNull(bindingConfig, "bindingConfig must not be null");
        if (bindingInitialized) {
            throw new IllegalStateException("LogRuntime binding has already been initialized");
        }
        int validatedLength = validateMaxMessageLength(bindingConfig.getMaxMessageLength());
        boundConfig = bindingConfig;
        boundMaxMessageLength = validatedLength;
        bindingInitialized = true;
    }

    /**
     * Locates the AOT-generated callsite class via the
     * {@code META-INF/prunus/aot/runtime-binding-callsite} resource on {@code classLoader} and
     * reflectively invokes its public static {@code bind()} method. The generated {@code bind()}
     * body (see {@code RuntimeBindingCallsiteGenerator}) instantiates the binding class and
     * forwards to {@link #initializeBinding(AbstractLogConfig)}.
     *
     * <p>Behavior:
     * <ul>
     *   <li>Resource absent on {@code classLoader} → silent return; the runtime keeps using
     *       {@link AbstractLogConfig#DEFAULT}.
     *   <li>{@link java.io.IOException} / {@link ReflectiveOperationException} / {@link LinkageError}
     *       from loading or invoking the callsite → wrapped in {@link IllegalStateException}, with
     *       the offending class name embedded in the message.
     *   <li>Resource present but blank/empty after strip → {@link IllegalStateException}
     *       with no cause; the message identifies the offending resource path.
     * </ul>
     *
     * <p>No internal idempotency guard. Multiple calls are intentional and supported — useful when
     * different {@link ClassLoader} trees (plugin frameworks, hot-reload, test isolation) each
     * carry their own callsite resource. The once-only enforcement lives in
     * {@link #initializeBinding} itself; if two successful callsite invocations both reach
     * {@code initializeBinding}, the second throws {@link IllegalStateException} from there.
     *
     * <p><b>Concurrency.</b> This method is intentionally not {@code synchronized}.
     * The JVM-global binding state mutated by {@code bind()} lives in
     * {@link #initializeBinding(AbstractLogConfig)}, whose synchronized block plus the
     * {@code volatile} declarations of {@link #boundConfig},
     * {@link #boundMaxMessageLength} and {@link #bindingInitialized} together provide
     * the happens-before edge: any thread observing {@code bindingInitialized == true}
     * (directly, or via {@link #getGlobalMaxMessageLength()} /
     * {@link #globalConfigBinding()}) sees the complete winner publish. Concurrent
     * callers — one per {@code ClassLoader} tree, or many on the same tree — are
     * supported; at most one reaches a successful {@code initializeBinding}, and the
     * rest surface the once-only {@link IllegalStateException} wrapped through this
     * method's reflection catch.
     *
     * @param classLoader the non-null classloader to scan.
     * @throws NullPointerException  if {@code classLoader} is {@code null}.
     * @throws IllegalStateException if a referenced callsite class fails to load or bind,
     *         or if the callsite resource is present but blank/empty after strip.
     */
    public static void invokeCallsiteBinding(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        String callsiteClass = null;
        try (InputStream is = classLoader.getResourceAsStream(CALLSITE_BINDING_RESOURCE)) {
            if (is == null) {
                return;
            }
            callsiteClass = new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
            if (callsiteClass.isEmpty()) {
                throw new IllegalStateException("Failed to invoke callsite binding: resource "
                        + CALLSITE_BINDING_RESOURCE + " is empty or blank");
            }
            Class.forName(callsiteClass, true, classLoader).getMethod("bind").invoke(null);
        } catch (IOException | ReflectiveOperationException | LinkageError e) {
            Throwable cause = (e instanceof InvocationTargetException ite) ? ite.getCause() : e;
            String targetInfo = (callsiteClass != null && !callsiteClass.isEmpty())
                    ? " for target class: [" + callsiteClass + "]"
                    : "";
            throw new IllegalStateException("Failed to invoke callsite binding" + targetInfo, cause);
        }
    }

    public static int getGlobalMaxMessageLength() {
        return boundMaxMessageLength;
    }

    public static boolean isBindingInitialized() {
        return bindingInitialized;
    }

    static AbstractLogConfig globalConfigBinding() {
        return boundConfig;
    }

    private static int validateMaxMessageLength(int maxLength) {
        if (maxLength < MaxMessageLength.MIN_VALUE) {
            throw new IllegalArgumentException(
                    "binding maxMessageLength must be >= " + MaxMessageLength.MIN_VALUE + ": " + maxLength);
        }
        if (maxLength > MaxMessageLength.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "binding maxMessageLength must be <= " + MaxMessageLength.MAX_VALUE + ": " + maxLength);
        }
        return maxLength;
    }

    /**
     * Installs a live {@link CoreRuntimeConfig} reference that {@link #isEnabled()} consults on
     * every call. The {@code configRef} is the {@link AtomicReference} instance shared with the
     * data plane; the data plane publishes runtime updates via {@code configRef.set(...)} for
     * hot-swap behavior.
     *
     * <p>Unlike {@link #initializeBinding(AbstractLogConfig)}, this method may be invoked multiple
     * times — each call replaces the previous reference instance entirely. The compile-time
     * binding state ({@link #globalConfigBinding()}, {@link #getGlobalMaxMessageLength()}) is
     * unaffected by data-plane linking.
     *
     * <p><b>Concurrency.</b> The {@code synchronized} block on this method serializes
     * concurrent {@code linkToDataPlane} writers; the {@code volatile} declaration of
     * {@link #ACTIVE_CONFIG_REF} publishes the replacement reference to lock-free fast-path
     * readers such as {@link #isEnabled()} without monitor traversal. Data-plane
     * {@code configRef.set(...)} updates are independently safe by virtue of
     * {@link AtomicReference}'s own write semantics.
     *
     * @param configRef non-null AtomicReference owned by the data plane.
     * @throws NullPointerException if {@code configRef} is {@code null}.
     */
    public static synchronized void linkToDataPlane(AtomicReference<CoreRuntimeConfig> configRef) {
        ACTIVE_CONFIG_REF = Objects.requireNonNull(configRef, "configRef must not be null");
    }

    @SuppressWarnings("NullAway") // ACTIVE_CONFIG_REF contents non-null by construction; get() modeled @Nullable
    public static boolean isEnabled() {
        return ACTIVE_CONFIG_REF.get().log().enabled();
    }

    /**
     * Bootstrap method for {@code LDC Dynamic} (condy) logger constants emitted by AOT-generated
     * callsites. The JVM's {@link java.lang.invoke.ConstantBootstraps} calling convention requires
     * the {@code (Lookup, String, Class<?>, ...trailingArgs)} parameter shape, so {@code name} and
     * {@code type} are declared as placeholder formals.
     *
     * <p>This implementation intentionally ignores both:
     * <ul>
     *   <li>{@code String name} — the JVM-supplied condy "constant name"; the returned logger name
     *       is always determined by {@code ownerName} (the trailing AOT-supplied argument) or, when
     *       absent, by {@code lookup.lookupClass()}.
     *   <li>{@code Class<?> type} — the JVM-supplied condy "constant type", which is always
     *       {@code org.slf4j.Logger} for this bootstrap and never participates in logger resolution.
     * </ul>
     *
     * <p><b>Reverse invariant</b>: flipping the value of {@code name} or {@code type} does not
     * change the returned logger instance — only {@code lookup} and {@code ownerName} are observable
     * inputs.
     */
    public static Logger condyLoggerFactory(MethodHandles.Lookup lookup, String name, Class<?> type, String ownerName) {
        if (ownerName != null && !ownerName.isBlank()) {
            return LoggerFactory.getLogger(ownerName);
        }
        return LoggerFactory.getLogger(lookup.lookupClass());
    }

    public static ClassValue<Boolean> buildWhitelistCache(String[] whitelistNames) {
        Objects.requireNonNull(whitelistNames, "whitelistNames must not be null");
        Set<String> nameSet = HashSet.newHashSet(whitelistNames.length);
        for (String name : whitelistNames) {
            Objects.requireNonNull(name, "whitelistNames must not contain null entries");
            if (!nameSet.add(name)) {
                throw new IllegalArgumentException("whitelistNames contains duplicate entry: [" + name + "]");
            }
        }
        Set<String> immutable = Set.copyOf(nameSet);
        return new ClassValue<>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                return isWhitelistedImpl(type, immutable, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        };
    }

    public static boolean isWhitelistedCached(Class<?> c, ClassValue<Boolean> cache) {
        return c != null && cache.get(c);
    }

    /**
     * Walks the superclass and superinterface DAG of {@code c} looking for a class whose
     * fully qualified name appears in {@code names}.
     *
     * <p>The {@code visited} set is <em>not</em> a defense against superclass self-loops (the JVM's
     * class hierarchy is a tree and cannot contain a self-loop). It guards the interface DAG: when
     * a class has multiple superinterfaces sharing a common ancestor (diamond inheritance / wide
     * interface DAG), {@code visited} prevents the same interface from being traversed twice and
     * keeps complexity linear in the size of the ancestor closure.
     */
    private static boolean isWhitelistedImpl(Class<?> c, Set<String> names, Set<Class<?>> visited) {
        if (!visited.add(c)) {
            return false;
        }
        if (names.contains(c.getName())) {
            return true;
        }

        Class<?> superclass = c.getSuperclass();
        if (superclass != null && isWhitelistedImpl(superclass, names, visited)) {
            return true;
        }

        for (Class<?> iface : c.getInterfaces()) {
            if (isWhitelistedImpl(iface, names, visited)) {
                return true;
            }
        }

        return false;
    }
}
