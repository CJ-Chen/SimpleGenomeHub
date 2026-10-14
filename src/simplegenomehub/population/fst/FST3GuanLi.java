package simplegenomehub.population.fst;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * FST3GuanLi - 线程池管理和任务分配类
 * 负责管理并行计算的Worker线程，分配任务
 */
public class FST3GuanLi {

    /**
     * Worker接口（统一管理start、join、stop方法）
     */
    public interface Worker {
        void start();
        void join() throws InterruptedException;
        default Throwable getFailure() { return null; }
    }

    /**
     * 队列模式的Worker接口（额外需要stop方法）
     */
    public interface QueueWorker extends Worker {
        void stop() throws InterruptedException;
    }

    /**
     * 启动所有Worker
     */
    public static <W extends Worker> void startAllWorkers(LinkedHashMap<String, W> workers) {
        for (W w : workers.values()) {
            w.start();
        }
    }

    /**
     * 等待所有Worker完成
     */
    public static <W extends Worker> void joinAllWorkers(LinkedHashMap<String, W> workers) throws InterruptedException {
        boolean interrupted = false;
        for (W w : workers.values()) {
            boolean joined = false;
            while (!joined) {
                try { w.join(); joined = true; }
                catch (InterruptedException failure) { interrupted = true; }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("FST interrupted after waiting for all child workers");
        }
    }

    /**
     * 停止所有队列Worker
     */
    public static <W extends QueueWorker> void stopAllWorkers(LinkedHashMap<String, W> workers) throws InterruptedException {
        for (W w : workers.values()) {
            w.stop();
        }
    }
    public static <W extends Worker> void checkFailures(LinkedHashMap<String, W> workers) throws java.io.IOException {
        for (java.util.Map.Entry<String, W> entry : workers.entrySet()) {
            if (entry.getValue().getFailure() != null)
                throw new java.io.IOException("Chromosome calculation failed: " + entry.getKey(), entry.getValue().getFailure());
        }
    }

    /** Always deliver termination and join every real thread, including on producer failure. */
    public static <W extends QueueWorker> void finishQueues(LinkedHashMap<String, W> workers) throws Exception {
        boolean interrupted = Thread.interrupted();
        for (W worker : workers.values()) {
            boolean stopped = false;
            while (!stopped) {
                try { worker.stop(); stopped = true; }
                catch (InterruptedException failure) { interrupted = true; }
            }
        }
        try { joinAllWorkers(workers); }
        catch (InterruptedException failure) { interrupted = true; }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("FST interrupted after child cleanup");
        }
        checkFailures(workers);
    }
}
