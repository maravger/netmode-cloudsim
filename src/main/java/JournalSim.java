import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.util.Pair;
import lpsolve.LpSolveException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisioner;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.vm.VmScheduler;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.IntStream;

public class JournalSim {
    // TODO: read these values from external file
    // TODO: set debugging toggle and different colors

    // Simulation related constants
    private static final double TIME_TO_TERMINATE_SIMULATION = 360;
    private static final double SCHEDULING_INTERVAL = 1;
    private static final int SAMPLING_INTERVAL = 30;

    // n-MMC related constants
    private static final Boolean CREATE_NMMC_TRANSITION_MATRIX = false;
    private static final int NMMC_HISTORY = 2;
    private static final String SIM_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/leivaDatas.csv";
    private static final String READ_NMMC_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/transitionMatrix(2history12000intervals).csv";
    private static final String WRITE_NMMC_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/transitionMatrix.csv";
    private static final String WRITE_INTERVALS_CSV_FILE_LOCATION = "/Users/avgr_m/Downloads/intervalStats.csv";

    // Environment related constants
    private static final int POI = 9; //define points of interest
    private static final int APPS = 2;
    private static final int NO_OF_DISTANCES = 1;
    private static final int MAX_USERS_PER_CELL = 2;
    private static final int GROUP_SIZE = 4; // [1..10]
    private static final int GRID_SIZE = 3;

    // Edge Servers related constants
    private static final int EDGE_HOSTS = 1;
    private static final int EDGE_HOST_PES = 16;
    private static final int EDGE_HOST_PE_MIPS = 2000;
    private static final int EDGE_HOST_RAM = 64768;
    private static final int EDGE_HOST_BW = 1000000;

    // VM related constants
    private static final int[][] VM_PES = {{1, 2, 4}, {1, 2, 4}, {1, 2, 4}}; // [app][flavor]
    private static final int[][] VM_PE_MIPS = {{2000, 2000, 2000}, {2000, 2000, 2000}, {2000, 2000, 2000}};
    private static final double[][] VM_GUARANTEED_AVG_RR = {{37.35, 82.24, 172.68}, {37.35, 82.24, 172.68}, {37.35, 82.24, 172.68}};
    private static final double[][] VM_GUARANTEED_MAX_RR = {{50.00, 110.00, 210.00}, {50.00, 110.00, 210.00}, {50.00, 110.00, 210.00}};
    private static final int VM_RAM = 4096;
    private static final int VM_BW = 200000;

    // Task related constants
    private static final int TASK_PES = 1;
    private static final int TASK_LENGTH = 1000;

    // Various "global" variables
    private int[][] alreadyAccessed; // 1 = been there, 0 = not been there
    private Double[][] simData;
    private int howManyVisited;
    private int lastAccessed;
    private int maxVmSize;
    private final CloudSim simulation = new CloudSim();
    private DatacenterBrokerSimpleExtended[] edgeBroker;
    private ArrayList<Vm>[][] vmList;
    private ArrayList<TaskSimple>[][] taskList;
    private double [][][] accumulatedCpuUtil;
    private int [][] taskCounter;
    private int lastIntervalFinishTime;
    private HashMap<String, int[]> transitionsLog;
    private HashMap<String, double[]> transitionProbabilitiesMap;
    private String historicState;
    private ArrayList<Integer> prevPos;
    private double[][] requestRatePerCell;
    private Boolean firstEvent;

    public static void main(String[] args) {
        new JournalSim();
    }

    public JournalSim() {
        alreadyAccessed = new int[GRID_SIZE][GRID_SIZE];
        edgeBroker = new DatacenterBrokerSimpleExtended[POI];
        vmList = (ArrayList<Vm>[][]) new ArrayList[POI][APPS];
        taskList = (ArrayList<TaskSimple>[][]) new ArrayList[POI][APPS];
        taskCounter = new int[POI][APPS];
        prevPos = new ArrayList<>(Arrays.asList(0, 0));

        simData = readSimCSVData(SIM_CSV_FILE_LOCATION);
        howManyVisited = 1;
        firstEvent = true;
        historicState = "";

        // One broker and one datacenter per Point of Interest
        for (int poi = 0; poi < POI; poi++) {
            Set<Datacenter> edgeDCList = new HashSet<>();
            Datacenter dc = createDatacenter(EDGE_HOSTS, EDGE_HOST_PES, EDGE_HOST_PE_MIPS, EDGE_HOST_RAM, EDGE_HOST_BW);
            dc.setName("DataCenter" + poi);
            edgeDCList.add(dc);
            edgeBroker[poi] = new DatacenterBrokerSimpleExtended(simulation);
            edgeBroker[poi].setName("AccessPoint" + poi);
            edgeBroker[poi].setDatacenterList(edgeDCList);
        }

        // Create number of initial VMs for each app
        int flavor = 2;
        int noOfVms = 1; // per app
        for (int poi = 0; poi < POI; poi++) {
            ArrayList<Vm> tempVmList = new ArrayList();
            for (int app = 0; app < APPS; app++) {
                vmList[poi][app] = createVms(noOfVms, poi, app, flavor);
                tempVmList.addAll(vmList[poi][app]);
                if (vmList[poi][app].size() > maxVmSize) maxVmSize = vmList[poi][app].size();
            }
            edgeBroker[poi].submitVmList(tempVmList);
        }

        // Initialize stat-gathering lists
        accumulatedCpuUtil = new double[POI][APPS][maxVmSize]; // TODO (1): reallocate it in every interval taking the number of VMs
                                                                // TODO (2):  of the Cloudlets into consideration;

        transitionsLog = new HashMap<>();
        // TODO: use transition probabilities map to predict incoming workload
        if (!CREATE_NMMC_TRANSITION_MATRIX) {
            transitionProbabilitiesMap = readNMMCTransitionMatrixCSV();
//            System.out.println("Transition Probabilities Map: ");
//            transitionProbabilitiesMap.entrySet().forEach(entry -> {
//                System.out.println(entry.getKey() + " -> " + Arrays.toString(entry.getValue()));
//            });
        }

//        runSimulationAndPrintResults();
        int[][] flavorCores = {{1, 2, 4}, {1, 2, 4}};
        ArrayList<int[][]> feasibleFormations = calculateFeasibleServerFormations(4, flavorCores);
        double[][] guaranteedWorkload = calculateServerGuaranteedWorkload(feasibleFormations);
        double[] energyConsumption = calculateServerPowerConsumption(feasibleFormations, EDGE_HOST_PES);
        double[][] predictedWorkload = {{50, 100}, {200, 400}, {200, 400}, {200, 400}, {200, 400}, {200, 400}, {200, 400}, {200, 400}, {200, 400}};
        ArrayList<Integer>[] vmPlacement = optimizeVmPlacement(guaranteedWorkload, energyConsumption, 3, predictedWorkload);
        calculateResidualWorkload(vmPlacement, guaranteedWorkload, predictedWorkload);

        System.out.println(getClass().getSimpleName() + " finished!");
        if (CREATE_NMMC_TRANSITION_MATRIX) createNMMCTransitionMatrixCSV();
    }

    // Take decisions with a second-wise granularity
    private void masterOfPuppets(final EventInfo evt) {
//        System.out.println((int)evt.getTime());
        int[][] assignedUsers;

        // Initial configurations
        if (firstEvent) {
            correctlyCreateVmDescriptions();
            firstEvent = false;
        }

        if (!(lastAccessed == (int) evt.getTime())) {

            collectVmStats();

            // If a full interval has been completed or first interval, move group and generate request rate per cell
            if (((int) evt.getTime() % SAMPLING_INTERVAL == 0) || ((int) evt.getTime() < SAMPLING_INTERVAL)) {
                // Move group
                alreadyAccessed[prevPos.get(0)][prevPos.get(1)] = 1;
                ArrayList<Integer> nextPos = moveGroup(0, 3, prevPos);
                if (CREATE_NMMC_TRANSITION_MATRIX) logTransitions(GRID_SIZE * prevPos.get(0) + prevPos.get(1),
                        GRID_SIZE * nextPos.get(0) + nextPos.get(1));
                prevPos = nextPos;
                howManyVisited++;
                // Check if group has finished the tour
                if (howManyVisited == GRID_SIZE * GRID_SIZE) {
//                System.out.println("!!! Tour Finished");
                    alreadyAccessed = new int[GRID_SIZE][GRID_SIZE];
                    howManyVisited = 1;
                }
                assignedUsers = createRandomUsers(GRID_SIZE, nextPos, GROUP_SIZE);
                requestRatePerCell = createRequestRate(assignedUsers);

                // If a full interval has been completed, gather stats and present them
                if ((int) evt.getTime() % SAMPLING_INTERVAL == 0) {
                    // Collect Stats
                    IntervalStats stats =  collectTaskStats();
                    int[][] intervalFinishedTasks = stats.getIntervalFinishedTasks();
                    int[][] intervalAdmittedTasks = stats.getIntervalAdmittedTasks();
                    double[][] accumulatedResponseTime = stats.getAccumulatedResponseTime();
                    formatAndPrintIntervalStats(intervalFinishedTasks,
                            intervalAdmittedTasks, accumulatedResponseTime);
                    accumulatedCpuUtil = new double[POI][APPS][maxVmSize];
                    lastIntervalFinishTime = (int) evt.getTime();
                }
            }

            // Create requests based on generated request rate
            int app = 0; // TODO: create workload for more than one apps
            if (!CREATE_NMMC_TRANSITION_MATRIX) generateRequests(requestRatePerCell, evt, app);

            lastAccessed = (int) evt.getTime();
        }
    }

    private double[][] calculateResidualWorkload(ArrayList<Integer>[] vmPlacement, double[][] guaranteedWorkload,
                                                 double[][] predictedWorkload) {
        double[][] residualWorkload = new double[POI][APPS];

        for (int poi = 0; poi < POI; poi++) {
            double[] servedWorkload = new double[APPS];
            for (int vmFormation : vmPlacement[poi]) {
                for (int app = 0; app < APPS; app++) {
                    servedWorkload[app] += guaranteedWorkload[vmFormation][app];
                }
            }
            for (int app = 0; app < APPS; app++) {
                if (servedWorkload[app] - predictedWorkload[poi][app] > 0)
                    residualWorkload[poi][app] = 0;
                else
                    residualWorkload[poi][app] = predictedWorkload[poi][app] - servedWorkload[app];
            }
            System.out.println("Served Workload" + Arrays.toString(servedWorkload));
            System.out.println("Predicted Workload" + Arrays.toString(predictedWorkload[poi]));
            System.out.println("Residual Workload" + Arrays.toString(residualWorkload[poi]));
        }


        return residualWorkload;
    }
//
//    private double[][] calculateResidualResources() {
//
//    }

    private ArrayList<Integer>[] optimizeVmPlacement(double[][] guaranteedWorkload, double[] energyConsumption,
                                     int hosts, double[][] predictedWorkload) {
        ArrayList<Integer>[] vmPlacement = new ArrayList[POI];

        for (int poi = 0; poi < POI; poi++) {
//            System.out.println(Arrays.deepToString(guaranteedWorkload));
//            System.out.println(Arrays.toString(energyConsumption));
            try {
                vmPlacement[poi] = Optimizer.optimizeVmPlacement(guaranteedWorkload, energyConsumption, hosts, predictedWorkload[poi]);
            } catch (LpSolveException e) {
                e.printStackTrace();
            }
        }
        System.out.println("\n\n" + Arrays.deepToString(vmPlacement));
        return vmPlacement;
    }

    private double[][] calculateServerGuaranteedWorkload(ArrayList<int[][]> feasibleFormations) {
        int totalFormations = feasibleFormations.size();
        double[][] guaranteedWorkload = new double[totalFormations][APPS];

        for (int permutation = 0; permutation < totalFormations; permutation++) {
//            System.out.println(Arrays.deepToString(feasibleFormations.get(permutation)));
            for (int app = 0; app < APPS; app++) {
                for (int flavor : feasibleFormations.get(permutation)[app]) {
                    guaranteedWorkload[permutation][app] += VM_GUARANTEED_AVG_RR[app][ArrayUtils.indexOf(VM_PES[app], flavor)];
                }
            }
        }

//        System.out.println(Arrays.deepToString(guaranteedWorkload));

        return guaranteedWorkload;
    }

    private double[] calculateServerPowerConsumption(ArrayList<int[][]> feasibleFormations, int serverCores) {
        int totalFormations = feasibleFormations.size();
        double[] energyConsumption = new double[feasibleFormations.size()];
        double pMax = 2000; // the maximum power consumed when the server is fully utilized, in Watts
        double k = 0.6; // k is the fraction of power consumed by an idle server (usually around 70%)

        for (int permutation = 0; permutation < totalFormations; permutation++) {
//            System.out.println(Arrays.deepToString(feasibleFormations.get(permutation)));
            for (int app = 0; app < APPS; app++) {
                for (int flavor : feasibleFormations.get(permutation)[app]) {
//                    System.out.println(ArrayUtils.indexOf(VM_PES[app], flavor));
                    energyConsumption[permutation] += calculateVmPowerConsumption(serverCores, VM_PES[app][flavor], k, pMax);
//                    System.out.println(energyConsumption[permutation]);
                }
            }
        }

        return energyConsumption;
    }

    // Use Energy Model defined in paper to predict the power consumed by each VM provisioned in a server with an error below 5%
    private double calculateVmPowerConsumption(int serverCores, int vmCores, double k, double pMax) {
        return k * pMax + ((1 - k) * pMax * vmCores / serverCores);
    }

    private ArrayList<int[][]> calculateFeasibleServerFormations(int serverCores, int[][] flavorCores) {
        ArrayList<int[][]> formations = new ArrayList<>();
        ArrayList<int[]> tempFormations = new ArrayList<>();
        ArrayList<int[][]> uniqueFormations = new ArrayList<>();

        // Get permutations per App
        for (int length = 1; length <= serverCores; length++) {
            for (int app = 0; app < APPS; app++) {
                for (int[] permutation : calculatePermutationsOfLength(flavorCores[app], length)) {
                    // First Check
                    if (IntStream.of(permutation).sum() <= serverCores) tempFormations.add(permutation);
                }
            }
        }

        // Get total feasible permutations
        for (int[][] permutation : calculatePermutationsOfLength(tempFormations, APPS)) {
            int permutationCoreSum = 0;
            for (int app = 0; app < APPS; app++) permutationCoreSum += IntStream.of(permutation[app]).sum();
            if (permutationCoreSum <= serverCores) formations.add(permutation);
        }

        // Remove duplicates
        for (int[][] newPermutation : formations) {
            // Sort flavors inside
            for (int[] appWisePermutation : newPermutation) Arrays.sort(appWisePermutation);
            boolean unique = true;
            for (int[][] oldPermutation : uniqueFormations) {
                // Sort flavors inside
                for (int[] appWisePermutation : oldPermutation) Arrays.sort(appWisePermutation);
                if (Arrays.deepEquals(newPermutation, oldPermutation)) unique = false;
            }
            if (unique)
                uniqueFormations.add(newPermutation);
        }

        for (int[][] permutation : uniqueFormations) {
            int permutationCoreSum = 0;
            for (int app = 0; app < APPS; app++) permutationCoreSum += IntStream.of(permutation[app]).sum();
//            System.out.println(permutationCoreSum);
            System.out.println(Arrays.deepToString(permutation));
        }

        return uniqueFormations;
    }

    private ArrayList<int[]> calculatePermutationsOfLength(int[] flavorCores, int length) {
        ArrayList<int[]> permutations = new ArrayList<>();
        int size = flavorCores.length;

        // There can be (len)^l permutations
        for (int i = 0; i < (int)Math.pow(size, length); i++) {
            // Convert i to len th base
            permutations.add(createPermutation(i, flavorCores, size, length));
        }

        return permutations;
    }

    // Overloading
    private ArrayList<int[][]> calculatePermutationsOfLength(ArrayList<int[]> formations, int length) {
        ArrayList<int[][]> permutations = new ArrayList<>();
        int size = formations.size();

        // There can be (len)^l permutations
        for (int i = 0; i < (int)Math.pow(size, length); i++) {
            // Convert i to len th base
            permutations.add(createPermutation(i, formations, size, length));
        }

        return permutations;
    }

    private int[] createPermutation(int n, int arr[], int len, int L) {
        int[] permutation = new int[L];
        // Sequence is of length L
        for (int i = 0; i < L; i++) {
            // Print the ith element of sequence
//            System.out.print(arr[n % len]);
            permutation[i] = arr[n % len];
            n /= len;
        }
//        System.out.print();
//        System.out.println(Arrays.toString(permutation));

        return permutation;
    }

    // Overloading
    private int[][] createPermutation(int n, ArrayList<int[]> arr, int len, int L) {
        int[][] permutation = new int[APPS][];

        // Sequence is of length L
        for (int i = 0; i < L; i++) {
            // Print the ith element of sequence
//            System.out.print(arr[n % len]);
            permutation[i] = arr.get(n % len);
            n /= len;
        }
//        System.out.println(Arrays.deepToString(permutation));

        return permutation;
    }

    private void collectVmStats() {
        for (int poi = 0; poi < POI; poi++) {
            for (int app = 0; app < APPS; app++) {
                for (int vm = 0; vm < vmList[poi][app].size(); vm++) {
                    accumulatedCpuUtil[poi][app][vm] += vmList[poi][app].get(vm).getCpuPercentUtilization();
//                    System.out.println("VM ID: " + poi + "" + app + "" + vm);
//                    System.out.println("Current CPU Util: " + vmList[poi][app].get(vm).getCpuPercentUtilization());
//                    System.out.println("Total CPU Util: " + accumulatedCpuUtil[poi][app][vm]);
                }
            }
        }
    }

    private IntervalStats collectTaskStats() {
        int[][] intervalFinishedTasks = new int[POI][APPS];
        int[][] intervalAdmittedTasks = new int[POI][APPS];
        double[][] accumulatedResponseTime  = new double[POI][APPS];

        for (int poi = 0; poi < POI; poi++) {
//                System.out.println(edgeBroker[poi].getCloudletFinishedList().size());
            for (Cloudlet c : edgeBroker[poi].getCloudletFinishedList()) {
                // Ensure that Task has been completed within the Interval
                if (c.getFinishTime() > lastIntervalFinishTime) {
//                        System.out.println("Task ID: " + c.getId());
//                        System.out.println("Execution Time: " + c.getActualCpuTime());
//                        System.out.println("Finish Time: " + c.getFinishTime());
//                        System.out.println("Start Time: " + c.getExecStartTime());
//                        System.out.println("-------------------------------------");
                    JsonObject description = new JsonParser().parse(c.getVm().getDescription()).getAsJsonObject();
                    int app = description.get("App").getAsInt();
                    intervalFinishedTasks[poi][app]++;
                    accumulatedResponseTime[poi][app] += c.getActualCpuTime();
//                    System.out.println("Current accumulated Response Time: " + accumulatedResponseTime[poi][app]);
//                    System.out.println(" + " + (c.getFinishTime() - c.getExecStartTime()));
//                    System.out.println(" ------------------------------------------");
                }
            }
        }

        for (int poi = 0; poi < POI; poi++) {
//               System.out.println(edgeBroker[poi].getCloudletFinishedList().size());
//            System.out.println(edgeBroker[poi].getCloudletSubmittedList().size());
            for (Cloudlet c : edgeBroker[poi].getCloudletSubmittedList()) {
                // Ensure that Task has been completed within the Interval
//                System.out.println(c.getLastDatacenterArrivalTime());
//                System.out.println(lastIntervalFinishTime);
                if (c.getLastDatacenterArrivalTime() > lastIntervalFinishTime) {
                    JsonObject description = new JsonParser().parse(c.getVm().getDescription()).getAsJsonObject();
                    int app = description.get("App").getAsInt();
//                    System.out.println(c.getLastDatacenterArrivalTime());
                    intervalAdmittedTasks[poi][app]++;
//                    System.out.println(intervalAdmittedTasks[poi][app]);
                }
            }
        }

        return new IntervalStats(intervalFinishedTasks, intervalAdmittedTasks, accumulatedResponseTime);
    }

    private void formatAndPrintIntervalStats(int[][] intervalFinishedTasks, int[][] intervalAdmittedTasks,
                                             double[][] accumulatedResponseTime) {
        try {
            BufferedWriter br = new BufferedWriter(new FileWriter(WRITE_INTERVALS_CSV_FILE_LOCATION, true));
            StringBuilder sb = new StringBuilder();

            System.out.printf("%n%n------------------------- INTERVAL INFO --------------------------%n%n");
            System.out.printf(" POI | App | Admitted Tasks| Finished Tasks | Average Throughput | Average Response Time \n");
            for (int poi = 0; poi < POI; poi++) {
                for (int app = 0; app < APPS; app++) {
                    // Print in screen
                    System.out.println(String.format("%4s", poi) + " | " + String.format("%3s", app) + " | " +
                        String.format("%14s", intervalAdmittedTasks[poi][app]) + " | " + String.format("%14s",
                        intervalFinishedTasks[poi][app]) + " | " + String.format("%18.2f", intervalFinishedTasks[poi][app]
                        / (double) SAMPLING_INTERVAL) + " | " + String.format("%21.2f", accumulatedResponseTime[poi][app] /
                            intervalFinishedTasks[poi][app]));
                    // Print in the CSV file
                    sb.append(poi + "," + app + "," + intervalAdmittedTasks[poi][app] + "," + intervalFinishedTasks[poi][app] +
                        "," + String.format("%.2f", intervalFinishedTasks[poi][app] / (double) SAMPLING_INTERVAL) + ","
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
        for (int poi = 0; poi < POI; poi++) {
            for (int app = 0; app < APPS; app++) {
                for (int vm = 0; vm < vmList[poi][app].size(); vm++) {
                    System.out.println(String.format("%3s", poi + "" + app + "" + vm) + " | " +
                        String.format("%17.2f", (accumulatedCpuUtil[poi][app][vm] / SAMPLING_INTERVAL) * 100));
                }
            }
        }
    }

    private void correctlyCreateVmDescriptions() {
        for (int poi = 0; poi < POI; poi++) {
            for (int app = 0; app < APPS; app++) {
                for (int vmi = 0; vmi < vmList[poi][app].size(); vmi++) {
                    Vm vm = vmList[poi][app].get(vmi);
                    vm.setDescription("{\"App\": " + app + " }"); // Vm Description in Json format
                }
            }
        }
    }

    private HashMap<String, double[]> readNMMCTransitionMatrixCSV() {
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

    private Pair<String, double[]> getRecordFromLine(String line) {
        double[] value = new double[POI];
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

    private void createNMMCTransitionMatrixCSV() {
        // Sort HashMap by Keys
        Map<String, int[]> sortedTransitionLog = new TreeMap<>(transitionsLog);

        sortedTransitionLog.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + " -> " + Arrays.toString(entry.getValue()));
        });

        List<String> statesList = new ArrayList<>(sortedTransitionLog.keySet());
        int statesListIterator = 0;
        double[][] transitionMatrix = createNMMCTransitionMatrix(POI, sortedTransitionLog);

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

    private void logTransitions(int prevState, int nextState) {
        if (historicState.length() > NMMC_HISTORY) historicState = historicState.substring(1); // remove oldest state
        historicState += Integer.toString(prevState); //concat previous state
        if (!transitionsLog.containsKey(historicState)) {
            transitionsLog.put(historicState, new int[POI]); // create the array
        }
//        System.out.println("Previous State: " + prevState);
//        System.out.println("Historic State: " + historicState);
//        System.out.println("Next State: " + nextState);
        transitionsLog.get(historicState)[nextState]++;
        System.out.println("Transition Log: ");
        transitionsLog.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + " -> " + Arrays.toString(entry.getValue()));
        });
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

    private void generateRequests(double[][] requestRatePerCell, EventInfo evt, int app) {
        PoissonDistribution pD;
        int tasksToCreate, poi;

        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                poi = GRID_SIZE * i + j;
                if (requestRatePerCell[i][j] != 0) {
                    pD = new PoissonDistribution(requestRatePerCell[i][j] / SAMPLING_INTERVAL);
                    tasksToCreate = pD.sample();
                    taskList[poi][app] = new ArrayList<>();
                    System.out.printf("%n#-----> Creating %d Task(s) at PoI %d, for App %d at time %.0f sec.%n", tasksToCreate, poi, app, evt.getTime());
                    taskList[poi][app] = (ArrayList<TaskSimple>) createTasks(tasksToCreate, poi, app, 0);
                    edgeBroker[poi].submitCloudletList(taskList[poi][app], vmList[poi][app].get(0)); // TODO make app selection + load balancing
                }
                else {
                    System.out.printf("%n#-----> Creating %d Task(s) at PoI %d, for App %d at time %.0f sec.%n", 0, poi, app, evt.getTime());
                }
            }
        }
    }

    private double[][] createRequestRate(int[][] assignedUsers) {
        double[][] requestRatePerCell = new double[GRID_SIZE][GRID_SIZE];
        Random random = new Random(); // randomize distance from cell
        int distance;

        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (assignedUsers[i][j] != 0) {
                    distance = random.nextInt(NO_OF_DISTANCES) + 1;
                    requestRatePerCell[i][j] = simData[(assignedUsers[i][j] * distance) - 1][12];
                }
                else {
                    requestRatePerCell[i][j] = 0;
                }
            }
        }
//        System.out.println(Arrays.deepToString(assignedUsers));
//        System.out.println(Arrays.deepToString(requestRatePerCell));

        return requestRatePerCell;
    }

    private int[][] createRandomUsers(int gridSize, ArrayList<Integer> groupPos, int groupSize) {
        Random random = new Random();
        int[][] usersPerCell = new int[gridSize][gridSize];

        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                usersPerCell[i][j] = random.nextInt(MAX_USERS_PER_CELL);
            }
        }
        usersPerCell[groupPos.get(0)][groupPos.get(1)] += groupSize;
//        System.out.println("Users Per Cell");
//        for (int[] line : usersPerCell) {
//                for (int tile : line) {
//                    System.out.print(tile + " ");
//                }
//                System.out.println();
//            }
//            System.out.println("-----------");
        return usersPerCell;
    }

    private Double[][] readSimCSVData(String filename) {
        ArrayList<ArrayList<Double>> simTempListData = new ArrayList<>();
        BufferedReader csvReader = null;

        try {
            csvReader = new BufferedReader(new FileReader(filename));
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

    private ArrayList<Integer> moveGroup(int secondsLeft, int gridSize,  ArrayList<Integer> currPos) {
        int posX = 0;
        int posY = 0;

        if (secondsLeft != 0) {
            secondsLeft--;
        }
        else {
            // Choose next position
            ArrayList<ArrayList<Double> > grid = new ArrayList(3);
            double largestDist = 0;
            for (int x = 0; x < gridSize; x++) {
                ArrayList<Double> line = new ArrayList<>();
                for (int y = 0; y < gridSize; y++) {
                    double dist = Math.abs(currPos.get(0) - x) + Math.abs(currPos.get(1) - y);
                    line.add(dist);
                    if (dist > largestDist) {
                        largestDist = dist;
                    }
                }
                grid.add(line);
            }

            double newVal;
            double totalVal = 0; // this will be used to form the cumulative distribution function
            for (int x = 0; x < gridSize; x++) {
                for (int y = 0; y < gridSize; y++) {
                    double tile = grid.get(x).get(y);
                    if (tile == 0) {
                        newVal = 0;
                    }
                    else {
                        newVal = Math.round(largestDist/tile * 100.0) / 100.0;
                    }
                    grid.get(x).set(y, newVal);
                    totalVal += newVal;
                }
            }

            // Pick next step with the help of a Gaussian Probability distribution
            Random rand = new Random();
            int p;
            double cumulativeProbability;
            Boolean selectedNextState = false;
            while (!selectedNextState) {
                p = rand.nextInt((int) totalVal);
                cumulativeProbability = 0;
                outerLoop:
                for (int x = 0; x < gridSize; x++) {
                    for (int y = 0; y < gridSize; y++) {
                        cumulativeProbability += grid.get(x).get(y);
                        if (p <= cumulativeProbability && alreadyAccessed[x][y] == 0) {
//                        System.out.println(totalVal);
//                        System.out.println(p);
//                        System.out.println(cumulativeProbability);
//                        System.out.println(tile);
//                        System.out.println("-----------");
//                        System.out.println(x);
//                        System.out.println(y);
                            posY = y;
                            posX = x;
                            selectedNextState = true;
                            break outerLoop;
                        }
                    }
                }
            }
//            System.out.println();
//
//            System.out.println("Already Accessed: ");
//            for (int[] line : alreadyAccessed) {
//                for (int tile : line) {
//                    System.out.print(tile + " ");
//                }
//                System.out.println();
//            }
//            System.out.println("-----------");
//
//            System.out.println("Group's Position:");
//            for (ArrayList<Double> line : grid) {
//                for (Double tile : line) {
////                    System.out.print(tile + " ");
//                    if (tile == 0.0) System.out.print("X ");
//                    if (tile != 0.0) System.out.print("0 ");
//                }
//                System.out.println();
//            }
//            System.out.println("-----------");
        }

        return new ArrayList<>(Arrays.asList(posX, posY));
    }

    private Datacenter createDatacenter(int hosts, int hostPes, int hostPeMips, int hostRam, int hostBw) {
        final List<Host> hostList = new ArrayList<>(hosts);
        for(int i = 0; i < hosts; i++) {
            Host host = createHost(hostPes, hostPeMips, hostRam, hostBw);
            hostList.add(host);
        }

        //Uses a VmAllocationPolicySimple by default to allocate VMs
        final Datacenter dc = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    private Host createHost(int hostPes, int hostPeMips, int hostRam, int hostBw) {
        final List<Pe> peList = new ArrayList<>(hostPes);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < hostPes; i++) {
            //Uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(hostPeMips));
        }

        final long ram = hostRam; //in Megabytes
        final long bw = hostBw; //in Megabits/s
        final long storage = 1000000; //in Megabytes

        ResourceProvisioner ramProvisioner = new ResourceProvisionerSimple();
        ResourceProvisioner bwProvisioner = new ResourceProvisionerSimple();
        VmScheduler vmScheduler = new VmSchedulerTimeShared();
        Host host = new HostSimple(ram, bw, storage, peList);
        host
            .setRamProvisioner(ramProvisioner)
            .setBwProvisioner(bwProvisioner)
            .setVmScheduler(vmScheduler);
        return host;
    }

    private ArrayList<Vm> createVms(int noOfVms, int exhibit, int app, int flavor) {
        int vm_pes = VM_PES[app][flavor - 1];
        int vm_pe_mips = VM_PE_MIPS[app][flavor - 1];
        int vm_ram = VM_RAM;
        int vm_bw = VM_BW;
        final ArrayList<Vm> list = new ArrayList<>(noOfVms);
        for (int i = 0; i < noOfVms; i++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final Vm vm = new VmSimple(vm_pe_mips, vm_pes);
            vm.setId(exhibit * 10 + app);
            vm.setRam(vm_ram).setBw(vm_bw).setSize(1000);
            // Description contains application id
            vm.setDescription(Integer.toString(app));
            list.add(vm);
        }
        return list;
    }

    private List<TaskSimple> createTasks(int noOfTasks, int exhibit, int app, int interArrivalTime) {
        final List<TaskSimple> tempTaskList = new ArrayList<>(noOfTasks);

        //UtilizationModel defining the Tasks use up to 90% of any resource all the time
        final UtilizationModelDynamic utilizationModel = new UtilizationModelDynamic(0.9);

        int submissionDelay = 0;
        for (int i = 0; i < noOfTasks; i++) {
            final TaskSimple task = new TaskSimple(TASK_LENGTH, TASK_PES, utilizationModel);
            task.setUtilizationModelRam(UtilizationModel.NULL); // TODO: reconsider if we care about RAM and BW Utilization
            task.setUtilizationModelBw(UtilizationModel.NULL);
            task.setId(Long.parseLong((exhibit * 10 + app) + Integer.toString(taskCounter[exhibit][app])));
            task.setSizes(1024);
            task.setSubmissionDelay(submissionDelay);
            task.setBroker(edgeBroker[exhibit]);
            tempTaskList.add(task);
            submissionDelay += interArrivalTime;
            taskCounter[exhibit][app]++;
        }

        return tempTaskList;
    }

    private void runSimulationAndPrintResults() {
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

        simulation.terminateAt(TIME_TO_TERMINATE_SIMULATION);
        simulation.addOnClockTickListener(this::masterOfPuppets);
        simulation.start();
        List<TaskSimple> tasks = new ArrayList<>();
        for (int poi = 0; poi < POI; poi++) {
            tasks.addAll(edgeBroker[poi].getCloudletFinishedList());
        }
        if (!CREATE_NMMC_TRANSITION_MATRIX) new CloudletsTableBuilder(tasks).build();
    }
}
