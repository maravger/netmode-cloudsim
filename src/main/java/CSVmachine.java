import javafx.util.Pair;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.*;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.*;

public final class CSVmachine implements SignalHandler{
    // TODO: read these values from external file
    // TODO: set debugging toggle and different colors

    private static final String WRITE_INTERVALS_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/intervalStats.csv";
    private static final String SIM_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/leivaDatas.csv";
    // private static final String READ_NMMC_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/transitionMatrix(2history12000intervals).csv";
    private static final String READ_NMMC_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/transitionMatrix(realDS).csv";
    private static final String WRITE_NMMC_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/transitionMatrix.csv";
    private int pois;
    private int apps;
    private int samplingInterval;
    private String timeStamp;
    private HashMap<String, double[]> predictionStats;
    private HashMap<String, double[]> mrfStats;

    public CSVmachine (int pois, int apps, int samplingInterval) {
        this.pois = pois;
        this.apps = apps;
        this.samplingInterval = samplingInterval;
        this.timeStamp = String.valueOf(new Date());
        this.predictionStats = new HashMap<>();
        this.mrfStats = new HashMap<>();
    }

    public void listenTo(String signalName) {
        Signal signal = new Signal(signalName);
        Signal.handle(signal, this);
    }

    public void handle(Signal signal) {
        System.out.println("Signal: " + signal);
        if (signal.toString().trim().equals("SIGINT")) {
            System.out.println("SIGINT raised, archiving and terminating...");

            archiveSimulationCSVs();

            System.exit(1);
        }
    }

    public void formatPrintAndArchiveIntervalStats(int intervalNo, double[][] intervalPredictedTasks,
                                                   int[][] intervalFinishedTasks, int[][] intervalAdmittedTasks,
                                                   double[][] accumulatedResponseTime, HashMap<Long, Double> accumulatedCpuUtil,
                                                   int[] poiAllocatedCores, int[] poiPowerConsumption,
                                                   int[][] allocatedUsers, int[][] allocatedCores, double[][] avgSinr,
                                                   double avgResidualEnergy, int[][] predictedUsers, int[][] intervalViolations,
                                                   int[] optimalPowerConsumption, double[][] residualWorkload) {
        // Print to console
        System.out.printf("%n%n------------------------- INTERVAL INFO --------------------------%n%n");
        System.out.printf(" POI | App | Admitted Tasks | Finished Tasks | Average Throughput | Average Response Time \n");
        for (int poi = 0; poi < this.pois; poi++) {
            for (int app = 0; app < this.apps; app++) {
                // Print in screen
                System.out.println(String.format("%4s", poi) + " | " + String.format("%3s", app) + " | " +
                        String.format("%14s", intervalAdmittedTasks[poi][app]) + " | " + String.format("%14s",
                        intervalFinishedTasks[poi][app]) + " | " + String.format("%18.2f", intervalFinishedTasks[poi][app]
                        / (double) this.samplingInterval) + " | " + String.format("%21.2f", accumulatedResponseTime[poi][app] /
                        intervalFinishedTasks[poi][app]));
            }
        }
        SortedSet<Long> vmIDs = new TreeSet<>(accumulatedCpuUtil.keySet());
        System.out.println("\n------------------------------------------------------------------\n");
        System.out.printf("   VM | Average CPU Util. \n");
        for (Long vmID : vmIDs) {
            System.out.println(String.format("%5s", vmID) + " | " +
            String.format("%17.2f", (accumulatedCpuUtil.get(vmID) / this.samplingInterval) * 100));
        }

        //Update CSVs
        System.out.println("...Updating Interval Prediction CSVs");
        this.updateIntervalPredictionCSVs(intervalPredictedTasks, intervalAdmittedTasks, predictedUsers, allocatedUsers,
                intervalNo);

        System.out.println("...Updating Interval App Performance CSVs");
        this.updateIntervalAppPerformanceCSVs(intervalNo, intervalAdmittedTasks, intervalFinishedTasks,
                accumulatedResponseTime, allocatedUsers, allocatedCores, avgSinr, avgResidualEnergy, residualWorkload);

        System.out.println("...Updating Interval Host Performance CSVs");
        this.updateIntervalPoiPerformanceCSVs(intervalNo, poiPowerConsumption, poiAllocatedCores, optimalPowerConsumption);

        System.out.println("...Updating Total Prediction CSVs");
        this.updateTotalPredictionCSVs(intervalPredictedTasks, intervalAdmittedTasks, accumulatedResponseTime,
                intervalFinishedTasks, intervalViolations, residualWorkload);

        this.updateTotalPowerConsumptionCSVs(intervalNo, poiPowerConsumption, optimalPowerConsumption);

        System.out.println();
        System.out.println("\n------------------------------------------------------------------\n");

        // pressAnyKeyToContinue();
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

    private void updateIntervalPredictionCSVs(double[][] predictedWorkload, int[][] admittedTasks, int[][] predictedUsers,
                                              int[][] realUsers, int intervalNo) {
        // Prediction Evaluation
        for (int poi = 0; poi < this.pois; poi++) {
            for (int app = 0; app < this.apps; app++) {
                // Initiate files if not present
                String fileName = "(" + this.timeStamp + ")" + "_P" + poi + "_A" + app + ".csv";
                File csvFile = new File(System.getProperty("user.dir") + "/evaluation_results/Prediction/" + fileName);
                StringBuilder sb = new StringBuilder();
                if(!csvFile.isFile()) {
//                    System.out.println("File does not exist!");
                    sb.append("Interval, Predicted_Workload, Real_Workload, Predicted_Users, Real_Users\n");
                }
                try {
                    BufferedWriter br = new BufferedWriter(new FileWriter(csvFile, true));

                    sb.append(intervalNo + "," + predictedWorkload[poi][app] + "," + admittedTasks[poi][app] + "," +
                           predictedUsers[poi][app] + "," + realUsers[poi][app] + "\n");

                    br.write(sb.toString());
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void updateTotalPredictionCSVs(double[][] predictedWorkload, int[][] admittedTasks,
                                           double[][] accumulatedResponseTime, int[][] intervalFinishedTasks,
                                           int[][] intervalViolations, double[][] residualWorkload) {

        // Prediction Evaluation
        int toExclude = 0;
        double[][] acc = new double[this.pois][this.apps];
        boolean[][] exclude = new boolean[this.pois][this.apps];
        for (int poi = 0; poi < this.pois; poi++) {
            for (int app = 0; app < this.apps; app++) {
                if (predictedWorkload[poi][app] > admittedTasks[poi][app]) exclude[poi][app] = true; // experimental
                double dif = Math.abs(predictedWorkload[poi][app] - admittedTasks[poi][app]);
                if (admittedTasks[poi][app] != 0) acc[poi][app] = dif / admittedTasks[poi][app];
                else toExclude++;
            }
        }
        System.out.println("Per Poi - Per App Prediction Error = " + Arrays.deepToString(acc));

        // Mean Accuracy
        double sum = 0;
        for (int poi = 0; poi < this.pois; poi++) {
            for (int app = 0; app < this.apps; app++) {
                sum += acc[poi][app];
            }
        }

        double avg = sum / (this.pois * this.apps - toExclude);
        System.out.println("Per Poi - Per App Prediction Error SUM = " + sum);
        System.out.println("Mean Prediction Error = " + avg);

        // Average Response Time
        double[][] art = new double[this.pois][this.apps];
        for (int poi = 0; poi < this.pois; poi++) {
            for (int app = 0; app < this.apps; app++) {
                art[poi][app] = accumulatedResponseTime[poi][app] / intervalFinishedTasks[poi][app];
            }
        }
        System.out.println("Per Poi - Per App ART = " + Arrays.deepToString(art));

        // double[][] violationsPercentage = new double[this.pois][this.apps];
        // Total Violations Percentage
        // for (int poi = 0; poi < this.pois; poi++) {
        //     for (int app = 0; app < this.apps; app++) {
        //         violationsPercentage[poi][app] = intervalViolations[poi][app] / intervalFinishedTasks[poi][app];
        //     }
        // }
        // System.out.println("Interval violations = " + Arrays.deepToString(violationsPercentage));

        // Mean ART
        toExclude = 0;
        for (int poi = 0; poi < this.pois; poi++) {
            for (int app = 0; app < this.apps; app++) {
                if (!Double.isNaN(art[poi][app])) sum += art[poi][app];
                else toExclude++;
            }
        }
        double avg2 = sum / (this.pois * this.apps - toExclude);
        System.out.println("Per Poi - Per App ART SUM = " + sum);
        System.out.println("Mean ART = " + avg2);

        String fileName = "TOTAL(" + this.timeStamp + ").csv";
        File csvFile = new File(System.getProperty("user.dir") + "/evaluation_results/Prediction/" + fileName);
        StringBuilder sb = new StringBuilder();
        if(!csvFile.isFile()) {
            // sb.append("Interval, Prediction_Error, Total_Violations, Average_Response_Time\n");
            sb.append("Prediction_Error, Average_Response_Time, NoOfViolations, NoOf_Finished_Tasks, AccResponseTime, " +
                    "Residual Workload\n");
        }
        try {
            BufferedWriter br = new BufferedWriter(new FileWriter(csvFile, true));

            for (int poi = 0; poi < this.pois; poi++) {
                for (int app = 0; app < this.apps; app++) {
                    if (exclude[poi][app]) continue;
                    if ((acc[poi][app] != 0) && !Double.isNaN(art[poi][app]) && art[poi][app] < 20) {
                        // sb.append(intervalNo + "," + acc[poi][app] + "," + violations + "," + art[poi][app] + "\n");
                        sb.append(acc[poi][app] + "," + art[poi][app] + "," + intervalViolations[poi][app] + "," +
                                intervalFinishedTasks[poi][app] + "," + accumulatedResponseTime[poi][app] + ", " +
                                residualWorkload[poi][app] + "\n");
                        DecimalFormat dc = new DecimalFormat("#.#");
                        dc.setRoundingMode(RoundingMode.FLOOR);
                        String key = dc.format(acc[poi][app]); // sort by prediction accuracy
                        if (!predictionStats.containsKey(key)) predictionStats.put(key, new double[3]);
                        predictionStats.get(key)[0] += accumulatedResponseTime[poi][app]; // accumulate response time
                        predictionStats.get(key)[1] += intervalFinishedTasks[poi][app]; // number of incidents
                        predictionStats.get(key)[2] += intervalViolations[poi][app]; //number of violations

                        String key2 = String.valueOf(Math.round((int)residualWorkload[poi][app]/10.0) * 10); // sort by residual workload
                        // System.out.println(key2);
                        if (!mrfStats.containsKey(key2)) mrfStats.put(key2, new double[3]);
                        mrfStats.get(key2)[0] += accumulatedResponseTime[poi][app]; // accumulate response time
                        mrfStats.get(key2)[1] += intervalFinishedTasks[poi][app]; // number of incidents
                        mrfStats.get(key2)[2] += intervalViolations[poi][app]; //number of violations
                    }
                }
            }

            br.write(sb.toString());
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // pressAnyKeyToContinue();
    }

    private void updateIntervalAppPerformanceCSVs(int intervalNo, int[][] admittedTasks, int[][] finishedTasks,
                                                  double[][] accumulatedResponseTime, int[][] allocatedUsers,
                                                  int[][] allocatedCores, double[][] avgSinr, double avgResidualEnergy,
                                                  double[][] residualWorkload) {
        // App Performance evaluation
        for (int poi = 0; poi < this.pois; poi++) {
            for (int app = 0; app < this.apps; app++) {
                // Initiate files if not present
                String fileName = "(" + this.timeStamp + ")" + "_P" + poi + "_A" + app + ".csv";
                File csvFile = new File(System.getProperty("user.dir") + "/evaluation_results/AppPerformance/" + fileName);
                StringBuilder sb = new StringBuilder();
                if(!csvFile.isFile()) {
//                    System.out.println("File does not exist!");
                    sb.append("Interval, Admitted, Finished, AvgThroughput, AvgResponseTime, AllocatedUsers, " +
                            "AllocatedCores, AvgSINR, AvgResidualEnergy, ResidualWorkload\n");
                }
                try {
                    BufferedWriter br = new BufferedWriter(new FileWriter(csvFile, true));

                    sb.append(intervalNo + "," + admittedTasks[poi][app] + "," + finishedTasks[poi][app] + "," +
                            String.format("%.2f", finishedTasks[poi][app] / (double) this.samplingInterval) + "," +
                            String.format("%.2f", accumulatedResponseTime[poi][app] / finishedTasks[poi][app]) + "," +
                            allocatedUsers[poi][app] + "," + allocatedCores[poi][app] + "," +
                            String.format("%.2f", avgSinr[poi][app]) + "," + avgResidualEnergy +
                            String.format("%.2f", residualWorkload[poi][app]) + "," + "\n");

                    br.write(sb.toString());
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void updateIntervalPoiPerformanceCSVs(int intervalNo, int[] powerConsumption, int[] allocatedCores,
                                                  int[] optimalPowerConsumption) {
        // Host Performance evaluation
        for (int poi = 0; poi < this.pois; poi++) {
            // Initiate files if not present
            String fileName = "(" + this.timeStamp + ")" + "_P" + poi + ".csv";
            File csvFile = new File(System.getProperty("user.dir") + "/evaluation_results/PoiPerformance/" + fileName);
            StringBuilder sb = new StringBuilder();
            if(!csvFile.isFile()) {
                // System.out.println("File does not exist!");
                sb.append("Interval, PwrConsumption, OptimalPwrConsumption, AllocatedCores\n");
            }
            try {
                BufferedWriter br = new BufferedWriter(new FileWriter(csvFile, true));

                sb.append(intervalNo + "," + powerConsumption[poi] + "," + optimalPowerConsumption[poi] + "," + allocatedCores[poi] + "\n");

                br.write(sb.toString());
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateTotalPowerConsumptionCSVs(int intervalNo, int[] powerConsumption, int[] optimalPowerConsumption) {
        double powerConsumptionAverage = Arrays.stream(powerConsumption).average().getAsDouble();
        double optimalPowerConsumptionAverage = Arrays.stream(optimalPowerConsumption).average().getAsDouble();
        String fileName = "TOTAL_PWR_CNSMPT(" + this.timeStamp + ")" + ".csv";
        File csvFile = new File(System.getProperty("user.dir") + "/evaluation_results/PoiPerformance/" + fileName);
        StringBuilder sb = new StringBuilder();
        if(!csvFile.isFile()) {
            // System.out.println("File does not exist!");
            sb.append("Interval, AvgPwrConsumption, AvgOptimalPwrConsumption\n");
        }
        try {
            BufferedWriter br = new BufferedWriter(new FileWriter(csvFile, true));

            sb.append(intervalNo + "," + powerConsumptionAverage + "," + optimalPowerConsumptionAverage + "\n");

            br.write(sb.toString());
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sumUpTotalPredictions(){
        predictionStats.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + " -> " + Arrays.toString(entry.getValue()));
        });

        Map<String, double[]> sortedPredErrors = new TreeMap<>(predictionStats);

        String fileName = "TOTAL(" + this.timeStamp + ").csv";
        File csvFile = new File(System.getProperty("user.dir") + "/evaluation_results/Prediction/" + fileName);
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        try {
            BufferedWriter br = new BufferedWriter(new FileWriter(csvFile, true));
            sortedPredErrors.entrySet().forEach(entry -> {
                sb.append(entry.getKey() + "," + new DecimalFormat("#.##").format(entry.getValue()[0] /
                        entry.getValue()[1]) + "," + new DecimalFormat("#.##").format(entry.getValue()[2] /
                        entry.getValue()[1]) + "\n");
            });
            br.write(sb.toString());
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sumUpTotalMRF(){
        mrfStats.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + " -> " + Arrays.toString(entry.getValue()));
        });

        Map<String, double[]> sortedPredErrors = new TreeMap<>(mrfStats);

        String fileName = "TOTAL(" + this.timeStamp + ").csv";
        File csvFile = new File(System.getProperty("user.dir") + "/evaluation_results/MRF/" + fileName);
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        try {
            BufferedWriter br = new BufferedWriter(new FileWriter(csvFile, true));
            sortedPredErrors.entrySet().forEach(entry -> {
                sb.append(entry.getKey() + "," + new DecimalFormat("#.##").format(entry.getValue()[0] /
                        entry.getValue()[1]) + "," + new DecimalFormat("#.##").format(entry.getValue()[2] /
                        entry.getValue()[1]) + "\n");
            });
            br.write(sb.toString());
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void archiveSimulationCSVs() {
        File[] folders = new File(System.getProperty("user.dir") + "/evaluation_results").listFiles();

        for (File folder : folders) {
            new File(folder.toPath().toString(), "Averages").mkdirs();
            if (folder.isDirectory()) {
                File[] files = folder.listFiles();
                Arrays.sort(files);
                File file = files[1];
                try {
                    Files.move(file.toPath(), Paths.get(folder.toPath().toString(), "Averages", file.getName()),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        for (File folder : folders) {
            // Create "Archived" directory if it doesn't exist
            new File(folder.toPath().toString(), "Archived").mkdirs();
            if (folder.isDirectory()) {
//                System.out.println(folder.toPath());
                File[] files = folder.listFiles();
//                System.out.println(files.toString());
                for (File file : files) {
                    if (file.isFile()) {
                        try {
                            Files.move(file.toPath(), Paths.get(folder.toPath().toString(), "Archived", file.getName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private static void pressAnyKeyToContinue()
    {
        System.out.println("Press Enter key to continue...");
        try
        {
            System.in.read();
        }
        catch(Exception e)
        {}
    }

    public void plotCSVs() {
        sumUpTotalPredictions();
        sumUpTotalMRF();
        Plotter plotter = new Plotter();
//        plotter.doPlots();
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

}
