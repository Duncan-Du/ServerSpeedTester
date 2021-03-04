import java.io.*;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerSpeedTester {
    public static final String TEST_TARGET_SERVER = "8864"; // CenturyLink Server in Seattle, WA
    public static final String OUTPUT_FILE = "result.txt";
    public static final String REGEX = "[0-9]";
    public static final boolean VERBOSE = true;
    public static final long TIME_OUT = 30000;

    public static void main(String[] args) {
        ProcessBuilder processBuilder = new ProcessBuilder();

        try {
            // get list of servers
            Set<String> servers = getServers(processBuilder);
            Set<Server> serversByDownloadSpeed = new TreeSet<>();
            BufferedWriter output = new BufferedWriter(new FileWriter(new File(OUTPUT_FILE)));

            for (String server : servers) {
                // Disconnect
                processBuilder.command("piactl", "disconnect");
                processBuilder.start();
                System.out.println("Disconnecting");
                connectionStateWaitFor(processBuilder, "Disconnected", TIME_OUT);

                // Attempt to connect to server
                print(output, server);
                System.out.println("Connecting to " + server);
                processBuilder.command("piactl", "set", "region", server);
                processBuilder.start();
                Thread.sleep(2000);
                processBuilder.command("piactl", "connect");
                processBuilder.start();
                Thread.sleep(2000);
                String state = connectionStateWaitFor(processBuilder, "Connected", 20000);
                if (state.equals("Connected")) {
                    // run speed test
                    processBuilder.command("speedtest", "--progress=no", "--server-id=" + TEST_TARGET_SERVER);
                    System.out.println("Running speed test for server " + server);
                    Process process = processBuilder.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line = reader.readLine();
                    Thread.sleep(2000);
                    long startTime = System.currentTimeMillis();
                    while (line != null && line.isEmpty() && System.currentTimeMillis() - startTime < TIME_OUT * 2) {
                        Thread.sleep(2000);
                        line = reader.readLine();
                    }
                    if (line != null && line.isEmpty()) {
                        processBuilder = new ProcessBuilder();
                        print(output, "Unexpected Output / Speed Test Initialization Timed Out");
                    } else {
                        boolean timedOut = false;
                        startTime = System.currentTimeMillis();
                        while (line != null && !timedOut) {
                            if (line.contains("[error]")) {
                                print(output, line);
                                break;
                            }
                            if (line.contains("Latency") || line.contains("Download")
                                    || line.contains("Upload") || line.contains("Packet Loss")) {
                                if (line.contains("Download")) {
                                    Pattern p = Pattern.compile(REGEX);
                                    Matcher m = p.matcher(line);
                                    if (m.find()) {
                                        int start = m.start();
                                        int end = line.indexOf(" Mbps");
                                        assert end > start;
                                        double downloadSpeed = Double.parseDouble(line.substring(start, end));
                                        Server s = new Server(server, downloadSpeed);
                                        serversByDownloadSpeed.add(s);
                                    }
                                }
                                print(output, line);
                            }
                            line = reader.readLine();
                            timedOut = System.currentTimeMillis() - startTime > TIME_OUT * 8;
                        }
                        if (timedOut) {
                            processBuilder = new ProcessBuilder();
                            print(output, "Unexpected Output / Speed Test Initialization Timed Out");
                        }
                    }

                } else {
                    print(output, state);
                    print(output, "Connection Failed after " + TIME_OUT/1000 + " seconds.");
                }
                print(output, "");
            }
            print(output, "Servers ranked by download speed:");
            for (Server s : serversByDownloadSpeed) {
                print(output, s.toString());
            }
            output.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    public static void print(BufferedWriter output, String line) throws IOException {
        output.write(line + "\n");
        if (VERBOSE) {
            System.out.println(line);
        }
    }

    /**
     * Calls "piactl get connectionstate" and waits for targetState for
     * maxWaitTime.
     * @param processBuilder    OS Process
     * @param targetState       State to wait for
     * @param maxWaitTime       The Maximum amount of time to wait, in ms
     * @return the most recent state if maxWaitTime is reached and targetState
     * did not appear. Returns targetState otherwise.
     * @throws IOException
     * @throws InterruptedException
     */
    public static String connectionStateWaitFor(ProcessBuilder processBuilder,
                                                String targetState,
                                                long maxWaitTime)
            throws IOException,InterruptedException {
        long startTime = System.currentTimeMillis();
        String state;
        long endTime;
        do {
            processBuilder.command("piactl", "get", "connectionstate");
            Process process = processBuilder.start();
            Thread.sleep(2000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            state = reader.readLine();
            endTime = System.currentTimeMillis();
            System.out.print(".");
        } while ((!targetState.equals(state)) && endTime - startTime < maxWaitTime);
        System.out.println();
        return state;
    }



    /**
     * Retrieves a set of servers
     * @param processBuilder    OS Process
     * @return                  a set of servers as strings
     */
    public static Set<String> getServers(ProcessBuilder processBuilder) throws IOException {
        processBuilder.command("piactl", "get", "regions");

        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String server = reader.readLine();
        Set<String> serverSet = new TreeSet<>();
        while (server != null) {
            serverSet.add(server);
            server = reader.readLine();
        }
        return serverSet;
    }
}
