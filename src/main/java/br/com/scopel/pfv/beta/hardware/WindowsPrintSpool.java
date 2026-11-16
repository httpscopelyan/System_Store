package br.com.scopel.pfv.beta.hardware;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class WindowsPrintSpool {

    private static final String PRINTER_ENV = "PDV_PRINTER_NAME";
    private static final String END_MARK = "__PDV_END__";
    private static final long STARTUP_TIMEOUT_MILLIS = 20_000;
    private static final long SCRIPT_TIMEOUT_MILLIS = 15_000;
    private static final long SETTLE_MILLIS = 300;

    private static final String PURGE_AND_CHECK_SCRIPT =
            "Get-PrintJob -PrinterName $n -ErrorAction SilentlyContinue | Remove-PrintJob -ErrorAction SilentlyContinue;"
                    + "$p=Get-Printer -Name $n -ErrorAction SilentlyContinue;"
                    + "if($p -and ($p.WorkOffline -or $p.PrinterStatus -eq 'Offline')){'OFFLINE'}else{'OK'}";

    private static final String WAIT_DRAIN_SCRIPT =
            "Start-Sleep -Milliseconds " + SETTLE_MILLIS + ";$t=0;$r='STUCK';"
                    + "while($t -lt %d){"
                    + "if(@(Get-PrintJob -PrinterName $n -ErrorAction SilentlyContinue).Count -eq 0){$r='DONE';break};"
                    + "Start-Sleep -Milliseconds 250;$t+=250};"
                    + "if($r -eq 'STUCK'){Get-PrintJob -PrinterName $n -ErrorAction SilentlyContinue | Remove-PrintJob -ErrorAction SilentlyContinue};"
                    + "$r";

    private static Session session;
    private static String sessionPrinter;

    private WindowsPrintSpool() {
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    static void warmUp(String printerName) {
        if (!isWindows() || printerName == null || printerName.isBlank()) {
            return;
        }
        Thread thread = new Thread(() -> run(printerName, "'OK'", SCRIPT_TIMEOUT_MILLIS), "print-spool-warmup");
        thread.setDaemon(true);
        thread.start();
    }

    static void clearAndRequireOnline(String printerName) {
        if (!isWindows()) {
            return;
        }
        String result = run(printerName, PURGE_AND_CHECK_SCRIPT, SCRIPT_TIMEOUT_MILLIS);
        if ("OFFLINE".equals(result)) {
            throw new EscPosPrinter.PrinterException(
                    "A impressora está offline. Nada foi enviado; use o botão de imprimir quando ela voltar.", null);
        }
    }

    static void requireJobConsumed(String printerName, long timeoutMillis) {
        if (!isWindows()) {
            return;
        }
        String script = String.format(WAIT_DRAIN_SCRIPT, timeoutMillis);
        String result = run(printerName, script, timeoutMillis + SCRIPT_TIMEOUT_MILLIS);
        if ("STUCK".equals(result)) {
            throw new EscPosPrinter.PrinterException(
                    "A impressora não respondeu e o trabalho foi cancelado. Use o botão de imprimir novamente.", null);
        }
    }

    private static synchronized String run(String printerName, String script, long timeoutMillis) {
        try {
            if (session == null || !session.isAlive() || !printerName.equals(sessionPrinter)) {
                closeSession();
                session = Session.start(printerName);
                sessionPrinter = printerName;
            }
            String result = session.execute(script, timeoutMillis);
            if (result == null) {
                closeSession();
                return "";
            }
            return result;
        } catch (IOException e) {
            closeSession();
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeSession();
            return "";
        }
    }

    private static void closeSession() {
        if (session != null) {
            session.close();
        }
        session = null;
        sessionPrinter = null;
    }

    private static final class Session {

        private final Process process;
        private final Writer input;
        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

        private Session(Process process) {
            this.process = process;
            this.input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        }

        static Session start(String printerName) throws IOException, InterruptedException {
            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-Command", "-");
            builder.environment().put(PRINTER_ENV, printerName);
            builder.redirectErrorStream(true);
            Session created = new Session(builder.start());
            Runtime.getRuntime().addShutdownHook(new Thread(created::close));
            created.startReader();
            String ready = created.execute("$n=$env:" + PRINTER_ENV + ";'OK'", STARTUP_TIMEOUT_MILLIS);
            if (ready == null) {
                created.close();
                throw new IOException("PowerShell não iniciou");
            }
            return created;
        }

        private void startReader() {
            Thread reader = new Thread(() -> {
                try (BufferedReader out = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = out.readLine()) != null) {
                        lines.add(line.trim());
                    }
                } catch (IOException ignored) {
                    return;
                }
            }, "print-spool-reader");
            reader.setDaemon(true);
            reader.start();
        }

        boolean isAlive() {
            return process.isAlive();
        }

        String execute(String script, long timeoutMillis) throws IOException, InterruptedException {
            lines.clear();
            input.write(script + "\n'" + END_MARK + "'\n");
            input.flush();
            String last = "";
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return null;
                }
                String line = lines.poll(remaining, TimeUnit.NANOSECONDS);
                if (line == null) {
                    return null;
                }
                if (END_MARK.equals(line)) {
                    return last;
                }
                if (!line.isEmpty()) {
                    last = line;
                }
            }
        }

        void close() {
            process.destroyForcibly();
        }
    }
}
