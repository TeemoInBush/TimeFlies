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

public class Main {

    public static final String LINE = "--------------------";
    public static final String QUIT_EVENT = "QUIT";
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

    private static void help() {
        System.out.println("quit - 退出\n" +
                "show - 打印\n" +
                "rename - 修改当前任务描述\n" +
                "set - 补充任务，set xxx 12:00-13:00表示任务xxx在12:00开始，13:00结束\n" +
                "done - 当前任务完成\n" +
                "任意数字 - 表示切换当前任务\n" +
                "任意字符串 - 表示新增任务");
    }

    private static void set(String input) {
        String[] args = input.replaceFirst("set", "").trim().split("\\s");
        if (args.length < 2) {
            System.out.println("格式错误，应该输入set xxx start-end / set xxx start / set xxx -end");
            return;
        }
        try {
            String eventName = args[0];
            int index = args[1].indexOf("-");
            LocalDateTime start = convertToTime(index == -1 ? args[1] : args[1].substring(0, index));
            LocalDateTime end = convertToTime(index == -1 ? null : args[1].substring(index + 1));
            if (start == null && end == null) {
                return;
            }
            if (end != null && start != null && !end.isAfter(start)) {
                System.out.println("结束时间需要晚于开始时间！");
                return;
            }
            if (start != null) {
                // 任务开始
                EVENT_LOG_LIST.add(new EventLog(eventName, EventType.START, start));
                if (QUEUE.isEmpty() && end == null) {
                    EVENT_LOG_LIST.add(EventLog.start(eventName));
                }
                if (end == null && !QUEUE.contains(eventName)) {
                    QUEUE.add(eventName);
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
            EVENT_LOG_LIST.sort(Comparator.comparing(EventLog::getTime).thenComparing(e -> e.getType() == EventType.START ? -1 : 1));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static LocalDateTime convertToTime(String time) {
        if (time == null || time.trim().length() == 0) {
            return null;
        }
        String[] times = time.trim().split(":");
        int hour = Integer.parseInt(times[0]);
        int minutes = Integer.parseInt(times[1]);
        return LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minutes));
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
        EVENT_LOG_LIST.removeIf(e -> e.getEventName().equals(QUIT_EVENT));
        if (!EVENT_LOG_LIST.isEmpty() && !QUIT_EVENT.equals(EVENT_LOG_LIST.get(EVENT_LOG_LIST.size() - 1).getEventName())) {
            EVENT_LOG_LIST.add(EventLog.start(QUIT_EVENT));
        }
    }

    private static void show(String input) {
        String date = input.replace("show", "").trim();
        if (date.isEmpty()) {
            printEventLog();
            return;
        }
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyMMdd"));
        try {
            EVENT_LOG_LIST = readObject("event_log_list_" + localDate + ".data");
            printEventLog();
            EVENT_LOG_LIST = readObject(EVENT_LOG_LIST_FILE);
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

    private static void printEventLog() {
        if (EVENT_LOG_LIST.isEmpty()) {
            System.out.println("empty");
            return;
        }
        System.out.println(LINE);
        EventLog lastLog = null;
        AGGS.clear();
        Set<String> doneEvents = new HashSet<>();
        for (int i = 0; i < EVENT_LOG_LIST.size(); i++) {
            EventLog eventLog = EVENT_LOG_LIST.get(i);
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
        if (lastLog != null && !QUIT_EVENT.equals(lastLog.getEventName())) {
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
        return Duration.between(EVENT_LOG_LIST.get(0).getTime(), LocalDateTime.now()).getSeconds();
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
        }
    }

    private static void beforeRun() {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::saveToFile));
        TIMER.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
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
