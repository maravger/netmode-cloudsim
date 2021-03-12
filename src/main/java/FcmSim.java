import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.cloudsimplus.listeners.EventInfo;

import java.util.*;

public class FcmSim {

    // Simulation related constants
    private static final double TIME_TO_TERMINATE_SIMULATION = 3600;
    private static final int POI = 1;
    private static final int APPS = 1;
    private static final int SAMPLING_INTERVAL = 30;
    private static final double SCHEDULING_INTERVAL = 1;
    private static final int GRID_SIZE = 1;

    // Edge Servers related constants
    private static final int EDGE_HOSTS = 3;
    private static final int EDGE_HOST_PES = 4;
    private static final int EDGE_HOST_PE_MIPS = 2000;
    private static final int EDGE_HOST_RAM = 64768;
    private static final int EDGE_HOST_BW = 1000000;

    // VM related constants
    private static final int[][] VM_PES = {{1, 2, 4}}; // [app][flavor]
    private static final int[][] VM_PE_MIPS = {{2000, 2000, 2000}};
    private static final int VM_RAM = 4096;
    private static final int VM_BW = 200000;

    // Task related constants
    private static final int TASK_PES = 1;
    private static final int TASK_SIZE = 102400; // in Kb
    private static final int[] TASK_LENGTH = {100}; // in MI

    private Double[][] simData;
    private Boolean firstEvent;
    private DatacenterBrokerSimpleExtended[] edgeBroker;
    private CSVmachine csvm;
    private ArrayList<Vm>[][] vmList;
    private int lastAccessed;
    private int lastIntervalFinishTime;
    private double[][][] requestRatePerCell;
    private double[][] intervalPredictedTasks;
    private int[] poiAllocatedCores;
    private int[] poiPowerConsumption;
    private int[][] allocatedUsers;
    private int[][] allocatedCores;
    private double[][] avgSinr;
    private HashMap<Long, Double> accumulatedCpuUtil;
    private ArrayList<TaskSimple>[][] taskList;
    private int [][] taskCounter;
    private User[] assignedUsers;
    private double avgResidualEnergy;
    private int[][] previousPredictedUsersPerCellPerApp;

    private final CloudSim simulation = new CloudSim();

    public static void main(String[] args) {
        new FcmSim();
    }

    public FcmSim() {
        vmList = (ArrayList<Vm>[][]) new ArrayList[POI][APPS];
        csvm = new CSVmachine(POI, APPS, SAMPLING_INTERVAL);
        accumulatedCpuUtil = new HashMap<>();
        taskList = (ArrayList<TaskSimple>[][]) new ArrayList[POI][APPS];
        taskCounter = new int[POI][APPS];
        poiAllocatedCores = new int[POI];
        poiPowerConsumption = new int[POI];
        allocatedUsers = new int[POI][APPS];
        allocatedCores = new int[POI][APPS];
        intervalPredictedTasks = new double[POI][APPS];
        avgSinr = new double[POI][APPS];
        previousPredictedUsersPerCellPerApp = new int[POI][APPS];

        // Archive previous Simulation results
        csvm.archiveSimulationCSVs();

        // CSV machine "listens" to terminating signals in order to archive files
        csvm.listenTo("INT");

        simData = csvm.readSimCSVData();
        firstEvent = true;

        // Add one broker and one datacenter per Point of Interest
        createBrokersAndDatacenters(POI);

        // Create number of initial VMs for each app
        int randomFlavor = new Random().nextInt(3);
        vmList[0][0] = createVms(1, 0, randomFlavor);
        edgeBroker[0].submitVmList(vmList[0][0]);
        correctlySetVmDescriptions(vmList[0][0]);

        // Run simulation
        runSimulationAndPrintResults();
    }

    // Take decisions with a second-wise granularity
    private void masterOfPuppets(final EventInfo evt) {
        // Make sure to call only once per second
        if (!(lastAccessed == (int) evt.getTime())) {
            // If a full interval has been completed or first interval, predict group movement, optimize vm placement,
            // actually move group, generate request rate per cell
            if (((int) evt.getTime() % SAMPLING_INTERVAL == 0) || firstEvent) {
                if (!firstEvent) {
                    // Collect Stats and present them
                    IntervalStats stats = collectTaskStats();
                    int[][] intervalFinishedTasks = stats.getIntervalFinishedTasks();
                    int[][] intervalAdmittedTasks = stats.getIntervalAdmittedTasks();
                    int[][] intervalViolations = new int[POI][APPS];
                    double[][] accumulatedResponseTime = stats.getAccumulatedResponseTime();

                    // TODO: remove when debugging is over
                    System.out.println("Request Rate to generate in Previous Interval: " + Arrays.deepToString(requestRatePerCell));

                    csvm.formatPrintAndArchiveIntervalStats(((int) evt.getTime()) / SAMPLING_INTERVAL,
                            intervalPredictedTasks, intervalFinishedTasks, intervalAdmittedTasks, accumulatedResponseTime,
                            accumulatedCpuUtil, poiAllocatedCores, poiPowerConsumption, allocatedUsers, allocatedCores,
                            avgSinr, avgResidualEnergy, previousPredictedUsersPerCellPerApp, intervalViolations);

                    // Initiate interval variables
                    accumulatedCpuUtil = new HashMap<>();
                    lastIntervalFinishTime = (int) evt.getTime();
                }

                // Spawn VMs realizing random decisions
                int randomFlavor = new Random().nextInt(3);
                vmList[0][0] = createVms(1, 0, randomFlavor);
                edgeBroker[0].submitVmList(vmList[0][0]);
                correctlySetVmDescriptions(vmList[0][0]);

                // Change request rate based on the users
                assignedUsers = createAssignedUsers(2, 10, 50, 70);
                requestRatePerCell = new double[][][]{{{createRequestRate(assignedUsers)}}};

                // First interval arrangements are now over
                if (firstEvent) firstEvent = false;
            }

            // Actually create the requests based on the previously generated request rate and delegate them per VM/app
            generateRequests(requestRatePerCell, evt);

            // Vm resource usage stats collected per second
            collectVmStats();

            lastAccessed = (int) evt.getTime();
        }
    }

    private IntervalStats collectTaskStats() {
        int[][] intervalFinishedTasks = new int[POI][APPS];
        int[][] intervalAdmittedTasks = new int[POI][APPS];
        int[][] intervalViolations = new int[POI][APPS];
        double[][] accumulatedResponseTime  = new double[POI][APPS];

        for (int poi = 0; poi < POI; poi++) {
            for (Cloudlet c : edgeBroker[poi].getCloudletFinishedList()) {
                if (c.getFinishTime() > lastIntervalFinishTime) {
                    JsonObject description = new JsonParser().parse(c.getVm().getDescription()).getAsJsonObject();
                    int app = description.get("App").getAsInt();
                    intervalFinishedTasks[poi][app]++;
                    accumulatedResponseTime[poi][app] += c.getActualCpuTime();
                }
            }
        }

        for (int poi = 0; poi < POI; poi++) {
            for (Cloudlet c : edgeBroker[poi].getCloudletSubmittedList()) {
                // Ensure that Task has been completed within the Interval
                if (c.getLastDatacenterArrivalTime() > lastIntervalFinishTime) {
                    JsonObject description = new JsonParser().parse(c.getVm().getDescription()).getAsJsonObject();
                    int app = description.get("App").getAsInt();
                    intervalAdmittedTasks[poi][app]++;
                }
            }
        }

        return new IntervalStats(intervalFinishedTasks, intervalAdmittedTasks, intervalViolations, accumulatedResponseTime);
    }

    private void createBrokersAndDatacenters(int pois) {
        edgeBroker = new DatacenterBrokerSimpleExtended[pois];
        for (int poi = 0; poi < pois; poi++) {
            Set<Datacenter> edgeDCList = new HashSet<>();
            Datacenter dc = createDatacenter(EDGE_HOSTS, EDGE_HOST_PES, EDGE_HOST_PE_MIPS, EDGE_HOST_RAM, EDGE_HOST_BW);
            dc.setName("DataCenter" + poi);
            edgeDCList.add(dc);
            edgeBroker[poi] = new DatacenterBrokerSimpleExtended(simulation);
            edgeBroker[poi].setName("AccessPoint" + poi);
            edgeBroker[poi].setDatacenterList(edgeDCList);
        }
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
        // List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < hostPes; i++) {
            // Uses a PeProvisionerSimple by default to provision PEs for VMs
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

    private ArrayList<Vm> createVms(int noOfVms, int app, int flavor) {
        destroyVms();

        int vm_pes = VM_PES[app][flavor];
        int vm_pe_mips = VM_PE_MIPS[app][flavor];
        int vm_ram = VM_RAM;
        int vm_bw = VM_BW;
        final ArrayList<Vm> list = new ArrayList<>(noOfVms);
        for (int i = 0; i < noOfVms; i++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            CloudletSchedulerTimeSharedExtended cloudletScheduler = new CloudletSchedulerTimeSharedExtended();
            final Vm vm = new VmSimple(vm_pe_mips, vm_pes);
            allocatedCores[0][0] = vm_pes;
            vm.setRam(vm_ram).setBw(vm_bw).setSize(1000);
            vm.setCloudletScheduler(cloudletScheduler); // TODO: not sure if suppressing is better or real issue exists
            list.add(vm);
        }
        return list;
    }

    private void destroyVms() {
        System.out.println("\n--------- DESTROYING VMS ---------\n");
        for (int poi = 0; poi < POI; poi++) {
//            System.out.println("POI: " + poi);
            for (int hostID = 0; hostID < EDGE_HOSTS; hostID++) {
                Host host = edgeBroker[poi].getDatacenterList().get(0).getHost(hostID);
                for (Vm vm: host.getVmCreatedList()){
                    vm.setFailed(true);
                    vm.getHost().getVmScheduler().deallocatePesFromVm(vm);
                    host.destroyVm(vm);
                }
            }
        }
    }

    private User[] createAssignedUsers (int usersLowerBound, int usersUpperBound, int distLowerBound, int distUpperBound) {
        int randomNumberOfUsers = usersLowerBound + new Random().nextInt(usersUpperBound-usersLowerBound);
        User[] user = new User[randomNumberOfUsers];
        double energySum = 0;

        // create users with random distance from BS and remaining energy
        for (int i = 0; i < randomNumberOfUsers; i++) {
            double distance = distLowerBound + new Random().nextInt(distUpperBound-distLowerBound);
            double remainingEnergy = new Random().nextInt(80) + 20; // Battery percentage
            energySum += remainingEnergy;
            user[i] = new User(distance, remainingEnergy);
        }
        avgResidualEnergy = energySum / user.length;
        allocatedUsers[0][0] = user.length;
        System.out.println("Average Residual Energy: " + avgResidualEnergy);
        return user;
    }

    private int createRequestRate(User[] assignedUsers) {
        double[] usersTransmissionRate = calculateUsersTransmissionRate(assignedUsers);
        double totalTransmissionRate = Arrays.stream(usersTransmissionRate).sum();
        int totalRequestRate = (int) totalTransmissionRate/TASK_SIZE;

        System.out.println("Assigned Users: " + assignedUsers.length);
        System.out.println("Request Rate to generate: " + totalRequestRate);

        return totalRequestRate;
    }

    private void generateRequests(double[][][] requestRatePerCellPerApp, EventInfo evt) {
        PoissonDistribution pD;
        int tasksToCreate;

        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                int poi = GRID_SIZE * i + j;
                for (int app = 0; app < APPS; app++) {
                    if (requestRatePerCellPerApp[i][j][app] != 0) {
                        tasksToCreate = (int)requestRatePerCellPerApp[i][j][app];
                        taskList[poi][app] = new ArrayList<>();
                        System.out.printf("%n#-----> Creating %d Task(s) at PoI %d, for App %d at time %.0f sec.%n",
                                tasksToCreate, poi, app, evt.getTime());
                        taskList[poi][app] = (ArrayList<TaskSimple>) createTasks(tasksToCreate, poi, app, 0);
                        edgeBroker[poi].submitCloudletList(taskList[poi][app]);
                    } else {
                        System.out.printf("%n#-----> Creating %d Task(s) at PoI %d, for App %d at time %.0f sec.%n", 0,
                                poi, app, evt.getTime());
                    }
                }
            }
        }
    }

    private List<TaskSimple> createTasks(int noOfTasks, int poi, int app, int interArrivalTime) {
        final List<TaskSimple> tempTaskList = new ArrayList<>(noOfTasks);

        //UtilizationModel defining the Tasks use up to 90% of any resource all the time
        final UtilizationModelDynamic utilizationModel = new UtilizationModelDynamic(0.9);

        int submissionDelay = 0;
        for (int i = 0; i < noOfTasks; i++) {
            final TaskSimple task = new TaskSimple(TASK_LENGTH[app], TASK_PES, utilizationModel);
            task.setUtilizationModelRam(UtilizationModel.NULL); // TODO: reconsider if we care about RAM and BW Utilization
            task.setUtilizationModelBw(UtilizationModel.NULL);
            task.setId(Long.parseLong((poi * 10 + app) + Integer.toString(taskCounter[poi][app])));
            task.setSizes(TASK_SIZE);
            task.setSubmissionDelay(submissionDelay);
            task.setBroker(edgeBroker[poi]);
            tempTaskList.add(task);
            submissionDelay += interArrivalTime;
            taskCounter[poi][app]++;
        }

        return tempTaskList;
    }

    private void collectVmStats() {
        for (int poi = 0; poi < POI; poi++) {
            for (int app = 0; app < APPS; app++) {
                for (Vm vm : vmList[0][0]) {
                    if (accumulatedCpuUtil.containsKey(vm.getId()))
                        accumulatedCpuUtil.put(vm.getId(),
                                accumulatedCpuUtil.get(vm.getId()) + vm.getCpuPercentUtilization());
                    else
                        accumulatedCpuUtil.put(vm.getId(), vm.getCpuPercentUtilization());
                }
            }
        }
    }

    private void correctlySetVmDescriptions(ArrayList<Vm> vmList) {
        for (Vm vm: vmList)
            vm.setDescription("{\"App\": " + "0" + " }"); // Vm Description in Json format
    }

    private double[] calculateUsersTransmissionRate(User[] assignedUsers){
        double B = 20 * 180 * Math.pow(10, 3); // Resource Blocks * kHz, Bandwidth.
        double[] sinr = new double[assignedUsers.length]; // SINR (signal to interference plus noise ratio).
        double[] gnk = new double[assignedUsers.length]; // channel gain per user.
        double[] trate = new double[assignedUsers.length]; // channel gain per user.
        double pmax = 0.2; // W, or 23dBm assume p = pmax for each user, as we do not incorporate power management.
        double h = 0.97; // just a constant.
        int a = -3; // the path loss exponent which corresponds to urban and suburban environments.
        double sigma2 = Math.pow(10, (-14.4)) * B; // W, noise power at the BS

//        System.out.println(sigma2);

        // calculate channel gain for each user, based on their distance:
        for (int i = 0; i < assignedUsers.length; i++) {
            gnk[i] = h * Math.pow(assignedUsers[i].distance, a);
        }
//        System.out.println(Arrays.toString(gnk));

        // calculate sinr for each user, based on their channel gain:
        for (int i = 0; i < assignedUsers.length; i++) {
            double sum = 0;
            double p = assignedUsers[i].remainingEnergy * pmax * 0.01; // P is set opportunistically, in relevance to the device's residual energy
            for (int j = 0; j < assignedUsers.length; j++) {
                if (j != i) {
                    sum +=  p * gnk[j];
                }
            }
//            System.out.println(p * gnk[i]);
//            System.out.println(sum);
            sinr[i] = (p * gnk[i]) / (sum + sigma2);
        }
//        System.out.println(Arrays.toString(sinr));

        avgSinr[0][0] = Arrays.stream(sinr).sum()/sinr.length;

//        System.out.println(avgSinr[0][0]);

        // calculate transmission rate for each user, based on their sinr:
        for (int i = 0; i < assignedUsers.length; i++) {
            trate[i] = B * Math.log(1 + sinr[i]);
        }

        return trate;
    }

    private void runSimulationAndPrintResults() {
        simulation.terminateAt(TIME_TO_TERMINATE_SIMULATION);
        simulation.addOnClockTickListener(this::masterOfPuppets);
        simulation.start();
        List<TaskSimple> tasks = new ArrayList<>();
        for (int poi = 0; poi < POI; poi++) {
            tasks.addAll(edgeBroker[poi].getCloudletFinishedList());
        }
        System.out.println(getClass().getSimpleName() + " finished!");
    }
}
