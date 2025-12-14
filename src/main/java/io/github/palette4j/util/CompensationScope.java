package io.github.palette4j.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Represents a scoped compensation mechanism for managing reversible side effects
 * in a controlled execution block.
 * <p>
 * This class implements a lightweight <em>compensation scope</em> (sometimes referred to as
 * a rollback or undo scope), allowing callers to register deferred undo actions that will be
 * executed automatically if the scope is not explicitly committed.
 * </p>
 * <p>
 * The scope is designed to be used with Java’s {@code try-with-resources} construct, ensuring
 * deterministic execution of compensation logic at scope exit.
 * </p>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><b>Undo stack</b> – a {@link Deque} of deferred {@link Runnable} compensation actions,
 *       executed in <em>LIFO</em> order.</li>
 *   <li><b>Commit flag</b> – indicates whether the scope has been successfully committed, suppressing
 *       execution of compensation actions on close.</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * <p>
 * Compensation actions are registered via {@link #defer(Runnable)} or
 * {@link #defer(Runnable, Consumer)} and are pushed onto an internal stack.
 * </p>
 * <p>
 * When {@link #close()} is invoked:
 * </p>
 * <ul>
 *   <li>If the scope has been {@linkplain #commit() committed}, no actions are executed.</li>
 *   <li>If the scope is <em>not</em> committed, all deferred actions are executed in reverse
 *       registration order (last-in, first-out).</li>
 * </ul>
 * <p>
 * This execution model is particularly well-suited for composing multiple dependent operations
 * where partial failure requires compensating earlier side effects.
 * </p>
 *
 * <h2>Error handling</h2>
 * <p>
 * Two defer variants are provided:
 * </p>
 * <ul>
 *   <li>{@link #defer(Runnable)} – exceptions thrown by the undo action are silently ignored.</li>
 *   <li>{@link #defer(Runnable, Consumer)} – exceptions are intercepted and
 *       forwarded to a caller-supplied handler. <strong>{@code NB!}</strong> If the exception-handling consumer
 *       itself throws an exception - this exception will <strong>propagate and terminate the rollback</strong>.</li>
 * </ul>
 * <p>
 * This allows callers to choose between fail-safe cleanup and explicit error reporting,
 * depending on operational requirements.
 * </p>
 *
 * <h2>Construction and lifecycle</h2>
 * <p>
 * Instances are intended to be short-lived and bound to a logical execution scope.
 * Typical usage follows the pattern:
 * </p>
 * <pre>{@code
 * try (CompensationScope scope = new CompensationScope()) {
 *     allocateResource();
 *     scope.defer(() -> releaseResource());
 *
 *     performOperation();
 *
 *     scope.commit(); // suppress compensation - no rollback will be performed on closing the scope
 * }
 * }
 * </pre>
 *
 * If {@code commit()} is not called, compensation actions are automatically executed
 * when the scope is closed. Once the scope is {@linkplain #commit() committed},
 * no further {@linkplain #defer(Runnable) deferring} and will result in {@link IllegalStateException} being thrown.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This class is <strong>not thread-safe</strong>. Instances are expected to be confined
 * to a single thread and a single execution flow.
 * </p>
 *
 * <h2>Design intent</h2>
 * <p>
 * {@code CompensationScope} provides a pragmatic alternative to heavyweight transactional
 * mechanisms in scenarios where:
 * </p>
 * <ul>
 *   <li>Operations span multiple systems or non-transactional resources</li>
 *   <li>Rollback must be explicitly modeled and ordered</li>
 *   <li>Failure recovery should remain local, predictable, and explicit</li>
 * </ul>
 * <p>
 * <h2>Examples</h2>
 * <pre>{@code
 *  try (CompensationScope scope = new CompensationScope()) {
 *      restApi.createOrder(order);  // places an order  via restful API
 *      scope.defer(() -> restApi.cancelOrder(order.getId()));  // rollback action
 *
 *      //... perform other operations
 *      //... if an exception is thrown - scope rolls back the deferred actions
 *
 *      scope.commit();  // commits the scope - no rollback required on closing
 *
 *  } catch (Exception e) {
 *      //... handle exception
 *  }
 *
 * }
 * </pre>
 */
public class CompensationScope implements AutoCloseable {

    private final Deque<Runnable> stack = new ArrayDeque<>();
    private boolean committed;

    /**
     * Closes this resource, executing any deferred compensation actions in LIFO order if the scope has not been committed.
     * If the scope has been committed via {@link CompensationScope#commit()}, this method does nothing.
     * This method is automatically called at the end of a try-with-resources block.
     */
    @Override
    public final void close() {
        if (committed) {
            return;
        }
        while (!stack.isEmpty()) {
            stack.pop().run();
        }
    }

    /**
     * Defers an undo action to be executed if the scope is not committed.
     * This version allows for handling exceptions that may occur during the execution of the undo action.
     * <p>
     * <strong>{@code NB!}</strong> If the {@code onUndoException} itself throws an exception - this exception will propagate
     * out of scope and <strong>terminate</strong> current {@link CompensationScope}'s rollback mechanism.
     *
     * @param undo            the {@link Runnable} action to be executed for compensation.
     * @param onUndoException a {@link Consumer} that accepts a {@link Throwable} and is called
     *                        if the undo action throws an exception.
     * @throws IllegalStateException if the scope has been committed.
     */
    public final void defer(Runnable undo, Consumer<Throwable> onUndoException) {
        if (committed) {
            throw new IllegalStateException("Compensation scope has been committed");
        }

        stack.push(() -> {
            try {
                undo.run();
            } catch (Throwable t) {
                onUndoException.accept(t);
            }
        });
    }

    /**
     * Defers an undo action to be executed if the scope is not committed.
     * Any exceptions thrown by the undo action will be silently ignored.
     *
     * @param undo the {@link Runnable} action to be executed for compensation.
     * @throws IllegalStateException if the scope has been committed.
     */
    public final void defer(Runnable undo) {
        if (committed) {
            throw new IllegalStateException("Compensation scope has been committed");
        }

        stack.push(() -> {
            try {
                undo.run();
            } catch (Throwable ignore) {
            }
        });
    }

    /**
     * Commits the current compensation scope.
     * <p>
     * After calling this method, no undo actions will be executed
     * when the scope will be closed via {@link CompensationScope#close()}.
     */
    public final void commit() {
        committed = true;
    }
}
