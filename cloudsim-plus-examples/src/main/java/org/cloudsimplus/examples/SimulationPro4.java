package org.cloudsimplus.examples;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterCharacteristics;
import org.cloudbus.cloudsim.datacenters.DatacenterCharacteristicsSimple;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.distributions.ExponentialDistr;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.util.Log;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RefineryUtilities;

import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Comparator.comparing;

public class SimulationPro4 {

    private static final List<XYSeries> CPU_SERIES_LIST = new ArrayList<>();
    private static final List<XYSeries> RAM_SERIES_LIST = new ArrayList<>();
    private static final List<XYSeries> ART_SERIES_LIST = new ArrayList<>();
    private static final List<XYSeries> ART90_SERIES_LIST = new ArrayList<>();
    private static final List<XYSeries> ART_LL_SERIES_LIST = new ArrayList<>();
    private static final List<XYSeries> ART_UL_SERIES_LIST = new ArrayList<>();
    private static final List<XYSeries> REQUESTS_SUBMITTED_PERSEC = new ArrayList<>();
    private static final List<XYSeries> REQUESTS_FINISHED_PERSEC = new ArrayList<>();
    private static final List<XYSeries> REQUESTS_REJECTED_PERSEC = new ArrayList<>();
    private static final List<XYSeries> REQUESTS_PREDICTED_PERSEC = new ArrayList<>();
    private static final List<XYSeries> REQUESTS_CREATED_PERSEC = new ArrayList<>();
    private static final List<XYSeries> AREA = new ArrayList<>();


    //sim properties
    private static final int TIME_TO_FINISH_SIMULATION = 1000;
    private static final int SAMPLING_INTERVAL = 30; // The interval in which the Datacenter will schedule events.

    private static final int HOSTS = 1;
    private static final int HOST_PES = 100;
    private static final int HOST_PES_MIPS_CAPACITY = 2000;
    private static final int HOST_RAM = 512000; //in MB

    private static final int VMS = 2;
    private static final int VM_MIPS_CAPACITY = 2000;
    private static final int VM_RAM = 16000; //(int) (0.02*HOST_RAM); //in MB, initial

    private static final int CLOUDLET_LENGTH_DESIRED_MEAN = 6000;
    private static final int CLOUDLET_PES_LOWER_BOUND = 1;
    private static final int CLOUDLET_PES_UPPER_BOUND = 3;

    private static final int CONSTANT_TO_CREATE = 1;

    //control properties
    //nothing
    private static final int TIME_TO_CHANGE_AREA = TIME_TO_FINISH_SIMULATION/4;
    private static final double[][] U_PES_MIN = {{10, 20, 30, 40}, {10, 20, 30, 40}};
    private static final double[][] U_PES_MAX = {{30, 40, 50, 60}, {30, 40, 50, 60}};
    private static final double[][] U_REQ_MIN = {{0.5, 3.5, 4.5, 5.5}, {0.5, 3.5, 4.5, 5.5}};
    private static final double[][] U_REQ_MAX = {{3.5, 5.5, 7.5, 10}, {3.5, 5.5, 7.5, 10}};
    private static final double[][] X_ART_REF = {{2.5, 2.5, 2.5, 2.5}, {3.5, 3.5, 3.5, 3.5}};
    private static final double[][] U_PES_REF = {{25, 35, 45, 55}, {25, 35, 45, 55}};
    private static final double[][] U_REQ_REF = {{2.9522, 4.6272, 6.1769, 8.0228}, {3.2358, 5.2899, 7.375, 9.5755}};
    private static final double[][] K1 = {{0, 0, 0, 0.84874}, {1.4286, 0, 0, 0.61825}};
    private static final double[][] K2 = {{-0.21913, -0.34912, -0.52926, -0.79089}, {-0.075496, -0.060028, -0.035707, -0.1213}};
    private static final double[][] A = {{0.519, 0.4245, 0.4492, 0.5092}, {0.519, 0.4245, 0.4492, 0.5092}};
    private static final double[][] B1 = {{-0.1522, -0.0737, -0.0325, -0.0238}, {-0.1522, -0.0737, -0.0325, -0.0238}};
    private static final double[][] B2 = {{1.6962, 0.8684, 0.4597, 0.3161}, {1.6962, 0.8684, 0.4597, 0.3161}};
    private static final double[][] AREAS_AVG_REQ_RATES = {{2.2, 4.3, 6.3, 8}, {2.2, 4.5, 6.5, 8.5}};
    private static final int[][] VM_AREAS_LIST = {{3, 1, 2, 1}, {1, 3, 2, 2}} ;

    private static final int[] VM_PES = {(int)U_PES_REF[0][VM_AREAS_LIST[0][0]], (int)U_PES_REF[1][VM_AREAS_LIST[1][0]]}; //initial
    private static final int MAX_TOTAL_VM_PES = (int)(0.9*HOST_PES);
    private static final int SCHEDULING_INTERVAL = 1; // The interval in which the Datacenter will schedule events.

    private final CloudSim simulation;

    private int vmIndex;
    private int lastFinished;
    private int intervalCount = 0;
    private int working_area1 = 0;
    private double[] totalCpuTime;
    private double[] globaltotalCpuTime;
    private double[] averageResponseTime;
    private double averageHostCpuUsage;
    private double[] averageCpuUsage;
    private double averageHostRamUsage;
    private double[] averageRamUsage;
    private int[] globalhowManyFinished;
    private int randomVmIndexToBind;
    private int previousBrake;
    private int previousChangeBrake;
    private int[] area;
    private int ticks;
    private int globalTicks;
    private int createdCloudlets;
    private int createsVms;
    private int[] numberOfPesForScaling;
    private int[][] howManySubmitted;
    private int[][] howManyCreated;
    private int[][] howManyPredicted;
    private int[][] totalCloudletsRejected;
    private double[] totalCpuPercentUse;
    private double totalHostCpuPercentUse;
    private double[] globaltotalCpuPercentUse;
    private double[] totalRamPercentUse;
    private double totalHostRamPercentUse;
    private double totalHowManyFinished;
    private double[] globaltotalRamPercentUse;

    private double[] u_pes_min;
    private double[] u_pes_max;
    private double[] u_req_min;
    private double[] u_req_max;
    private double[] x_art_ref;
    private double[] u_pes_ref;
    private double[] u_req_ref;
    private double[] k1;
    private double[] k2;
    private double[] a;
    private double[] b1;
    private double[] b2;
    private double[] sprev;
    private double[] bprev = {0.5, 0.5};
    private double[] poissonToCreate;

    // Create a broker for each application
    private DatacenterBroker broker0;
    private List<Host> hostList;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private ArrayList<Double>[] n90thPercentile;

    private FileWriter[] writers = new FileWriter[VMS];

    public static void main(String[] args) throws Exception {

        // exw allaksei
        // org/cloudbus/cloudsim/brokers/DatacenterBrokerAbstract.java:598
        // kai
        // org/cloudbus/cloudsim/brokers/DatacenterBrokerAbstract.java:780
        // kai
        // org/cloudbus/cloudsim/brokers/DatacenterBrokerAbstract.java:322
        // kai
        // org/cloudbus/cloudsim/brokers/DatacenterBrokerAbstract.java:328
        // gia na min typwnei polla
        final XYSeriesCollection[] artCollection = new XYSeriesCollection[VMS];
        final XYSeriesCollection[] reqsCollection = new XYSeriesCollection[VMS];
        final XYSeriesCollection[] predVsCreCollection = new XYSeriesCollection[VMS];
        for (int l = 0; l < VMS; l++) {
            artCollection[l] = new XYSeriesCollection();
            reqsCollection[l] = new XYSeriesCollection();
            predVsCreCollection[l] = new XYSeriesCollection();
        }
        List<Plotter> windowPlots = new ArrayList<>();
        String titleFormater;
        for (int i=0; i < VMS; i++) {
            CPU_SERIES_LIST.add(new XYSeries("CPU"));
            RAM_SERIES_LIST.add(new XYSeries("RAM"));
            ART_SERIES_LIST.add(new XYSeries("ART"));
            ART90_SERIES_LIST.add(new XYSeries("90th"));
            ART_LL_SERIES_LIST.add(new XYSeries("Lower Limit"));
            ART_UL_SERIES_LIST.add(new XYSeries("Upper Limit"));
            REQUESTS_SUBMITTED_PERSEC.add(new XYSeries("Submitted"));
            REQUESTS_FINISHED_PERSEC.add(new XYSeries("Finished"));
            REQUESTS_REJECTED_PERSEC.add(new XYSeries("Rejected"));
            REQUESTS_PREDICTED_PERSEC.add(new XYSeries("Predicted"));
            REQUESTS_CREATED_PERSEC.add(new XYSeries("Created"));
            AREA.add(new XYSeries("area"));
        }
        new SimulationPro4();
        for (int i=0; i < VMS; i++) {
            artCollection[i].addSeries(ART_SERIES_LIST.get(i));
            artCollection[i].addSeries(ART_LL_SERIES_LIST.get(i));
            artCollection[i].addSeries(ART_UL_SERIES_LIST.get(i));
//            artCollection[i].addSeries(ART90_SERIES_LIST.get(i));
            reqsCollection[i].addSeries(REQUESTS_SUBMITTED_PERSEC.get(i));
            reqsCollection[i].addSeries(REQUESTS_FINISHED_PERSEC.get(i));
            reqsCollection[i].addSeries(REQUESTS_REJECTED_PERSEC.get(i));
            predVsCreCollection[i].addSeries(REQUESTS_PREDICTED_PERSEC.get(i));
            predVsCreCollection[i].addSeries(REQUESTS_CREATED_PERSEC.get(i));
            Color color = new Color((int)(Math.random() * 0x1000000));
//            titleFormater = String.format("VM%2d : CPU Usage (percentage)", i);
//            windowPlots.add(new Plotter(titleFormater, CPU_SERIES_LIST.get(i), color.darker()));
//            titleFormater = String.format("VM%2d : RAM Usage (percentage)", i);
//            windowPlots.add(new Plotter(titleFormater, RAM_SERIES_LIST.get(i), color.darker()));
            titleFormater = String.format("VM%2d : Response Time (sec)", i);
            windowPlots.add(new Plotter(titleFormater, artCollection[i], "Seconds"));
            titleFormater = String.format("VM%2d : Requests/Sec", i);
            windowPlots.add(new Plotter(titleFormater, reqsCollection[i], "Rate"));
            titleFormater = String.format("VM%2d : Predicted vs Created /Sec", i);
            windowPlots.add(new Plotter(titleFormater, predVsCreCollection[i], "Rate"));
            titleFormater = String.format("VM%2d : Area", i);
            windowPlots.add(new Plotter(titleFormater, AREA.get(i), color.darker()));
//            windowPlots.add(new Plotter(titleFormater, REQUESTS_SUBMITTED_PERSEC.get(i), color.darker()));
        }
        double i = 0;
        double j = 0;
        for (Plotter win : windowPlots) {
            win.pack();
            RefineryUtilities.positionFrameOnScreen(win, i*0.5, j*0.5);
            win.setVisible(true);
            i++;
            if (i*0.5 > 1) {
                i=0;
                j++;
            }
            if (j*0.5 >1) {
                j = 0.2;
            }
        }
    }

    /**
     * Default constructor that builds the simulation scenario and starts the simulation.
     */
    private SimulationPro4() throws Exception{
        //init filewriters
        String[] csvFiles = new String[VMS];
        String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            csvFiles[vmIndex] = "/Users/avgr_m/Desktop/CSVs/Vm" + vmIndex + "_@" + timeStamp + "_" + TIME_TO_FINISH_SIMULATION/SAMPLING_INTERVAL + "Samples" + ".csv";
            writers[vmIndex] = new FileWriter(csvFiles[vmIndex]);
            CSVUtils.writeLine(writers[vmIndex], Arrays.asList("Time", "ART", "ART90", "CPUutil", "PES", "RAMutil", "RAM", "ReqsSubmitted", "ReqsFinished", "ReqsRejected", "HostCPUutil", "HostRAMutil", "Area"));
        }

        //variables init config
        int intervals = Math.round(TIME_TO_FINISH_SIMULATION/SAMPLING_INTERVAL) + 1;
        totalCloudletsRejected = new int[VMS][intervals];
        howManySubmitted = new int[VMS][intervals];
        howManyCreated = new int[VMS][intervals];
        howManyPredicted = new int[VMS][intervals];
        for (int[] row: howManySubmitted)
            Arrays.fill(row, 0);
        for (int[] row: howManyCreated)
            Arrays.fill(row, 0);
        for (int[] row: howManyPredicted)
            Arrays.fill(row, 0);
        long startTime = System.currentTimeMillis();
        previousBrake = 0;
        previousChangeBrake = 0;
        lastFinished = 0;
        n90thPercentile = new ArrayList[VMS];
        for (int l = 0; l < VMS; l++) {
            n90thPercentile[l] = new ArrayList<>();
        }
        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>();
        totalCpuTime = new double[VMS];
        globaltotalCpuTime = new double[VMS];
        globalhowManyFinished = new int[VMS];
        averageResponseTime = new double[VMS];
        averageCpuUsage = new double[VMS];
        averageRamUsage = new double[VMS];
        totalCpuPercentUse = new double[VMS];
        totalRamPercentUse = new double[VMS];
        globaltotalCpuPercentUse = new double[VMS];
        globaltotalRamPercentUse = new double[VMS];
        numberOfPesForScaling = new int[VMS];
        poissonToCreate = new double[VMS];

        u_pes_min = new double[VMS];
        u_pes_max = new double[VMS];
        u_req_min = new double[VMS];
        u_req_max = new double[VMS];
        x_art_ref = new double[VMS];
        u_pes_ref = new double[VMS];
        u_req_ref = new double[VMS];
        k1 = new double[VMS];
        k2 = new double[VMS];
        a = new double[VMS];
        b1 = new double[VMS];
        b2 = new double[VMS];
        area = new int[VMS];
        sprev = new double[VMS];
        area[0] = VM_AREAS_LIST[0][0];
        area[1] = VM_AREAS_LIST[1][0];
        poissonToCreate[0] = AREAS_AVG_REQ_RATES[0][area[0]] - CONSTANT_TO_CREATE;
        poissonToCreate[1] = AREAS_AVG_REQ_RATES[1][area[1]] - CONSTANT_TO_CREATE;

        u_pes_min[0] = U_PES_MIN[0][area[0]];
        u_pes_max[0] = U_PES_MAX[0][area[0]];
        u_req_min[0] = U_REQ_MIN[0][area[0]];
        u_req_max[0] = U_REQ_MAX[0][area[0]];
        x_art_ref[0] = X_ART_REF[0][area[0]];
        u_pes_ref[0] = U_PES_REF[0][area[0]];
        u_req_ref[0] = U_REQ_REF[0][area[0]];
        k1[0] = K1[0][area[0]];
        k2[0] = K2[0][area[0]];
        a[0] = A[0][area[0]];
        b1[0] = B1[0][area[0]];
        b2[0] = B2[0][area[0]];

        u_pes_min[1] = U_PES_MIN[1][area[1]];
        u_pes_max[1] = U_PES_MAX[1][area[1]];
        u_req_min[1] = U_REQ_MIN[1][area[1]];
        u_req_max[1] = U_REQ_MAX[1][area[1]];
        x_art_ref[1] = X_ART_REF[1][area[1]];
        u_pes_ref[1] = U_PES_REF[1][area[1]];
        u_req_ref[1] = U_REQ_REF[1][area[1]];
        k1[1] = K1[1][area[1]];
        k2[1] = K2[1][area[1]];
        a[1] = A[1][area[1]];
        b1[1] = B1[1][area[1]];
        b2[1] = B2[1][area[1]];

        sprev[0] = u_req_ref[0];
        sprev[1] = u_req_ref[1];

        //simulation init config
        simulation = new CloudSim();
        this.simulation.addOnClockTickListener(this::onClockTickCollectListener);
        this.simulation.terminateAt(TIME_TO_FINISH_SIMULATION);

        //create entities
        createDatacenter();
        broker0 = new DatacenterBrokerSimple(this.simulation);
        vmList.addAll(createListOfScalableVms(VMS));
        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            createConstantCloudletArrival(vmIndex);
            createPoissonCloudletArrival(poissonToCreate[vmIndex], 0, SAMPLING_INTERVAL, (int)(u_req_ref[vmIndex] - CONSTANT_TO_CREATE) * SAMPLING_INTERVAL, vmIndex);
        }
        Collections.sort(cloudletList, comparing(Cloudlet::getSubmissionDelay));
        broker0.submitVmList(vmList);

        //simulation start & measure execution time
        this.simulation.start();

        long estimatedTime = System.currentTimeMillis() - startTime;
        long second = (estimatedTime / 1000) % 60;
        long minute = (estimatedTime / (1000 * 60)) % 60;
        long hour = (estimatedTime / (1000 * 60 * 60)) % 24;
        Log.printFormatted("\nEstimated execution time: %02d:%02d:%02d \n", hour, minute, second);
        for (int i = 0; i < vmList.size(); i++) {
            Log.printFormatted("VM %d, Average CPU usage: %6.1f \n", i, globaltotalCpuPercentUse[i]/globalTicks);
            Log.printFormatted("VM %d, Average RAM usage: %6.1f \n", i, globaltotalRamPercentUse[i]/globalTicks);
            Log.printFormatted("VM %d, Average Response Time: %6.1f \n", i, globaltotalCpuTime[i]/globalhowManyFinished[i]);
            writers[i].flush();
            writers[i].close();
        }
        Log.printFormatted("TOTAL FINISHED " + (int)totalHowManyFinished);
    }

    private void onClockTickCollectListener(EventInfo eventInfo){
        //Sum for every tick
        //Log.printFormattedLine("#Tick at %.2f second\n", eventInfo.getTime());
        ticks++;
//        Log.printFormattedLine("Tick Time " + eventInfo.getTime());
        globalTicks++;
        for (int i = 0; i < vmList.size(); i++) {
            Vm vm = vmList.get(i);
            //Log.printFormattedLine("Current CPU " + vm.getCpuPercentUsage() * 100.0 + "\n");
            totalCpuPercentUse[i] += vm.getCpuPercentUsage() * 100.0;
            globaltotalCpuPercentUse[i] += vm.getCpuPercentUsage() * 100.0;
            totalRamPercentUse[i] += vm.getRam().getPercentUtilization() * 100;
            globaltotalRamPercentUse[i] += vm.getRam().getPercentUtilization() * 100;
        }

        //added when bypassed pauseListener
        //change request rate every TIME_TO_FINISH_SIMULATION/4
        if(((int)eventInfo.getTime() % TIME_TO_CHANGE_AREA == 0) && (int)eventInfo.getTime() != 0 && (int)eventInfo.getTime() != previousChangeBrake){
            Log.printFormatted("---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
            Log.printFormatted("################################################################################################################################################################################################################################\n");
            working_area1++;
            previousChangeBrake = (int)eventInfo.getTime(); // Duplicate pauses bug fix
            Log.printFormattedLine("\n\t#Time to change area @" + eventInfo.getTime());
            Log.printFormattedLine("\n\t#Current poisson rate for Vm1: " + poissonToCreate[0] + ", Current total rate for Vm1: " + (poissonToCreate[0]+CONSTANT_TO_CREATE));
            Log.printFormattedLine("\n\t#Current poisson rate for Vm2: " + poissonToCreate[1] + ", Current total rate for Vm2: " + (poissonToCreate[1]+CONSTANT_TO_CREATE));
            poissonToCreate[0] = AREAS_AVG_REQ_RATES[0][VM_AREAS_LIST[0][working_area1]] - CONSTANT_TO_CREATE;
            poissonToCreate[1] = AREAS_AVG_REQ_RATES[1][VM_AREAS_LIST[1][working_area1]] - CONSTANT_TO_CREATE;
            Log.printFormattedLine("\n\t#Next poisson rate for Vm1: " + poissonToCreate[0] + ", Next poisson rate for Vm1: " + (poissonToCreate[0]+CONSTANT_TO_CREATE));
            Log.printFormattedLine("\n\t#Next poisson rate for Vm2: " + poissonToCreate[1] + ", Next poisson rate for Vm2: " + (poissonToCreate[1]+CONSTANT_TO_CREATE) + "\n");
            Log.printFormatted("################################################################################################################################################################################################################################\n");
        }
        if(((int)eventInfo.getTime() % SAMPLING_INTERVAL == 0) && (int)eventInfo.getTime() != 0 && (int)eventInfo.getTime() != previousBrake){
            previousBrake = (int)eventInfo.getTime(); // Duplicate pauses bug fix
            //Log.printFormattedLine("\n#Simulation paused at %.2f second", eventInfo.getTime());
            scaleResourcesInInterval(eventInfo);
        }
    }

    private void scaleResourcesInInterval(EventInfo pauseInfo){
        double s, spred, b, alpha = 0.5, v = 0.5;
        double totalPesAllocated = 0.0;
        double upperlimit[] = new double[VMS];
        double x0;
        int howManyFinished[] = new int[VMS];
        long prevIntervalPes[] = new long[VMS];
        long prevIntervalRam[] = new long[VMS];
        List<Cloudlet> currentIntervalFinishedCloudlets = new ArrayList<>(broker0.getCloudletFinishedList().subList(lastFinished, broker0.getCloudletFinishedList().size())); //creating a shallow copy
        lastFinished = broker0.getCloudletFinishedList().size();
        vmIndex = 0;
        Arrays.fill(totalCpuTime, 0.0);
        Arrays.fill(averageCpuUsage, 0.0);
        Arrays.fill(averageRamUsage, 0.0);
        Arrays.fill(averageResponseTime, 0.0);

//        Log.printFormatted("Current Interval Finished: " + currentIntervalFinishedCloudlets.size() + "\n");
//        Log.printFormatted("Sum Finished: " + broker0.getCloudletFinishedList().size() + "\n");
        for(Cloudlet cloudlet : currentIntervalFinishedCloudlets){
            vmIndex = cloudlet.getVm().getId();
            howManyFinished[vmIndex]++; //tracks how many cloudlets finished for each vm
            globalhowManyFinished[vmIndex]++; //tracks how many cloudlets finished for each vm
            totalCpuTime[vmIndex] += cloudlet.getActualCpuTime(); //Returns the total execution time of the Cloudlet in seconds.
            globaltotalCpuTime[vmIndex] += cloudlet.getActualCpuTime();
            n90thPercentile[vmIndex].add(cloudlet.getActualCpuTime());
            //Log.printFormatted("Finish Time: " + cloudlet.getActualCpuTime() + "\n");
            //Log.printFormatted("Finish Time: " + cloudlet.getVm().getCloudletScheduler().getCloudletFinishedList().get(vmIndex).getFinishTime() + "\n");
        }

        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            prevIntervalPes[vmIndex] = vmList.get(vmIndex).getProcessor().getCapacity();
            prevIntervalRam[vmIndex] = vmList.get(vmIndex).getRam().getCapacity();
            //Log.printFormatted("PES to be allocated: " +  numberOfPesForScaling[vmIndex] + "\n");
        }

        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            totalHostCpuPercentUse += (totalCpuPercentUse[vmIndex]/ticks)*prevIntervalPes[vmIndex];
            totalHostRamPercentUse += (totalRamPercentUse[vmIndex]/ticks)*prevIntervalRam[vmIndex];
        }

        Log.printFormatted("---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            if (howManyFinished[vmIndex] == 0) {
                averageResponseTime[vmIndex] = 0.0;
            }
            else {
                averageResponseTime[vmIndex] = totalCpuTime[vmIndex] / howManyFinished[vmIndex];
            }
            averageCpuUsage[vmIndex] = totalCpuPercentUse[vmIndex]/ticks;
            averageRamUsage[vmIndex] = totalRamPercentUse[vmIndex]/ticks;

            averageHostCpuUsage = totalHostCpuPercentUse/HOST_PES;
            averageHostRamUsage = totalHostRamPercentUse/HOST_RAM;

            if (averageCpuUsage[vmIndex] == 0) {
                System.out.println("\n \n \n \n FATAL ERROR WITH ZEROS!!!!!!!! \n \n \n \n");
                this.simulation.terminate();
            }

            //calculate ART 90th percentile
            double art90 = get90thPercentile(n90thPercentile[vmIndex]);
            Vm vm = vmList.get(vmIndex);
            printLogAndCsV(pauseInfo, prevIntervalPes, prevIntervalRam, howManyFinished, vm.getId(), vmIndex, art90);
            totalHowManyFinished += howManyFinished[vmIndex];
        }
        //Truncate Accumulators
        for (List<Double> sublist: n90thPercentile) {
            sublist.clear();
        }
        Arrays.fill(totalCpuPercentUse, 0.0);
        Arrays.fill(totalRamPercentUse, 0.0);
        totalHostCpuPercentUse = 0;
        totalHostRamPercentUse = 0;
        ticks = 0;
        intervalCount++;

        //predict & change area
        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            Log.printFormattedLine("\n\t#Predicting for VM: " + vmIndex);
            double prevRealReqRate = (Math.round(howManyCreated[vmIndex][intervalCount-1] * 100.0 /SAMPLING_INTERVAL) / 100.0);
            Log.printFormattedLine("\n\t#Previous request Rate = " + prevRealReqRate);
            s = alpha * prevRealReqRate + (1 - alpha) * (sprev[vmIndex]-bprev[vmIndex]);
            //Log.printFormattedLine("\n\t#s = " + s);
            b = v * (s - sprev[vmIndex]) + (1 - v) * bprev[vmIndex];
            //Log.printFormattedLine("\n\t#b = " + b);
            spred = s + b;
            Log.printFormattedLine("\n\t#Predicted Request Rate = " + spred);
            sprev[vmIndex] = s;
            bprev[vmIndex] = b;
            howManyPredicted[vmIndex][intervalCount] = (int)(spred * SAMPLING_INTERVAL);

            if ((spred - U_REQ_MAX[vmIndex][area[vmIndex]] > 0.5) || (U_REQ_MAX[vmIndex][area[vmIndex]-1] - spred > 0.5)) {
                if (spred < 3.5) {
                    area[vmIndex] = 0;
                } else if (spred < 5.5) {
                    area[vmIndex] = 1;
                } else if (spred < 7.5) {
                    area[vmIndex] = 2;
                } else {
                    area[vmIndex] = 3;
                }
            }

            Log.printFormattedLine("\n\t#New area " + area[vmIndex]);
            u_pes_min[vmIndex] = U_PES_MIN[vmIndex][area[vmIndex]];
            u_pes_max[vmIndex] = U_PES_MAX[vmIndex][area[vmIndex]];
            u_req_min[vmIndex] = U_REQ_MIN[vmIndex][area[vmIndex]];
            u_req_max[vmIndex] = U_REQ_MAX[vmIndex][area[vmIndex]];
            x_art_ref[vmIndex] = X_ART_REF[vmIndex][area[vmIndex]];
            u_pes_ref[vmIndex] = U_PES_REF[vmIndex][area[vmIndex]];
            u_req_ref[vmIndex] = U_REQ_REF[vmIndex][area[vmIndex]];
            k1[vmIndex] = K1[vmIndex][area[vmIndex]];
            k2[vmIndex] = K2[vmIndex][area[vmIndex]];
            a[vmIndex] = A[vmIndex][area[vmIndex]];
            b1[vmIndex] = B1[vmIndex][area[vmIndex]];
            b2[vmIndex] = B2[vmIndex][area[vmIndex]];
            //Log.printFormattedLine("\n\t#New area's K's: " + k1[vmIndex] + " " + k2[vmIndex] + " (just verifying)\n");
        }

        //next interval limit cloudlet arrival rate
        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            //control process
            x0 = averageResponseTime[vmIndex];
            numberOfPesForScaling[vmIndex] = (int)(k1[vmIndex]*(x0 - x_art_ref[vmIndex]) + u_pes_ref[vmIndex]);
            if (numberOfPesForScaling[vmIndex] > u_pes_max[vmIndex]) {
                numberOfPesForScaling[vmIndex] = (int) u_pes_max[vmIndex];
            }
            if (numberOfPesForScaling[vmIndex] < u_pes_min[vmIndex]) {
                numberOfPesForScaling[vmIndex] = (int) u_pes_min[vmIndex];
            }
            totalPesAllocated += numberOfPesForScaling[vmIndex];
            upperlimit[vmIndex] = (k2[vmIndex]*(x0 - x_art_ref[vmIndex]) + u_req_ref[vmIndex]) - CONSTANT_TO_CREATE;
            if (upperlimit[vmIndex] + CONSTANT_TO_CREATE > u_req_max[vmIndex]) {
                upperlimit[vmIndex] = u_req_max[vmIndex];
            }
            if (upperlimit[vmIndex] + CONSTANT_TO_CREATE < u_req_min[vmIndex]) {
                upperlimit[vmIndex] = u_req_min[vmIndex];
            }
        }
        // normalization process
        if (totalPesAllocated > MAX_TOTAL_VM_PES) {
            for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
                numberOfPesForScaling[vmIndex] = (int)(numberOfPesForScaling[vmIndex] * MAX_TOTAL_VM_PES / totalPesAllocated);
                //Log.printFormatted("LIMIT EXCEEDED: FINAL PES to be allocated: " +  numberOfPesForScaling[vmIndex] + "\n");
            }
        }

        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            Vm vm = vmList.get(vmIndex);
            vm.getHost().getVmScheduler().deallocatePesFromVm(vm);
            List<Double> PEsList = new ArrayList<>();
            PEsList.addAll(Collections.nCopies(numberOfPesForScaling[vmIndex], (double) VM_MIPS_CAPACITY));
            vm.getHost().getVmScheduler().allocatePesForVm(vm, PEsList);
            vm.getProcessor().setCapacity(numberOfPesForScaling[vmIndex]);
        }

        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            Log.printFormatted("\nVM: " + vmIndex + "\n");
            Log.printFormatted("Xo: " + averageResponseTime[vmIndex] + " (previous ART)\n");
            Log.printFormatted("U1: " + numberOfPesForScaling[vmIndex] + " (next PES)\n");
            Log.printFormatted("U2: " + (upperlimit[vmIndex] + CONSTANT_TO_CREATE) + " (next Request Rate Limit)\n");
            Log.printFormatted("X1: " + (a[vmIndex]*averageResponseTime[vmIndex] + b1[vmIndex]*numberOfPesForScaling[vmIndex] + b2[vmIndex]*(upperlimit[vmIndex] + CONSTANT_TO_CREATE)) + " (estimated ART) \n\n");
            createPoissonCloudletArrival(poissonToCreate[vmIndex], intervalCount * SAMPLING_INTERVAL, (intervalCount + 1) * SAMPLING_INTERVAL, (int) (upperlimit[vmIndex] * SAMPLING_INTERVAL), vmIndex);
            Log.printFormatted("\nPredicted: " + howManyPredicted[vmIndex][intervalCount] + "\n");
            Log.printFormatted("\nCreated: " + howManyCreated[vmIndex][intervalCount] + "\n");
        }
    }

    private void printLogAndCsV(EventInfo pauseInfo, long prevIntervalPes[], long prevIntervalRam[], int howManyFinished[], int id, int vmIndex, double art90){

        CPU_SERIES_LIST.get(vmIndex).add(pauseInfo.getTime(),averageCpuUsage[vmIndex]);
        RAM_SERIES_LIST.get(vmIndex).add(pauseInfo.getTime(),averageRamUsage[vmIndex]);
        ART_SERIES_LIST.get(vmIndex).add(pauseInfo.getTime(),averageResponseTime[vmIndex]);
        ART90_SERIES_LIST.get(vmIndex).add(pauseInfo.getTime(),art90);
        ART_LL_SERIES_LIST.get(vmIndex).add(pauseInfo.getTime(), X_ART_REF[vmIndex][area[vmIndex]]);
        ART_UL_SERIES_LIST.get(vmIndex).add(pauseInfo.getTime(),2*X_ART_REF[vmIndex][area[vmIndex]]);
        REQUESTS_SUBMITTED_PERSEC.get(vmIndex).add(pauseInfo.getTime(),howManySubmitted[vmIndex][intervalCount]*1.0/SAMPLING_INTERVAL);
        REQUESTS_FINISHED_PERSEC.get(vmIndex).add(pauseInfo.getTime(),howManyFinished[vmIndex]*1.0/SAMPLING_INTERVAL);
        REQUESTS_REJECTED_PERSEC.get(vmIndex).add(pauseInfo.getTime(),totalCloudletsRejected[vmIndex][intervalCount]*1.0/SAMPLING_INTERVAL);
        REQUESTS_CREATED_PERSEC.get(vmIndex).add(pauseInfo.getTime() + 2*SAMPLING_INTERVAL, howManyCreated[vmIndex][intervalCount]*1.0/SAMPLING_INTERVAL);
        REQUESTS_PREDICTED_PERSEC.get(vmIndex).add(pauseInfo.getTime(), howManyPredicted[vmIndex][intervalCount]*1.0/SAMPLING_INTERVAL);
        AREA.get(vmIndex).add(pauseInfo.getTime(),area[vmIndex]);

        try {
            CSVUtils.writeLine(writers[vmIndex], Arrays.asList(
                String.valueOf(Math.round(pauseInfo.getTime() * 100.0) / 100.0),
                String.valueOf(Math.round(averageResponseTime[vmIndex] * 100.0) / 100.0),
                String.valueOf(Math.round(art90 * 100.0) / 100.0),
                String.valueOf(Math.round(averageCpuUsage[vmIndex] * 100.0 / 100.0) / 100.0),
                String.valueOf(Math.round((int)prevIntervalPes[vmIndex] * 100.0 / 100.0) / 100.0),
                String.valueOf(Math.round(averageRamUsage[vmIndex] * 100.0 / 100.0) / 100.0),
                String.valueOf((int)prevIntervalRam[vmIndex]),
                String.valueOf(Math.round(howManySubmitted[vmIndex][intervalCount] * 100.0 /SAMPLING_INTERVAL) / 100.0),
                String.valueOf(howManyFinished[vmIndex]),
                String.valueOf(Math.round(totalCloudletsRejected[vmIndex][intervalCount] * 100.0 /SAMPLING_INTERVAL) / 100.0),
                String.valueOf(Math.round(averageHostCpuUsage * 100.0 / 100.0) / 100.0),
                String.valueOf(Math.round(averageHostRamUsage * 100.0 / 100.0) / 100.0),
                String.valueOf(Math.round(area[vmIndex]))
                )
            );
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.printFormatted("\t\tTime %6.1f: | Vm %d | Average Interval CPU Usage: %6.2f%% | Average Interval Ram Usage: %6.2f%% " +
                "| Average Interval Response Time: %6.2f s \n\t\t\t\t\t | 90th Percentile Interval Response Time: %6.2f s  | PEs allocated: %d | RAM allocated: %dmb | Requests Submitted: %d | Requests Finished: %d" +
                "\n\t\t\t\t\t | Requests Rejected: %d | Average Interval Host CPU Usage: %6.2f%% | Average Interval Host Ram Usage: %6.2f%% | Area: %d \n\n",
            pauseInfo.getTime(),
            id,
            averageCpuUsage[vmIndex],
            averageRamUsage[vmIndex],
            averageResponseTime[vmIndex],
            art90,
            prevIntervalPes[vmIndex],
            prevIntervalRam[vmIndex],
            howManySubmitted[vmIndex][intervalCount],
            howManyFinished[vmIndex],
                totalCloudletsRejected[vmIndex][intervalCount],
            averageHostCpuUsage,
            averageHostRamUsage,
                area[vmIndex]
        );
    }

    private void createPoissonCloudletArrival(double reqsRate, int startTime, int endTime, int upperLimit, int vmindex) {
//        Log.printFormatted("Parameters: Request Rate: " + reqsRate + ", Start Time: " + startTime + ", End Time: " + endTime + ", Upper Limit: " + upperLimit + ", for Vm: " + vmindex + "\n");
        cloudletList.clear();
        boolean stop = false;
        int cloudletPes, nofCloudletsToCreate, totalCloudletsCreated = 0;
        long cloudletLength;
        ExponentialDistr exp = new ExponentialDistr(CLOUDLET_LENGTH_DESIRED_MEAN);
        int secs;
        //poisson
        for (secs = startTime; secs < endTime; secs++) {
            //randomize cloudlets/delay
            nofCloudletsToCreate = getPoisson(reqsRate); //Poisson distribution
            //create cloudlets
            if (((totalCloudletsCreated + nofCloudletsToCreate)>upperLimit) && (!stop)) {
                totalCloudletsRejected[vmindex][(startTime+endTime)/2/SAMPLING_INTERVAL] = (totalCloudletsCreated + nofCloudletsToCreate) - upperLimit;
                nofCloudletsToCreate = upperLimit - totalCloudletsCreated;
                for (int j = 0; j < nofCloudletsToCreate; j++) {
                    //randomize length
                    cloudletLength = (long) exp.sample();
                    if (cloudletLength <= 1000) {
                        cloudletLength = CLOUDLET_LENGTH_DESIRED_MEAN;
                    }
                    //randomize PES needed
                    cloudletPes = ThreadLocalRandom.current().nextInt(CLOUDLET_PES_LOWER_BOUND, CLOUDLET_PES_UPPER_BOUND); //uniform integer
                    //create cloudlet
                    cloudletList.add(createCloudlet(cloudletLength, cloudletPes, secs - startTime, vmindex));
                    totalCloudletsCreated++;
                }
                stop = true;
            }
            if (!stop) {
                for (int j = 0; j < nofCloudletsToCreate; j++) {
                    //randomize length
                    cloudletLength = (long) exp.sample();
                    if (cloudletLength <= 1000) {
                        cloudletLength = CLOUDLET_LENGTH_DESIRED_MEAN;
                    }
                    //randomize PES needed
                    cloudletPes = ThreadLocalRandom.current().nextInt(CLOUDLET_PES_LOWER_BOUND, CLOUDLET_PES_UPPER_BOUND); //uniform integer
                    //create cloudlet
                    cloudletList.add(createCloudlet(cloudletLength, cloudletPes, secs - startTime, vmindex));
                    totalCloudletsCreated++;
                }
            }
            else {
                totalCloudletsRejected[vmindex][(startTime+endTime)/2/SAMPLING_INTERVAL] += nofCloudletsToCreate;
            }
        }
        Log.printFormatted("Requests with Poisson Distribution that actually Arrived at VM: " + totalCloudletsCreated + "\n");
        Log.printFormatted("Requests with Poisson Distribution that were Rejected at VM: " + totalCloudletsRejected[vmindex][(startTime+endTime)/2/SAMPLING_INTERVAL] + "\n");
        broker0.submitCloudletList(cloudletList);
        //bind to VM
//        Log.printFormatted("Cloudlet List Length: " + cloudletList.size() + "\n");
        for (Cloudlet cl : cloudletList) {
//            Log.printFormatted("Submit to VM: " + vmindex + "\n");
            howManySubmitted[vmindex][(int)(cl.getSubmissionDelay()+startTime)/SAMPLING_INTERVAL]++;
//            Log.printFormatted("Submitted at VM: " + cl.getVm().getId() + "\n");
        }
//        Log.printFormatted("How many submitted: " + howManySubmitted[vmindex][(startTime+endTime)/2/SAMPLING_INTERVAL] + " for VM: " + vmindex + "\n");
        howManyCreated[vmindex][((startTime+endTime)/2)/SAMPLING_INTERVAL] = totalCloudletsCreated + totalCloudletsRejected[vmindex][(startTime+endTime)/2/SAMPLING_INTERVAL] + CONSTANT_TO_CREATE*SAMPLING_INTERVAL;
    }

    private void createConstantCloudletArrival(int vmindex) {
        cloudletList.clear();
        int cloudletPes, nofCloudletsToCreate, totalCloudletsCreated = 0;
        long cloudletLength;
//        Random r;
        ExponentialDistr exp = new ExponentialDistr(CLOUDLET_LENGTH_DESIRED_MEAN);
        int secs;
        //constant
        for (secs = 0; secs < TIME_TO_FINISH_SIMULATION; secs++) {
            nofCloudletsToCreate = CONSTANT_TO_CREATE;
            for (int j = 0; j < nofCloudletsToCreate; j++) {
                //r = new Random();
                //cloudletLength = (long) r.nextGaussian() * CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION + CLOUDLET_LENGTH_DESIRED_MEAN; //use: r.nextGaussian()*CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION+CLOUDLET_LENGTH_DESIRED_MEAN;
                cloudletLength = (long)exp.sample();
                //Log.printFormatted("Cloudlet length: " + cloudletLength + "\n");
                if (cloudletLength <= 1000) {
                    cloudletLength = CLOUDLET_LENGTH_DESIRED_MEAN;
                }
                //randomimze PES needed
                cloudletPes = ThreadLocalRandom.current().nextInt(CLOUDLET_PES_LOWER_BOUND, CLOUDLET_PES_UPPER_BOUND); //uniform integer
                //create cloudlet
                cloudletList.add(createCloudlet(cloudletLength, cloudletPes, secs, vmindex));
            }
            totalCloudletsCreated += nofCloudletsToCreate;
        }
        Log.printFormatted("Requests Created with Constant Distribution: " + totalCloudletsCreated + "\n");
        broker0.submitCloudletList(cloudletList);
        //bind to VM
        for (Cloudlet cl : cloudletList) {
//            Log.printFormatted("Submit to VM: " + vmindex + "\n");
            howManySubmitted[vmindex][(int)(cl.getSubmissionDelay())/SAMPLING_INTERVAL]++;
//            Log.printFormatted("Submitted at VM: " + cl.getVm().getId() + "\n");
        }
    }

    private double utilizationIncrement(UtilizationModelDynamic um) {
        return um.getUtilization() + um.getTimeSpan()*10;
    }

    private void createDatacenter() {
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }

        DatacenterCharacteristics characteristics = new DatacenterCharacteristicsSimple(hostList);
        Datacenter dc0 = new DatacenterSimple(simulation, characteristics, new VmAllocationPolicySimple());
        dc0.setSchedulingInterval(SCHEDULING_INTERVAL);
    }

    private Host createHost() {
        List<Pe> peList = new ArrayList<>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(HOST_PES_MIPS_CAPACITY, new PeProvisionerSimple()));
        }

        final long bw = 100000;
        final long storage = 10000000;
        return new HostSimple(HOST_RAM, bw, storage, peList)
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
    }

    private List<Vm> createListOfScalableVms(final int numberOfVms) {
        List<Vm> newList = new ArrayList<>(numberOfVms);
        for (int i = 0; i < numberOfVms; i++) {
            Vm vm = createVm();
            newList.add(vm);
        }

        return newList;
    }

    private Vm createVm() {
        final int id = createsVms++;

        return new VmSimple(id, VM_MIPS_CAPACITY, VM_PES[id])
            .setRam(VM_RAM).setBw(1000).setSize(10000).setBroker(broker0)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    private Cloudlet createCloudlet(long length, int numberOfPes, double delay, int vmindex) {
        final int id = createdCloudlets++;
        //randomly selects a length for the cloudlet
        UtilizationModelDynamic ramModel = new UtilizationModelDynamic(UtilizationModel.Unit.ABSOLUTE, 100);
        ramModel
            .setMaxResourceUtilization(200)
            .setUtilizationUpdateFunction(this::utilizationIncrement);
        UtilizationModel utilizationFull = new UtilizationModelFull();
        Cloudlet cl = new CloudletSimple(id, length, numberOfPes);
        cl.setFileSize(1024)
            .setOutputSize(1024)
            .setUtilizationModelBw(utilizationFull)
            .setUtilizationModelCpu(utilizationFull)
            .setUtilizationModelRam(ramModel)
            .setBroker(broker0)
            .setSubmissionDelay(delay);
        cl.setVm(vmList.get(vmindex));
        return cl;
    }

    private static int getPoisson(double lambda) {
        double L = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;

        do {
            k++;
            p *= Math.random();
        } while (p > L);

        return k - 1;
    }

    public static double get90thPercentile(List<Double> input) {
        Collections.sort(input);
        int size = input.size();
        int pos = (int)Math.round(0.9*size)-1;
        double value;
        if (pos>0) {
            value = input.get(pos);
        }
        else {
            value = 0;
        }
        return value;
    }

}
