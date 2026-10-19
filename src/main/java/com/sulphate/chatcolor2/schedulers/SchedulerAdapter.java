package com.sulphate.chatcolor2.schedulers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Scheduler abstraction that provides compatibility between Paper and Folia.
 * Automatically detects the server type and uses appropriate scheduling methods.
 */
public class SchedulerAdapter {

    private static final SchedulerWrapper SCHEDULER = createScheduler();

    private static SchedulerWrapper createScheduler() {
        if (isFoliaServer()) {
            return new FoliaSchedulerWrapper();
        }

        return new BukkitSchedulerWrapper();
    }

    private static boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Check if the server is running Folia.
     */
    public static boolean isFolia() {
        return SCHEDULER.isFolia();
    }

    /**
     * Runs a task repeatedly on the global region (Folia) or main thread (Paper).
     * 
     * @param plugin The plugin instance
     * @param task The task to run
     * @param delayTicks Delay before first execution in ticks
     * @param periodTicks Period between executions in ticks
     * @return A task wrapper that can be cancelled
     */
    public static TaskWrapper runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return SCHEDULER.runTimer(plugin, task, delayTicks, periodTicks);
    }

    /**
     * Runs a task once after a delay on the global region (Folia) or main thread (Paper).
     * 
     * @param plugin The plugin instance
     * @param task The task to run
     * @param delayTicks Delay before execution in ticks
     * @return A task wrapper that can be cancelled
     */
    public static TaskWrapper runLater(Plugin plugin, Runnable task, long delayTicks) {
        return SCHEDULER.runLater(plugin, task, delayTicks);
    }

    /**
     * Runs a task asynchronously.
     * 
     * @param plugin The plugin instance
     * @param task The task to run
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        SCHEDULER.runAsync(plugin, task);
    }

    /**
     * Runs a task on the entity's scheduler (Folia) or main thread (Paper).
     * This is used for entity-specific operations.
     * 
     * @param plugin The plugin instance
     * @param entity The entity
     * @param task The task to run
     */
    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        SCHEDULER.runForEntity(plugin, entity, task);
    }

    /**
     * Runs a task after a delay on the entity's scheduler (Folia) or main thread (Paper).
     * This is used for player-specific delayed operations.
     * 
     * @param plugin The plugin instance
     * @param entity The entity
     * @param task The task to run
     * @param delayTicks Delay before execution in ticks
     * @return A task wrapper that can be cancelled
     */
    public static TaskWrapper runForEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        return SCHEDULER.runForEntity(plugin, entity, task, delayTicks);
    }

    /**
     * Wrapper interface for scheduled tasks that can be cancelled.
     */
    public interface TaskWrapper {
        void cancel();
    }

    private interface SchedulerWrapper {
        boolean isFolia();

        TaskWrapper runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks);

        TaskWrapper runLater(Plugin plugin, Runnable task, long delayTicks);

        void runAsync(Plugin plugin, Runnable task);

        void runForEntity(Plugin plugin, Entity entity, Runnable task);

        TaskWrapper runForEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks);
    }

    private static final class BukkitSchedulerWrapper implements SchedulerWrapper {
        @Override
        public boolean isFolia() {
            return false;
        }

        @Override
        public TaskWrapper runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
            int taskId = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks).getTaskId();
            return new BukkitTaskWrapper(taskId);
        }

        @Override
        public TaskWrapper runLater(Plugin plugin, Runnable task, long delayTicks) {
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks).getTaskId();
            return new BukkitTaskWrapper(taskId);
        }

        @Override
        public void runAsync(Plugin plugin, Runnable task) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }

        @Override
        public void runForEntity(Plugin plugin, Entity entity, Runnable task) {
            Bukkit.getScheduler().runTask(plugin, task);
        }

        @Override
        public TaskWrapper runForEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks).getTaskId();
            return new BukkitTaskWrapper(taskId);
        }
    }

    private static final class FoliaSchedulerWrapper implements SchedulerWrapper {
        private final Method getGlobalRegionSchedulerMethod;
        private final Method getAsyncSchedulerMethod;
        private final Method getEntitySchedulerMethod;
        private final Method runAtFixedRateMethod;
        private final Method globalRunDelayedMethod;
        private final Method asyncRunNowMethod;
        private final Method entityRunMethod;
        private final Method entityRunDelayedMethod;
        private final Method cancelMethod;

        FoliaSchedulerWrapper() {
            try {
                Class<?> serverClass = Class.forName("org.bukkit.Server");
                Class<?> entityClass = Class.forName("org.bukkit.entity.Entity");
                Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
                Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

                getGlobalRegionSchedulerMethod = serverClass.getMethod("getGlobalRegionScheduler");
                getAsyncSchedulerMethod = serverClass.getMethod("getAsyncScheduler");
                getEntitySchedulerMethod = entityClass.getMethod("getScheduler");
                runAtFixedRateMethod = globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
                globalRunDelayedMethod = globalSchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
                asyncRunNowMethod = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
                entityRunMethod = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
                entityRunDelayedMethod = entitySchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
                cancelMethod = scheduledTaskClass.getMethod("cancel");
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize Folia scheduler reflection", e);
            }
        }

        @Override
        public boolean isFolia() {
            return true;
        }

        @Override
        public TaskWrapper runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
            try {
                Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(Bukkit.getServer());
                Object scheduledTask = runAtFixedRateMethod.invoke(
                    globalScheduler,
                    plugin,
                    (Consumer<Object>) ignored -> task.run(),
                    delayTicks,
                    periodTicks
                );
                return new FoliaTaskWrapper(scheduledTask, cancelMethod);
            } catch (Exception e) {
                throw new RuntimeException("Failed to schedule repeating task on Folia", e);
            }
        }

        @Override
        public TaskWrapper runLater(Plugin plugin, Runnable task, long delayTicks) {
            try {
                Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(Bukkit.getServer());
                Object scheduledTask = globalRunDelayedMethod.invoke(
                    globalScheduler,
                    plugin,
                    (Consumer<Object>) ignored -> task.run(),
                    delayTicks
                );
                return new FoliaTaskWrapper(scheduledTask, cancelMethod);
            } catch (Exception e) {
                throw new RuntimeException("Failed to schedule delayed task on Folia", e);
            }
        }

        @Override
        public void runAsync(Plugin plugin, Runnable task) {
            try {
                Object asyncScheduler = getAsyncSchedulerMethod.invoke(Bukkit.getServer());
                asyncRunNowMethod.invoke(asyncScheduler, plugin, (Consumer<Object>) ignored -> task.run());
            } catch (Exception e) {
                throw new RuntimeException("Failed to run async task on Folia", e);
            }
        }

        @Override
        public void runForEntity(Plugin plugin, Entity entity, Runnable task) {
            try {
                Object entityScheduler = getEntitySchedulerMethod.invoke(entity);
                entityRunMethod.invoke(entityScheduler, plugin, (Consumer<Object>) ignored -> task.run(), null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to run entity task on Folia", e);
            }
        }

        @Override
        public TaskWrapper runForEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
            try {
                Object entityScheduler = getEntitySchedulerMethod.invoke(entity);
                Object scheduledTask = entityRunDelayedMethod.invoke(
                    entityScheduler,
                    plugin,
                    (Consumer<Object>) ignored -> task.run(),
                    null,
                    delayTicks
                );
                return new FoliaTaskWrapper(scheduledTask, cancelMethod);
            } catch (Exception e) {
                throw new RuntimeException("Failed to run delayed entity task on Folia", e);
            }
        }
    }

    private static final class BukkitTaskWrapper implements TaskWrapper {
        private final int taskId;

        BukkitTaskWrapper(int taskId) {
            this.taskId = taskId;
        }

        @Override
        public void cancel() {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private static final class FoliaTaskWrapper implements TaskWrapper {
        private final Object scheduledTask;
        private final Method cancelMethod;

        FoliaTaskWrapper(Object scheduledTask, Method cancelMethod) {
            this.scheduledTask = scheduledTask;
            this.cancelMethod = cancelMethod;
        }

        @Override
        public void cancel() {
            try {
                cancelMethod.invoke(scheduledTask);
            } catch (Exception e) {
                throw new RuntimeException("Failed to cancel Folia task", e);
            }
        }
    }

}
