package com.company;

import com.company.pojo.EventLog;
import com.company.pojo.enums.EventType;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.company.pojo.EventLog.QUIT_EVENT;

public class Main {

    public static final String LINE = "--------------------";
    private static ArrayList<String> QUEUE;
    private static List<EventLog> EVENT_LOG_LIST;
    private static final String EVENT_LOG_LIST_FILE = "event_log_list_" + LocalDate.now() + ".data";
    private static final String QUEUE_FILE = "queue_" + LocalDate.now() + ".data";
    private static final Map<String, AtomicLong> AGGS = new HashMap<>();
    private static final Timer TIMER = new Timer();
    public static String ROOT_PATH = "";



    public static void main(String[] args) {
        System.out.println(getWelcomeWord());
        if (args.length > 0) {
            ROOT_PATH = args[0];
        }
        loadData();
        beforeRun();
        try {
            run();
        } catch (Exception e) {
            e.printStackTrace();
        }
        TIMER.cancel();
    }

    private static void run() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            if (input.isBlank()) {
                // do nothing
                continue;
            } else if ("quit".equals(input)) {
                // quit表示退出
                beforeQuit();
                break;
            } else if (input.startsWith("show")) {
                // show表示打印日志
                show(input);
            } else if (input.startsWith("rename")) {
                // rename表示修改当前任务描述
                rename(input);
            } else if (input.startsWith("set")) {
                // set表示补信息，set xxx 12:00 13:00表示任务xxx在12:00开始，13:00结束
                set(input);
            } else if ("done".equals(input)) {
                // done表示现在完成
                done();
            } else if (input.startsWith("help")) {
                // help表单打印帮助日志
                help();
            } else if (input.startsWith("del")) {
                // del表示删除
                del(input);
            }  else {
                try {
                    // 数字表示移动
                    int num = Integer.parseInt(input);
                    moveEvent(num);
                } catch (NumberFormatException ignored) {
                    // 字符串表示新增
                    addEvent(input);
                }
            }
            printQueue();
        }
    }

    private static void del(String input) {
        String eventName = input.replaceFirst("del", "").trim();
        EVENT_LOG_LIST.removeIf(e -> e.getEventName().equals(eventName));
        QUEUE.remove(eventName);
    }

    private static void help() {
        System.out.println("quit - 退出\n" +
                "show - 打印任务\n" +
                "rename - 修改任务描述，例如rename a，表示修改当前任务名称为a\n" +
                "del - 删除任务，例如del xxx\n" +
                "set - 补充任务，set xxx 12:00-13:00表示任务在12:00开始，13:00结束\n" +
                "       set xxx 12:00表示在12:00新增任务的开始事件\n" +
                "       set xxx -13:00表示在13:00新增任务的结束事件\n" +
                "       set xxx 220106T22:00-220106T2201新增跨天的任务\n" +
                "done - 任务完成\n" +
                "任意数字 - 切换任务\n" +
                "任意字符串 - 新增任务");
    }

    private static void set(String input) {
        String[] args = input.replaceFirst("set", "").trim().split("\\s");
        if (args.length < 2) {
            System.out.println("格式错误，应该输入set xxx start-end / set xxx start / set xxx -end");
            return;
        }
        try {
            String eventName = args[0];
            String timeStr = input.replaceFirst("set", "").replaceFirst(eventName, "").trim();
            int index = timeStr.indexOf("-");
            LocalDateTime start = convertToTime(index == -1 ? timeStr : timeStr.substring(0, index));
            LocalDateTime end = convertToTime(index == -1 ? null : timeStr.substring(index + 1));
            if (checkArgs(eventName, start, end)) {
                return;
            }
            if (start != null) {
                // 任务开始
                EVENT_LOG_LIST.add(new EventLog(eventName, EventType.START, start));
                if (isNotEnd(eventName) && end == null) {
                    if (QUEUE.isEmpty()) {
                        EVENT_LOG_LIST.add(EventLog.start(eventName));
                    }
                    if (!QUEUE.contains(eventName)) {
                        QUEUE.add(eventName);
                    }
                }
            }
            if (end != null) {
                // 任务结束
                QUEUE.remove(eventName);
                EVENT_LOG_LIST.add(new EventLog(eventName, EventType.END, end));
                if (!QUEUE.isEmpty()) {
                    String next = QUEUE.get(0);
                    EVENT_LOG_LIST.add(EventLog.start(next));
                }
            }
            EVENT_LOG_LIST.sort(Comparator.comparing(EventLog::getTime).thenComparing(e -> e.getType() == EventType.START ? 1 : -1));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean checkArgs(String eventName, LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) {
            return true;
        }
        if (end != null && start != null && !end.isAfter(start)) {
            System.out.println("结束时间需要晚于开始时间！");
            return true;
        }
        if (end != null && end.isAfter(LocalDateTime.now())) {
            System.out.println("结束时间不能晚于当前时间！");
            return true;
        }
        if (start != null && end != null) {
            for (EventLog eventLog : EVENT_LOG_LIST) {
                if (eventLog.getTime().isAfter(start) && eventLog.getTime().isBefore(end)) {
                    System.out.println("开始时间和结束时间中间有其他事件" + eventLog);
                    return true;
                }
            }
        }
        if (start == null && end != null) {
            if (EVENT_LOG_LIST.stream().noneMatch(e -> e.getEventName().equals(eventName))) {
                System.out.println("任务没有开始过，不能设置结束时间");
                return true;
            }
            for (EventLog eventLog : EVENT_LOG_LIST) {
                if (eventLog.getEventName().equals(eventName) && eventLog.getTime().isAfter(end)) {
                    System.out.println("任务的结束时间不能早于开始时间" + eventLog);
                    return true;
                }
            }
        }
        if (!isNotEnd(eventName)) {
            if (end != null) {
                System.out.println("任务已经结束，不能再添加结束事件");
                return true;
            }
            if (start != null) {
                for (EventLog eventLog : EVENT_LOG_LIST) {
                    if (eventLog.getEventName().equals(eventName) && eventLog.getType() == EventType.END && eventLog.getTime().isBefore(start)) {
                        System.out.println("任务已经在" + eventLog.getTime() + "结束，不能在结束时间后添加开始事件。");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isNotEnd(String eventName) {
        for (EventLog eventLog : EVENT_LOG_LIST) {
            if (eventLog.getEventName().equals(eventName) && eventLog.getType() == EventType.END) {
                return false;
            }
        }
        return true;
    }

    private static LocalDateTime convertToTime(String time) {
        if (time == null || time.trim().length() == 0) {
            return null;
        }
        if (time.length() == 5) {
            String[] times = time.trim().split(":");
            int hour = Integer.parseInt(times[0]);
            int minutes = Integer.parseInt(times[1]);
            return LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minutes));
        }
        if (time.length() == 12) {
            return LocalDateTime.parse(time.replace("T", " "), DateTimeFormatter.ofPattern("yyMMdd HH:mm"));
        }
        System.err.println("time format error " + time);
        return null;
    }

    private static void rename(String input) {
        if (QUEUE.isEmpty()) {
            return;
        }
        String newName = input.replace("rename ", "");
        String oldName = QUEUE.get(0);
        QUEUE.set(0, newName);
        for (EventLog eventLog : EVENT_LOG_LIST) {
            if (eventLog.getEventName().equals(oldName)) {
                eventLog.setEventName(newName);
            }
        }
    }

    private static void done() {
        String removed = QUEUE.remove(0);
        EVENT_LOG_LIST.add(EventLog.end(removed));
        if (!QUEUE.isEmpty()) {
            String next = QUEUE.get(0);
            EVENT_LOG_LIST.add(EventLog.start(next));
        }
    }

    private static void beforeQuit() {
        EVENT_LOG_LIST.removeIf(EventLog::isQuit);
        EVENT_LOG_LIST.add(EventLog.quit());
    }

    private static void show(String input) {
        String date = input.replace("show", "").trim();
        if (date.isEmpty()) {
            printEventLog(EVENT_LOG_LIST);
            return;
        }
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyMMdd"));
        try {
            List<EventLog> eventLogs = readObject("event_log_list_" + localDate + ".data");
            printEventLog(eventLogs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addEvent(String input) {
        if (QUEUE.contains(input)) {
            return;
        }

        QUEUE.add(0, input);
        EVENT_LOG_LIST.add(EventLog.start(input));
    }

    private static void moveEvent(int num) {
        if (num <= 0 || num >= QUEUE.size()) {
            return;
        }

        String removed = QUEUE.remove(num);
        QUEUE.add(0, removed);
        EVENT_LOG_LIST.add(EventLog.start(removed));
    }

    private static void printQueue() {
        System.out.println(LINE);
        for (int i = 0; i < QUEUE.size(); i++) {
            if (i == 0) {
                System.out.println("-- " + QUEUE.get(i) + " --");
                System.out.println(LINE);
            } else {
                System.out.println("[" + i + "] " + QUEUE.get(i));
            }
        }
        System.out.println(LINE);
    }

    private static void printEventLog(List<EventLog> eventLogs) {
        if (eventLogs.isEmpty()) {
            System.out.println("empty");
            return;
        }
        System.out.println(LINE);
        EventLog lastLog = null;
        AGGS.clear();
        Set<String> doneEvents = new HashSet<>();
        for (int i = 0; i < eventLogs.size(); i++) {
            EventLog eventLog = eventLogs.get(i);
            if (!isSame(lastLog, eventLog)) {
                System.out.print(isSameTime(lastLog, eventLog) ? eventLog.print() : "\n" + eventLog);
            }
            if (lastLog != null && !doneEvents.contains(lastLog.getEventName())) {
                addToAggs(eventLog, lastLog);
            }
            if (eventLog.getType() == EventType.END) {
                doneEvents.add(eventLog.getEventName());
            }
            lastLog = eventLog;
        }
        if (lastLog != null && !lastLog.isQuit()) {
            addToAggs(EventLog.end(lastLog.getEventName()), lastLog);
        }
        System.out.println("\n" + LINE);
        printAggs(doneEvents);
        System.out.println("total: " + printTime(getTotalCost()));
        System.out.println(LINE);
    }

    private static boolean isSameTime(EventLog lastLog, EventLog eventLog) {
        return lastLog != null && lastLog.getTime().isEqual(eventLog.getTime());
    }

    private static boolean isSame(EventLog lastLog, EventLog eventLog) {
        return lastLog != null && lastLog.getType() == eventLog.getType()
                && lastLog.getEventName().equals(eventLog.getEventName());
    }

    private static void printAggs(Set<String> doneEvents) {
        AtomicInteger i = new AtomicInteger(0);
        AGGS.entrySet().stream()
                .filter(e -> !e.getKey().equals(QUIT_EVENT))
                .sorted(Comparator.comparingLong(e -> (!doneEvents.contains(e.getKey()) ? 1000000000 : 0 ) + e.getValue().longValue()))
                .forEach(
                entry -> {
                    i.incrementAndGet();
                    String name = entry.getKey();
                    long time = entry.getValue().longValue();
                    name = (doneEvents.contains(name) ? " " : "+") + name;
                    System.out.println("[" + i + "] \t" + printTime(time) + "\t" + getPercent(time) + "\t" + name);
                }
        );
    }

    private static String getPercent(long time) {
        return String.format("%.1f", time * 100f / getTotalCost()) + "%";
    }

    private static String printTime(long time) {
        if (time >= 3600) {
            return String.format("%.1f", time / 3600f) + "h";
        } else if (time >= 60) {
            return String.format("%.1f", time / 60f) + "m";
        } else {
            return time + "s";
        }
    }

    private static long getTotalCost() {
        return Duration.between(EVENT_LOG_LIST.get(0).getTime(), EVENT_LOG_LIST.get(EVENT_LOG_LIST.size() - 1).getTime()).getSeconds();
    }

    private static void addToAggs(EventLog eventLog, EventLog lastLog) {
        if (lastLog != null && lastLog.getType() == EventType.START) {
            Duration duration = Duration.between(lastLog.getTime(), eventLog.getTime());
            AGGS.computeIfAbsent(lastLog.getEventName(), k -> new AtomicLong()).addAndGet(duration.getSeconds());
        }
    }

    private static <T> T readObject(String fileName) throws IOException, ClassNotFoundException {
        try (final FileInputStream fileInputStream = new FileInputStream(ROOT_PATH + fileName)) {
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            return (T) objectInputStream.readObject();
        }
    }

    private static <T> void writeObject(T object, String fileName) throws IOException {
        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(ROOT_PATH + fileName))) {
            outputStream.writeObject(object);
        } catch (Exception e) {
            System.out.println("write file failed" + e);
        }
    }

    private static void beforeRun() {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::saveToFile));
        TIMER.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                beforeQuit();
                saveToFile();
            }
        }, 2000L, 2000L);
        if (!QUEUE.isEmpty()) {
            EVENT_LOG_LIST.add(EventLog.start(QUEUE.get(0)));
        }
    }

    private static void saveToFile() {
        try {
            writeObject(EVENT_LOG_LIST, EVENT_LOG_LIST_FILE);
            writeObject(QUEUE, QUEUE_FILE);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void loadData() {
        try {
            System.out.println("文件存储在：" + Path.of(ROOT_PATH).toAbsolutePath());
            EVENT_LOG_LIST = readObject(EVENT_LOG_LIST_FILE);
            QUEUE = readObject(QUEUE_FILE);
        } catch (Exception e) {
            System.out.println("no data to load");
            EVENT_LOG_LIST = new ArrayList<>();
            QUEUE = new ArrayList<>();
        }
    }

    private static String getWelcomeWord() {
        if (LocalDate.now().getMonth().getValue() == 12 && LocalDate.now().getDayOfMonth() >= 30
        || (LocalDate.now().getMonth().getValue() == 1 && LocalDate.now().getDayOfMonth() <= 4)) {
            return " | |__   __ _ _ __  _ __  _   _   _ __   _____      __  _   _  ___  __ _ _ __ \n" +
                    " | '_ \\ / _` | '_ \\| '_ \\| | | | | '_ \\ / _ \\ \\ /\\ / / | | | |/ _ \\/ _` | '__|\n" +
                    " | | | | (_| | |_) | |_) | |_| | | | | |  __/\\ V  V /  | |_| |  __/ (_| | |   \n" +
                    " |_| |_|\\__,_| .__/| .__/ \\__, | |_| |_|\\___| \\_/\\_/    \\__, |\\___|\\__,_|_|   \n" +
                    "             |_|   |_|    |___/                         |___/                ";
        }
        return "  _    _                           ______                        _             \n" +
                " | |  | |                         |  ____|                      | |            \n" +
                " | |__| | __ _ _ __  _ __  _   _  | |____   _____ _ __ _   _  __| | __ _ _   _ \n" +
                " |  __  |/ _` | '_ \\| '_ \\| | | | |  __\\ \\ / / _ \\ '__| | | |/ _` |/ _` | | | |\n" +
                " | |  | | (_| | |_) | |_) | |_| | | |___\\ V /  __/ |  | |_| | (_| | (_| | |_| |\n" +
                " |_|  |_|\\__,_| .__/| .__/ \\__, | |______\\_/ \\___|_|   \\__, |\\__,_|\\__,_|\\__, |\n" +
                "              | |   | |     __/ |                       __/ |             __/ |\n" +
                "              |_|   |_|    |___/                       |___/             |___/ ";
    }

}
