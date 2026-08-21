package myau.event;

import myau.event.events.Event;
import myau.event.events.EventStoppable;
import myau.event.types.Priority;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event 分发器。
 *
 * 与原实现（DarkMagician6 的 EventAPI）相比，性能重构要点：
 * 1. 原实现使用 CopyOnWriteArrayList，每次迭代都会复制整个数组；
 *    事件分发是每帧几十次、每次上百监听方法的热点路径，复制开销巨大。
 *    现在注册完成后冻结为排序的只读快照数组，分发时仅遍历数组。
 * 2. 原实现分发时每次对 List 进行 contains/排序；现在排序只发生在注册阶段。
 * 3. 注册表与快照均使用 ConcurrentHashMap，避免分发与注册并发时的不安全访问。
 */
public final class EventManager {
    /**
     * 以事件类型为键的可变注册表，仅注册/注销阶段修改。
     */
    private static final Map<Class<? extends Event>, List<MethodData>> REGISTRY_MAP = new ConcurrentHashMap<>();

    /**
     * 以事件类型为键的只读快照，由 REGISTRY_MAP 派生，注册/注销后重建。
     */
    private static final Map<Class<? extends Event>, MethodData[]> SNAPSHOT_MAP = new ConcurrentHashMap<>();

    private EventManager() {
    }

    /**
     * 注册目标类中所有标注了 @EventTarget 的方法。
     */
    public static void register(Object object) {
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (!isMethodBad(method)) {
                register(method, object);
            }
        }
    }

    /**
     * 注册目标类中标注了 @EventTarget 且参数为指定事件类型的方法。
     */
    public static void register(Object object, Class<? extends Event> eventClass) {
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (!isMethodBad(method, eventClass)) {
                register(method, object);
            }
        }
    }

    /**
     * 注销目标对象注册的所有监听方法。
     */
    public static void unregister(Object object) {
        boolean changed = false;
        for (final List<MethodData> dataList : REGISTRY_MAP.values()) {
            for (final Iterator<MethodData> it = dataList.iterator(); it.hasNext(); ) {
                if (it.next().getSource().equals(object)) {
                    it.remove();
                    changed = true;
                }
            }
        }
        if (changed) {
            rebuildAllSnapshots();
            cleanMap(true);
        }
    }

    /**
     * 注销目标对象中指定事件类型的监听方法。
     */
    public static void unregister(Object object, Class<? extends Event> eventClass) {
        final List<MethodData> dataList = REGISTRY_MAP.get(eventClass);
        if (dataList == null) {
            return;
        }
        boolean changed = false;
        for (final Iterator<MethodData> it = dataList.iterator(); it.hasNext(); ) {
            if (it.next().getSource().equals(object)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            rebuildSnapshot(eventClass);
            cleanMap(true);
        }
    }

    /**
     * 注册一个监听方法。
     *
     * @param method 标注了 @EventTarget 的方法
     * @param object 监听对象（方法所属实例）
     */
    private static void register(Method method, Object object) {
        final Class<? extends Event> indexClass = (Class<? extends Event>) method.getParameterTypes()[0];
        final MethodData data = new MethodData(object, method, method.getAnnotation(EventTarget.class).value());
        if (!data.getTarget().isAccessible()) {
            data.getTarget().setAccessible(true);
        }
        List<MethodData> list = REGISTRY_MAP.get(indexClass);
        if (list == null) {
            list = new ArrayList<>();
            REGISTRY_MAP.put(indexClass, list);
        }
        if (!list.contains(data)) {
            list.add(data);
            rebuildSnapshot(indexClass);
        }
    }

    /**
     * 移除事件类型对应的注册表条目及其快照。
     */
    public static void removeEntry(Class<? extends Event> indexClass) {
        REGISTRY_MAP.remove(indexClass);
        SNAPSHOT_MAP.remove(indexClass);
    }

    /**
     * 清理空条目。仅当只清理空条目时保留非空列表。
     *
     * @param onlyEmptyEntries true 时仅删除监听方法为空的条目，否则删除全部
     */
    public static void cleanMap(boolean onlyEmptyEntries) {
        final Iterator<Map.Entry<Class<? extends Event>, List<MethodData>>> mapIterator = REGISTRY_MAP.entrySet().iterator();
        while (mapIterator.hasNext()) {
            final Map.Entry<Class<? extends Event>, List<MethodData>> entry = mapIterator.next();
            if (!onlyEmptyEntries || entry.getValue().isEmpty()) {
                mapIterator.remove();
                SNAPSHOT_MAP.remove(entry.getKey());
            }
        }
    }

    /**
     * 基于当前注册表重建所有事件类型的快照。
     */
    private static void rebuildAllSnapshots() {
        for (final Map.Entry<Class<? extends Event>, List<MethodData>> entry : REGISTRY_MAP.entrySet()) {
            SNAPSHOT_MAP.put(entry.getKey(), toSortedArray(entry.getValue()));
        }
    }

    /**
     * 重建单个事件类型的快照。
     */
    private static void rebuildSnapshot(Class<? extends Event> indexClass) {
        final List<MethodData> list = REGISTRY_MAP.get(indexClass);
        SNAPSHOT_MAP.put(indexClass, toSortedArray(list));
    }

    /**
     * 将监听方法列表转换为按优先级排序的只读数组。
     */
    private static MethodData[] toSortedArray(List<MethodData> list) {
        if (list == null || list.isEmpty()) {
            return new MethodData[0];
        }
        final MethodData[] snapshot = list.toArray(new MethodData[list.size()]);
        final MethodData[] sorted = new MethodData[snapshot.length];
        int offset = 0;
        for (final byte priority : Priority.VALUE_ARRAY) {
            for (final MethodData data : snapshot) {
                if (data.getPriority() == priority) {
                    sorted[offset++] = data;
                }
            }
        }
        return sorted;
    }

    private static boolean isMethodBad(Method method) {
        return method.getParameterTypes().length != 1 || !method.isAnnotationPresent(EventTarget.class);
    }

    private static boolean isMethodBad(Method method, Class<? extends Event> eventClass) {
        return isMethodBad(method) || !method.getParameterTypes()[0].equals(eventClass);
    }

    /**
     * 分发一个事件，按优先级顺序调用所有监听方法。
     * 若事件为 EventStoppable，一旦被停止则中断后续调用。
     */
    public static Event call(final Event event) {
        MethodData[] dataList = SNAPSHOT_MAP.get(event.getClass());
        if (dataList == null) {
            dataList = rebuildOnDemand(event.getClass());
        }
        if (dataList.length == 0) {
            return event;
        }
        if (event instanceof EventStoppable) {
            final EventStoppable stoppable = (EventStoppable) event;
            for (final MethodData data : dataList) {
                invoke(data, event);
                if (stoppable.isStopped()) {
                    break;
                }
            }
        } else {
            for (final MethodData data : dataList) {
                invoke(data, event);
            }
        }
        return event;
    }

    /**
     * 分发时若发现快照缺失（如注册发生在分发线程），按需从注册表重建。
     */
    private static MethodData[] rebuildOnDemand(Class<? extends Event> eventClass) {
        final List<MethodData> list = REGISTRY_MAP.get(eventClass);
        final MethodData[] rebuilt = toSortedArray(list);
        SNAPSHOT_MAP.put(eventClass, rebuilt);
        return rebuilt;
    }

    private static void invoke(MethodData data, Event argument) {
        try {
            data.getTarget().invoke(data.getSource(), argument);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * 单个监听方法的元数据。
     */
    private static final class MethodData {
        private final Object source;
        private final Method target;
        private final byte priority;

        public MethodData(Object source, Method target, byte priority) {
            this.source = source;
            this.target = target;
            this.priority = priority;
        }

        public Object getSource() {
            return source;
        }

        public Method getTarget() {
            return target;
        }

        public byte getPriority() {
            return priority;
        }
    }
}
