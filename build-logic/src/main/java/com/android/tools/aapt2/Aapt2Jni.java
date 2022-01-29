package com.android.tools.aapt2;

import com.tyron.builder.BuildModule;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.common.util.BinaryExecutor;

import org.openjdk.javax.tools.Diagnostic;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Aapt2Jni {
    
    private static final Pattern DIAGNOSTIC_PATTERN = Pattern.compile("(.*?):(\\d+): (.*?): (.+)");
    private static final Pattern DIAGNOSTIC_PATTERN_NO_LINE = Pattern.compile("(.*?): (.*?)" +
            ": (.+)");

    private static final int LOG_LEVEL_ERROR = 3;
    private static final int LOG_LEVEL_WARNING = 2;
    private static final int LOG_LEVEL_INFO = 1;

    private static final Aapt2Jni INSTANCE = new Aapt2Jni();

    public static Aapt2Jni getInstance() {
        return INSTANCE;
    }

    private String mFailureString;

    private final List<DiagnosticWrapper> mDiagnostics = new ArrayList<>();

    private Aapt2Jni() {

    }

    private static int getLineNumber(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int getLogLevel(String level) {
        if (level == null) {
            return 1;
        }
        switch (level) {
            case "error": return 3;
            case "warning": return 2;
            default:
            case "info": return 1;
        }
    }

    /**
     * Called by AAPT2 through JNI.
     *
     * @param level log level (3 = error, 2 = warning, 1 = info)
     * @param path path to the file with the issue
     * @param line line number of the issue
     * @param message issue message
     */
    @SuppressWarnings({"unused", "SameParameterValue"})
    private void log(int level, String path, long line, String message) {
        DiagnosticWrapper wrapper = new DiagnosticWrapper();
        switch (level) {
            case LOG_LEVEL_ERROR:
                wrapper.setKind(Diagnostic.Kind.ERROR);
                break;
            case LOG_LEVEL_WARNING:
                wrapper.setKind(Diagnostic.Kind.WARNING);
                break;
            case LOG_LEVEL_INFO:
                wrapper.setKind(Diagnostic.Kind.NOTE);
                break;
            default:
                wrapper.setKind(Diagnostic.Kind.OTHER);
                break;
        }
        if (path != null) {
            wrapper.setSource(new File(path));
        }
        wrapper.setLineNumber(line);
        wrapper.setEndLine((int) line);
        wrapper.setStartLine((int) line);
        wrapper.setMessage(message);
        mDiagnostics.add(wrapper);
    }

    private void clearLogs() {
        mDiagnostics.clear();
    }

    /**
     * Compile resources with Aapt2
     * @param args the arguments to pass to aapt2
     * @return exit code, non zero if theres an error
     */
    public static int compile(List<String> args) {
        Aapt2Jni instance = Aapt2Jni.getInstance();
        instance.clearLogs();

        // aapt2 has failed to load, fail early
        if (instance.mFailureString != null) {
            instance.log(LOG_LEVEL_ERROR, null, -1, instance.mFailureString);
            return -1;
        }

        args.add(0, "compile");
        args.add(0, getBinary());
        return executeBinary(args, instance);
    }

    public static int link(List<String> args) {
        Aapt2Jni instance = Aapt2Jni.getInstance();
        instance.clearLogs();

        // aapt2 has failed to load, fail early
        if (instance.mFailureString != null) {
            instance.log(LOG_LEVEL_ERROR, null, -1, instance.mFailureString);
            return -1;
        }

        args.add(0, "link");
        args.add(0, getBinary());

        return executeBinary(args, instance);
    }

    private static String getBinary() {
        return BuildModule.getContext().getApplicationInfo().nativeLibraryDir + "/libaapt2.so";
    }

    private static int executeBinary(List<String> args, Aapt2Jni logger) {
        BinaryExecutor binaryExecutor = new BinaryExecutor();
        binaryExecutor.setCommands(args);
        String execute = binaryExecutor.execute();
        String[] lines = execute.split("\n");
        for (String line : lines) {
            Matcher matcher = DIAGNOSTIC_PATTERN.matcher(line);
            if (matcher.find()) {
                String path = matcher.group(1);
                String lineNumber = matcher.group(2);
                String level = matcher.group(3);
                String message = matcher.group(4);
                logger.log(getLogLevel(level), path, getLineNumber(lineNumber), message);
            } else {
                Matcher m = DIAGNOSTIC_PATTERN_NO_LINE.matcher(line);
                if (m.find()) {
                    String path = matcher.group(1);
                    String level = matcher.group(2);
                    String message = matcher.group(3);
                    logger.log(getLogLevel(level), path, -1, message);
                }
            }
        }
        return logger.mDiagnostics.stream()
                .anyMatch(it -> it.getKind() == Diagnostic.Kind.ERROR)
                ? 1 : 0;
    }

    public static List<DiagnosticWrapper> getLogs() {
        return getInstance().mDiagnostics;
    }
}
