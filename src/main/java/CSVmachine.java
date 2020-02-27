import javafx.util.Pair;
import org.cloudbus.cloudsim.vms.Vm;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;

public final class CSVmachine {
    // TODO: read these values from external file
    // TODO: set debugging toggle and different colors

    private static final String WRITE_INTERVALS_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/intervalStats.csv";
    private static final String SIM_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/leivaDatas.csv";
    private static final String READ_NMMC_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/transitionMatrix(2history12000intervals).csv";
    private static final String WRITE_NMMC_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/transitionMatrix.csv";
    private int pois;
    private int apps;
    private int samplingInterval;

    public CSVmachine (int pois, int apps, int samplingInterval) {
        this.pois = pois;
        this.apps = apps;
        this.samplingInterval = samplingInterval;

        // Initial Configuration of "Interval Stats" CSV file
        try {
            Files.deleteIfExists(Paths.get(WRITE_INTERVALS_CSV_FILE_LOCATION));
            BufferedWriter br = new BufferedWriter(new FileWriter(WRITE_INTERVALS_CSV_FILE_LOCATION));
            StringBuilder sb = new StringBuilder();
            sb.append("POI,App,Admitted Tasks,Finished Tasks,Average Throughput,Average Response Time\n");
            br.write(sb.toString());
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void formatAndPrintIntervalStats(ArrayList<Vm>[][] vmList, int[][] intervalFinishedTasks, int[][] intervalAdmittedTasks,
                                            double[][] accumulatedResponseTime, double [][][] accumulatedCpuUtil) {
        try {
            BufferedWriter br = new BufferedWriter(new FileWriter(WRITE_INTERVALS_CSV_FILE_LOCATION, true));
            StringBuilder sb = new StringBuilder();

            System.out.printf("%n%n------------------------- INTERVAL INFO --------------------------%n%n");
            System.out.printf(" POI | App | Admitted Tasks| Finished Tasks | Average Throughput | Average Response Time \n");
            for (int poi = 0; poi < pois; poi++) {
                for (int app = 0; app < apps; app++) {
                    // Print in screen
                    System.out.println(String.format("%4s", poi) + " | " + String.format("%3s", app) + " | " +
                            String.format("%14s", intervalAdmittedTasks[poi][app]) + " | " + String.format("%14s",
                            intervalFinishedTasks[poi][app]) + " | " + String.format("%18.2f", intervalFinishedTasks[poi][app]
                            / (double) samplingInterval) + " | " + String.format("%21.2f", accumulatedResponseTime[poi][app] /
                            intervalFinishedTasks[poi][app]));
                    // Print in the CSV file
                    sb.append(poi + "," + app + "," + intervalAdmittedTasks[poi][app] + "," + intervalFinishedTasks[poi][app] +
                            "," + String.format("%.2f", intervalFinishedTasks[poi][app] / (double) samplingInterval) + ","
                            + String.format("%.2f", accumulatedResponseTime[poi][app] / intervalFinishedTasks[poi][app]) + "\n");
                }
            }
            br.write(sb.toString());
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("\n------------------------------------------------------------------\n");
        System.out.printf(" VM | Average CPU Util. \n");
        for (int poi = 0; poi < pois; poi++) {
            for (int app = 0; app < apps; app++) {
                for (int vm = 0; vm < vmList[poi][app].size(); vm++) {
                    System.out.println(String.format("%3s", poi + "" + app + "" + vm) + " | " +
                            String.format("%17.2f", (accumulatedCpuUtil[poi][app][vm] / samplingInterval) * 100));
                }
            }
        }
    }

    public HashMap<String, double[]> readNMMCTransitionMatrixCSV() {
        HashMap<String, double[]> records = new HashMap<>();
        try (Scanner scanner = new Scanner(new File(READ_NMMC_CSV_FILE_LOCATION));) {
            while (scanner.hasNextLine()) {
                Pair<String, double[]> record = getRecordFromLine(scanner.nextLine());
                records.put(record.getKey(), record.getValue());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return records;
    }

    public void createNMMCTransitionMatrixCSV(HashMap<String, int[]> transitionsLog) {
        // Sort HashMap by Keys
        Map<String, int[]> sortedTransitionLog = new TreeMap<>(transitionsLog);

        sortedTransitionLog.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + " -> " + Arrays.toString(entry.getValue()));
        });

        List<String> statesList = new ArrayList<>(sortedTransitionLog.keySet());
        int statesListIterator = 0;
        double[][] transitionMatrix = createNMMCTransitionMatrix(pois, sortedTransitionLog);

        // Write transition matrix to CSV
        try {
            BufferedWriter br = new BufferedWriter(new FileWriter(WRITE_NMMC_CSV_FILE_LOCATION));
            StringBuilder sb = new StringBuilder();
            DecimalFormat df = new DecimalFormat("0.00");
            for (double[] transitionsVector : transitionMatrix) {
                sb.append(statesList.get(statesListIterator));
                sb.append(",");
                statesListIterator++;
                for (double transitionProbability : transitionsVector) {
                    sb.append(df.format(transitionProbability));
                    sb.append(",");
                }
                sb.setLength(sb.length() - 1);
                sb.append("\n");
            }
            br.write(sb.toString());
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Double[][] readSimCSVData() {
        ArrayList<ArrayList<Double>> simTempListData = new ArrayList<>();
        BufferedReader csvReader = null;

        try {
            csvReader = new BufferedReader(new FileReader(SIM_CSV_FILE_LOCATION));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        while (true) {
            String row = "";
            try {
                if (!((row = csvReader.readLine()) != null)) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            String[] str_data = row.split(";");
            int size = str_data.length;
            ArrayList<Double> dbl_data = new ArrayList<>();
            // Convert to doubles
            for(int i = 0; i < size; i++) {
                dbl_data.add(Double.parseDouble(str_data[i]));
            }
//            System.out.println(Arrays.toString(dbl_data.toArray()));
            simTempListData.add(dbl_data);
        }
        try {
            csvReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Convert Arraylist of Arraylists to 2d Array one-liner
        Double[][] simTempArrayData = simTempListData.stream().map(u -> u.toArray(new Double[0])).toArray(Double[][]::new);
//        System.out.println(Arrays.deepToString(simTempArrayData));

        return simTempArrayData;
    }

    private Pair<String, double[]> getRecordFromLine(String line) {
        double[] value = new double[pois];
        String key;
        int i = 0;
        try (Scanner rowScanner = new Scanner(line)) {
            rowScanner.useDelimiter(",");
            // First element is extracted as the key
            key = rowScanner.next();
//            System.out.print(key + " -> ");
            while (rowScanner.hasNext()) {
                value[i] = Double.parseDouble(rowScanner.next());
                i++;
            }
//            System.out.println(Arrays.toString(value));
        }
        return new Pair<>(key, value);
    }

    private double[][] createNMMCTransitionMatrix(int numberOfStates, Map<String, int[]> sortedTransitionLog) {
//        int rows = (int) (1 - Math.pow(numberOfStates, (history + 2))) / (1 - numberOfStates) - 1;
        int rows = sortedTransitionLog.size();
        int columns = numberOfStates;
        int x = 0;
        int y;
//        System.out.println("Rows: " + rows);
//        System.out.println("Columns: " + columns);
        double[][] transitionMatrix = new double[rows][columns];
        for (int[] transitionsVector : sortedTransitionLog.values()) {
            int rowSum = 0;
            y = 0;
            for (int transitionProbability : transitionsVector) {
                rowSum += transitionProbability;
            }
//            System.out.println("RowSum: " + rowSum);
            for (int transitionFrequency : transitionsVector) {
//                System.out.println("X: " + x);
//                System.out.println("Y: " + y);
//                System.out.println("Transition Frequency: " + transitionFrequency);
                transitionMatrix[x][y] = transitionFrequency / (double) rowSum;
//                System.out.println("Transition Probability: " + transitionMatrix[x][y]);
                y++;
            }
            x++;
        }

        System.out.println("\nTransition Matrix: ");
        for (double[] line : transitionMatrix) {
            for (double tile : line) {
                System.out.printf("%.2f ", tile);
            }
            System.out.println();
        }
        System.out.println("-----------");

        return transitionMatrix;
    }
}
