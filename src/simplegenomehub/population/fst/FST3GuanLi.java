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
        for (W w : workers.values()) {
            w.join();
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
}
