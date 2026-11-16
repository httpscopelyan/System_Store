package br.com.scopel.pfv.beta.hardware;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class WindowsPrintSpool {

    private static final String PRINTER_ENV = "PDV_PRINTER_NAME";
    private static final long SCRIPT_TIMEOUT_SECONDS = 15;

    private static final String PURGE_AND_CHECK_SCRIPT =
            "$n=$env:PDV_PRINTER_NAME;"
                    + "Get-PrintJob -PrinterName $n -ErrorAction SilentlyContinue | Remove-PrintJob -ErrorAction SilentlyContinue;"
                    + "$p=Get-Printer -Name $n -ErrorAction SilentlyContinue;"
                    + "if($p -and ($p.WorkOffline -or $p.PrinterStatus -eq 'Offline')){'OFFLINE'}else{'OK'}";

    private static final String WAIT_DRAIN_SCRIPT =
            "$n=$env:PDV_PRINTER_NAME;$limit=%d;$t=0;"
                    + "while($t -lt $limit){"
                    + "$c=@(Get-PrintJob -PrinterName $n -ErrorAction SilentlyContinue).Count;"
                    + "if($c -eq 0){'DONE';exit};Start-Sleep -Milliseconds 250;$t+=250};"
                    + "Get-PrintJob -PrinterName $n -ErrorAction SilentlyContinue | Remove-PrintJob -ErrorAction SilentlyContinue;"
                    + "'STUCK'";

    private WindowsPrintSpool() {
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    static void clearAndRequireOnline(String printerName) {
        if (!isWindows()) {
            return;
        }
        String result = run(printerName, PURGE_AND_CHECK_SCRIPT);
        if ("OFFLINE".equals(result)) {
            throw new EscPosPrinter.PrinterException(
                    "A impressora está offline. Nada foi enviado; use o botão de imprimir quando ela voltar.", null);
        }
    }

    static void requireJobConsumed(String printerName, long timeoutMillis) {
        if (!isWindows()) {
            return;
        }
        String result = run(printerName, String.format(WAIT_DRAIN_SCRIPT, timeoutMillis));
        if ("STUCK".equals(result)) {
            throw new EscPosPrinter.PrinterException(
                    "A impressora não respondeu e o trabalho foi cancelado. Use o botão de imprimir novamente.", null);
        }
    }

    private static String run(String printerName, String script) {
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", script);
        builder.environment().put(PRINTER_ENV, printerName);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return output.lines().map(String::trim).filter(line -> !line.isEmpty())
                    .reduce((first, second) -> second).orElse("");
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
