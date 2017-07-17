package org.cloudsimplus.examples;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.events.SimEvent;
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
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RefineryUtilities;

import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Comparator.comparingDouble;

public class Simulation1 {

    private static final List<XYSeries> CPU_SERIES_LIST = new ArrayList<XYSeries>();
    private static final List<XYSeries> RAM_SERIES_LIST = new ArrayList<XYSeries>();
    private static final List<XYSeries> ART_SERIES_LIST = new ArrayList<XYSeries>();
    private static final List<XYSeries> ART90_SERIES_LIST = new ArrayList<XYSeries>();
    private static final List<XYSeries> REQUESTS_SUBMITTED_PERSEC = new ArrayList<XYSeries>();

    private static final int TIME_TO_FINISH_SIMULATION = 2000;
    private static final int SCHEDULING_INTERVAL = 1; // The interval in which the Datacenter will schedule events.
    //private static final double SCHEDULING_INTERVAL = 0.5; //for more precise scheduling interval; not sure if working tbh
    private static final int SAMPLING_INTERVAL = 30; // The interval in which the Datacenter will schedule events.
    private static final int HOSTS = 1;

    private static final int MIN_CLOUDLETS_PERDELAY = 1;
    private static final int MAX_CLOUDLETS_PERDELAY = 7;
    private static final int CONSTANT_TO_CREATE = (int)((MAX_CLOUDLETS_PERDELAY+MIN_CLOUDLETS_PERDELAY)/4);
    private static final int HOST_PES = 100;
    private static final int HOST_PES_MIPS_CAPACITY = 2000;
    private static final int HOST_RAM = 512000; //in MB
    private static final int HOST_STORAGE = 10000000; //in MB
    private static final int HOST_BW = 100000; //in Megabits/s
    private static final int VMS = 2;
    private static final int VM_PES = 50; //initial
    private static final int VM_MIPS_CAPACITY = 2000;
    private static final int VM_RAM = (int) (0.02*HOST_RAM); //in MB, initial
    //private static final int VM_RAM = 100000; //in MB, initial
    private static final int VM_STORAGE = 10000; //in MB
    private static final int VM_BW = 1000;
    private static final int VM_PES_LOWER_BOUND = 15;
    private static final int VM_PES_UPPER_BOUND = 30;
    private static final double VM_RAM_LOWER_BOUND = 89.0; //percentage
    private static final double VM_RAM_UPPER_BOUND = 89.1; //percentage
    private static final int MAX_TOTAL_VM_PES = 90;
    private static final double MAX_VM_RAM = 0.9; //percentage
    private static final int CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION = 2000;
    private static final int CLOUDLET_LENGTH_DESIRED_MEAN = 6000;
    private static final int CLOUDLET_PES_LOWER_BOUND = 1;
    private static final int CLOUDLET_PES_UPPER_BOUND = 3;
    private static final int INTERARRIVAL_DELAY_LOWER_BOUND = 1;
    private static final int INTERARRIVAL_DELAY_UPPER_BOUND = 2;

    private final CloudSim simulation;

    private int vmIndex;
    private int lastFinished;
    private int intervalCount = 0;
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
    private int ticks;
    private int globalTicks;
    private int createdCloudlets;
    private int createsVms;
    private int[] numberOfPesForScaling;
    private int[][] howManySubmitted;
    private double[] percentageOfRamForScaling;
    private double[] totalCpuPercentUse;
    private double totalHostCpuPercentUse;
    private double[] globaltotalCpuPercentUse;
    private double[] totalRamPercentUse;
    private double totalHostRamPercentUse;
    private double[] globaltotalRamPercentUse;
    private double[] totalTime;

    // Create a broker for each application
    private DatacenterBroker broker0;
    private List<Host> hostList;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private List<Cloudlet> finishedCloudletsAcu;
    private ArrayList<Double>[] n90thPercentile;

    private FileWriter[] writers = new FileWriter[VMS];

    public static void main(String[] args) throws Exception {

        // exw allaksei
        // org/cloudbus/cloudsim/brokers/DatacenterBrokerAbstract.java:598
        // kai
        // org/cloudbus/cloudsim/brokers/DatacenterBrokerAbstract.java:780
        // gia na min typwnei polla
        final XYSeriesCollection[] collection = new XYSeriesCollection[VMS];
        for (int l = 0; l < VMS; l++) {
            collection[l] = new XYSeriesCollection();
        }
        List<Plotter> windowPlots = new ArrayList<>();
        String titleFormater;
        for (int i=0; i < VMS; i++) {
            CPU_SERIES_LIST.add(new XYSeries("CPU"));
            RAM_SERIES_LIST.add(new XYSeries("RAM"));
            ART_SERIES_LIST.add(new XYSeries("ART"));
            ART90_SERIES_LIST.add(new XYSeries("90th"));
            REQUESTS_SUBMITTED_PERSEC.add(new XYSeries("Reqs/Sec"));
        }
        new Simulation1();
        for (int i=0; i < VMS; i++) {
            collection[i].addSeries(ART_SERIES_LIST.get(i));
            collection[i].addSeries(ART90_SERIES_LIST.get(i));
            Color color = new Color((int)(Math.random() * 0x1000000));
            titleFormater = String.format("VM%2d : CPU Usage (percentage)", i);
            windowPlots.add(new Plotter(titleFormater, CPU_SERIES_LIST.get(i), color.darker()));
            titleFormater = String.format("VM%2d : RAM Usage (percentage)", i);
            windowPlots.add(new Plotter(titleFormater, RAM_SERIES_LIST.get(i), color.darker()));
            titleFormater = String.format("VM%2d : Response Time (sec)", i);
            windowPlots.add(new Plotter(titleFormater, collection[i], "Seconds"));
            titleFormater = String.format("VM%2d : Requests Submitted/Sec", i);
            windowPlots.add(new Plotter(titleFormater, REQUESTS_SUBMITTED_PERSEC.get(i), color.darker()));
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
    private Simulation1() throws Exception{
        //init filewriters
        String[] csvFiles = new String[VMS];
        String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            csvFiles[vmIndex] = "/Users/avgr_m/Desktop/CSVs/Vm" + vmIndex + "_@" + timeStamp + "_" + TIME_TO_FINISH_SIMULATION/SAMPLING_INTERVAL + "Samples" + ".csv";
            writers[vmIndex] = new FileWriter(csvFiles[vmIndex]);
            CSVUtils.writeLine(writers[vmIndex], Arrays.asList("Time", "ART", "ART90", "CPUutil", "PES", "RAMutil", "RAM", "ReqsSubmitted", "ReqsFinished", "HostCPUutil", "HostRAMutil"));
        }

        //variables init config
        int intervals = Math.round(TIME_TO_FINISH_SIMULATION/SAMPLING_INTERVAL) + 1;
        howManySubmitted = new int[VMS][intervals];
        for (int[] row: howManySubmitted)
            Arrays.fill(row, 0);
        long startTime = System.currentTimeMillis();
        previousBrake = 0;
        lastFinished = 0;
        finishedCloudletsAcu = new ArrayList<>();
        n90thPercentile = new ArrayList[VMS];
        for (int l = 0; l < VMS; l++) {
            n90thPercentile[l] = new ArrayList<Double>();
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
        percentageOfRamForScaling = new double[VMS];
        totalCpuPercentUse = new double[VMS];
        totalRamPercentUse = new double[VMS];
        globaltotalCpuPercentUse = new double[VMS];
        globaltotalRamPercentUse = new double[VMS];
        totalTime = new double[VMS];
        numberOfPesForScaling = new int[VMS];

        //simulation init config
        simulation = new CloudSim();
        simulation.addOnClockTickListener(this::onClockTickCollectListener);
        simulation.addOnEventProcessingListener(simEvent -> pauseSimulationAtSpecificTime(simEvent));
        simulation.addOnSimulationPausedListener(pauseInfo -> {
            try {
                printCloudletsFinishedInIntervalAndResumeSimulation(pauseInfo);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        simulation.terminateAt(TIME_TO_FINISH_SIMULATION);

        //create entities
        createDatacenter();
        broker0 = new DatacenterBrokerSimple(simulation);
        vmList.addAll(createListOfScalableVms(VMS));
        //createUniformCloudletArrival();
        //createSinusoidCloudletArrival();
        createConstantCloudletArrival();
        createPoissonCloudletArrival();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        //bind cloudlets to vm
        int intrvl = SAMPLING_INTERVAL;
//        Log.printFormatted("CloudletList Length " + cloudletList.size());
        for (Cloudlet cl : cloudletList) {
            randomVmIndexToBind = ThreadLocalRandom.current().nextInt(0, VMS);
            broker0.bindCloudletToVm(cl,vmList.get(randomVmIndexToBind)); // cloudlets must be already submitted to be bounded
            howManySubmitted[randomVmIndexToBind][(int)cl.getSubmissionDelay()/SAMPLING_INTERVAL]++;
        }

        //simulation start & measure execution time
        simulation.start();

        long estimatedTime = System.currentTimeMillis() - startTime;
        long second = (estimatedTime / 1000) % 60;
        long minute = (estimatedTime / (1000 * 60)) % 60;
        long hour = (estimatedTime / (1000 * 60 * 60)) % 24;
        Log.printFormatted("\nEstimated execution time: %02d:%02d:%02d \n", hour, minute, second);
        //printSimulationResults();
        for (int i = 0; i < vmList.size(); i++) {
            Log.printFormatted("VM %d, Average CPU usage: %6.1f \n", i, globaltotalCpuPercentUse[i]/globalTicks);
            Log.printFormatted("VM %d, Average RAM usage: %6.1f \n", i, globaltotalRamPercentUse[i]/globalTicks);
            Log.printFormatted("VM %d, Average Response Time: %6.1f \n", i, globaltotalCpuTime[i]/globalhowManyFinished[i]);
            writers[i].flush();
            writers[i].close();
        }
    }

    private void onClockTickCollectListener(EventInfo eventInfo) {
        //Sum for every tick
        //Log.printFormattedLine("#Tick at %.2f second\n", eventInfo.getTime());
        ticks++;
        globalTicks++;
        for (int i = 0; i < vmList.size(); i++) {
            Vm vm = vmList.get(i);
            //Log.printFormattedLine("Current CPU" + vm.getCpuPercentUsage() * 100.0);
            totalCpuPercentUse[i] += vm.getCpuPercentUsage() * 100.0;
            globaltotalCpuPercentUse[i] += vm.getCpuPercentUsage() * 100.0;
            totalRamPercentUse[i] += vm.getRam().getPercentUtilization() * 100;
            globaltotalRamPercentUse[i] += vm.getRam().getPercentUtilization() * 100;
        }
    }

    private void pauseSimulationAtSpecificTime(SimEvent simEvent) {
        if(((int)simEvent.getTime() % SAMPLING_INTERVAL == 0) && (int)simEvent.getTime() != 0 && (int)simEvent.getTime() != previousBrake){
            previousBrake = (int)simEvent.getTime(); // Duplicate pauses bug fix
            simulation.pause();
        }
    }

    private void printCloudletsFinishedInIntervalAndResumeSimulation(EventInfo pauseInfo) throws IOException {
        Log.printFormattedLine("\n#Simulation paused at %.2f second", pauseInfo.getTime());
        scaleResourcesInInterval(pauseInfo);
        simulation.resume();
    }

    private void scaleResourcesInInterval(EventInfo pauseInfo) throws IOException {
        double totalPesAllocated = 0.0;
        double totalRamAllocated = 0.0;
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

        //Log.printFormatted("Current Interval Finished: " + currentIntervalFinishedCloudlets.size() + "\n");
        //Log.printFormatted("Sum Finished: " + broker0.getCloudletFinishedList().size() + "\n");
        for(Cloudlet cloudlet : currentIntervalFinishedCloudlets){
            vmIndex = cloudlet.getVm().getId();
            howManyFinished[vmIndex]++; //tracks how many cloudlets finished for each vm
            globalhowManyFinished[vmIndex]++; //tracks how many cloudlets finished for each vm
            totalCpuTime[vmIndex] += cloudlet.getActualCpuTime(); //Returns the total execution time of the Cloudlet in seconds.
            globaltotalCpuTime[vmIndex] += cloudlet.getActualCpuTime();
            n90thPercentile[vmIndex].add(cloudlet.getActualCpuTime());
        }
        //Log.printFormatted("How Many After " + howManyFinished[1] + "\n");

        // randomization process
        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            prevIntervalPes[vmIndex] = vmList.get(vmIndex).getProcessor().getCapacity();
            prevIntervalRam[vmIndex] = vmList.get(vmIndex).getRam().getCapacity();
            numberOfPesForScaling[vmIndex] = ThreadLocalRandom.current().nextInt(VM_PES_LOWER_BOUND, VM_PES_UPPER_BOUND); //uniform distribution for Vm CPU share
            //Log.printFormatted("PES to be allocated" +  numberOfPesForScaling[vmIndex] + "\n");
            //percentageOfRamForScaling[vmIndex] = ThreadLocalRandom.current().nextDouble(VM_RAM_LOWER_BOUND, VM_RAM_UPPER_BOUND) / 100; //uniform distribution for Vm CPU share
            totalPesAllocated += numberOfPesForScaling[vmIndex];
            //totalRamAllocated += percentageOfRamForScaling[vmIndex];
        }

        // normalization process
        if (totalPesAllocated > MAX_TOTAL_VM_PES) {
            for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
                numberOfPesForScaling[vmIndex] = (int)(numberOfPesForScaling[vmIndex] * MAX_TOTAL_VM_PES / totalPesAllocated);
                //Log.printFormatted("LIMIT EXCEEDED: FINAL PES to be allocated: " +  numberOfPesForScaling[vmIndex] + "\n");
            }
        }
        //if (totalRamAllocated > MAX_VM_RAM) {
        //    for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
        //        percentageOfRamForScaling[vmIndex] = (int)(percentageOfRamForScaling[vmIndex] * MAX_VM_RAM / totalRamAllocated);
        //    }
        //}

        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            totalHostCpuPercentUse += (totalCpuPercentUse[vmIndex]/ticks)*prevIntervalPes[vmIndex];
            totalHostRamPercentUse += (totalRamPercentUse[vmIndex]/ticks)*prevIntervalRam[vmIndex];
        }

        for (int vmIndex = 0; vmIndex < VMS; vmIndex++) {
            if (howManyFinished[vmIndex]==0) {
                averageResponseTime[vmIndex] = 0.0;
            }
            else {
                averageResponseTime[vmIndex] = totalCpuTime[vmIndex] / howManyFinished[vmIndex];
            }
            averageCpuUsage[vmIndex] = totalCpuPercentUse[vmIndex]/ticks;
            averageRamUsage[vmIndex] = totalRamPercentUse[vmIndex]/ticks;

            averageHostCpuUsage = totalHostCpuPercentUse/HOST_PES;
            averageHostRamUsage = totalHostRamPercentUse/HOST_RAM;

            //new technique
            Vm vm = vmList.get(vmIndex);
            vm.getHost().getVmScheduler().deallocatePesFromVm(vm);
            List<Double> PEsList = new ArrayList<Double>();
            PEsList.addAll(Collections.nCopies(numberOfPesForScaling[vmIndex],(double) 2000));
            vm.getHost().getVmScheduler().allocatePesForVm(vm, PEsList);
            vm.getProcessor().setCapacity(numberOfPesForScaling[vmIndex]);

            //calculate ART 90th percentile
            double art90 = get90thPercentile(n90thPercentile[vmIndex]);

            printLogAndCsV(pauseInfo, prevIntervalPes, prevIntervalRam, howManyFinished, vm.getId(), vmIndex, art90);
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
    }

    private void printLogAndCsV(EventInfo pauseInfo, long prevIntervalPes[], long prevIntervalRam[], int howManyFinished[], int id, int vmIndex, double art90) throws IOException {

        CPU_SERIES_LIST.get(vmIndex).add(pauseInfo.getTime(),averageCpuUsage[vmIndex]);
        RAM_SERIES_LIST.get(vmIndex).add(pauseInfo.getTime(),averageRamUsage[vmIndex]);
        ART_SERIES_LIST.get(vmIndex).add(pauseInfo.getTime(),averageResponseTime[vmIndex]);
        ART90_SERIES_LIST.get(vmIndex).add(pauseInfo.getTime(),art90);
        REQUESTS_SUBMITTED_PERSEC.get(vmIndex).add(pauseInfo.getTime(),howManySubmitted[vmIndex][intervalCount]*1.0/SAMPLING_INTERVAL);


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
            String.valueOf(Math.round(averageHostCpuUsage * 100.0 / 100.0) / 100.0),
            String.valueOf(Math.round(averageHostRamUsage * 100.0 / 100.0) / 100.0)
            )
        );

        Log.printFormatted("\t\tTime %6.1f: | Vm %d | Average Interval CPU Usage: %6.2f%% | Average Interval Ram Usage: %6.2f%% " +
                "| Average Interval Response Time: %6.2f s | 90th Percentile Interval Response Time: %6.2f s  | PEs allocated: %d | RAM allocated: %dmb | Requests Submitted: %d | Requests Finished: %d" +
                "| Average Interval Host CPU Usage: %6.2f%% | Average Interval Host Ram Usage: %6.2f%% \n",
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
            averageHostCpuUsage,
            averageHostRamUsage
        );
    }

    private void printSimulationResults() {
        //List<Cloudlet> finishedCloudlets = broker0.getCloudletsFinishedList();
        //append last stray finished cloudlets
        finishedCloudletsAcu.addAll(broker0.getCloudletFinishedList());
        List<Cloudlet> finishedCloudlets = finishedCloudletsAcu;
        Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        Comparator<Cloudlet> sortByStartTime = comparingDouble(c -> c.getExecStartTime());
        finishedCloudlets.sort(sortByVmId.thenComparing(sortByStartTime));
        new CloudletsTableBuilder(finishedCloudlets).build();
    }

    private void createUniformCloudletArrival() {
        int cloudletPes, nofCloudletsToCreate, totalCloudletsCreated = 0;
        long cloudletLength;
        Random r;
        int secs = 0;
        //Creates a List of Cloudlets that will start running immediately when the simulation starts
        while (secs<TIME_TO_FINISH_SIMULATION) {
            //randomize cloudlets/delay
            nofCloudletsToCreate = ThreadLocalRandom.current().nextInt(MIN_CLOUDLETS_PERDELAY, MAX_CLOUDLETS_PERDELAY); //uniform integer
            //create cloudlets
            for (int j = 0; j < nofCloudletsToCreate; j++) {
                //randomize length
                //r = new Random();
                //cloudletLength = (long)r.nextGaussian()*CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION+CLOUDLET_LENGTH_DESIRED_MEAN; //use: r.nextGaussian()*CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION+CLOUDLET_LENGTH_DESIRED_MEAN;
                cloudletLength = (long) new ExponentialDistr(CLOUDLET_LENGTH_DESIRED_MEAN).sample(); //use: r.nextGaussian()*CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION+CLOUDLET_LENGTH_DESIRED_MEAN;
                if (cloudletLength <= 0) {
                    cloudletLength = CLOUDLET_LENGTH_DESIRED_MEAN;
                }
                //randomimze PES needed
                cloudletPes = ThreadLocalRandom.current().nextInt(CLOUDLET_PES_LOWER_BOUND, CLOUDLET_PES_UPPER_BOUND); //uniform integer
                //create cloudlet
                cloudletList.add(createCloudlet(cloudletLength, cloudletPes, secs));
            }
            totalCloudletsCreated += nofCloudletsToCreate;
            secs += ThreadLocalRandom.current().nextInt(INTERARRIVAL_DELAY_LOWER_BOUND, INTERARRIVAL_DELAY_UPPER_BOUND);
        }
        Log.printFormatted("Requests Created with Uniform Distribution: " + totalCloudletsCreated + "\n");
    }

    private void createSinusoidCloudletArrival() {
        int cloudletPes, nofCloudletsToCreate, totalCloudletsCreated = 0;
        long cloudletLength;
        Random r;
        int secs = 0;
        while (secs<TIME_TO_FINISH_SIMULATION) {
            nofCloudletsToCreate = (int) Math.round(((MIN_CLOUDLETS_PERDELAY/2) * (Math.sin(secs*Math.PI/(SAMPLING_INTERVAL/2))+1)) + (MAX_CLOUDLETS_PERDELAY - MIN_CLOUDLETS_PERDELAY));
            for (int j = 0; j < nofCloudletsToCreate; j++) {
                //randomize length
                //r = new Random();
                //cloudletLength = (long)r.nextGaussian()*CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION+CLOUDLET_LENGTH_DESIRED_MEAN; //use: r.nextGaussian()*CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION+CLOUDLET_LENGTH_DESIRED_MEAN;
                cloudletLength = (long) new ExponentialDistr(CLOUDLET_LENGTH_DESIRED_MEAN).sample();
                if (cloudletLength <= 0) {
                    cloudletLength = CLOUDLET_LENGTH_DESIRED_MEAN;
                }
                //randomimze PES needed
                cloudletPes = ThreadLocalRandom.current().nextInt(CLOUDLET_PES_LOWER_BOUND, CLOUDLET_PES_UPPER_BOUND); //uniform integer
                //create cloudlet
                cloudletList.add(createCloudlet(cloudletLength, cloudletPes, secs));
            }
            totalCloudletsCreated += nofCloudletsToCreate;
            secs += ThreadLocalRandom.current().nextInt(INTERARRIVAL_DELAY_LOWER_BOUND, INTERARRIVAL_DELAY_UPPER_BOUND);
        }
        Log.printFormatted("Requests Created with Sinusoid Distribution: " + totalCloudletsCreated + "\n");
    }

    private void createPoissonCloudletArrival() {
        int cloudletPes, nofCloudletsToCreate, totalCloudletsCreated = 0;
        long cloudletLength;
        Random r;
        int secs = 0;
        //Creates a List of Cloudlets that will start running immediately when the simulation starts
        while (secs<TIME_TO_FINISH_SIMULATION) {
            //randomize cloudlets/delay
            nofCloudletsToCreate = getPoisson((MAX_CLOUDLETS_PERDELAY+MIN_CLOUDLETS_PERDELAY)/2); //Poisson distribution
            //create cloudlets
            for (int j = 0; j < nofCloudletsToCreate; j++) {
                //randomize length
                //r = new Random();
                //cloudletLength = (long)r.nextGaussian()*CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION+CLOUDLET_LENGTH_DESIRED_MEAN; //use: r.nextGaussian()*CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION+CLOUDLET_LENGTH_DESIRED_MEAN;
                cloudletLength = (long) new ExponentialDistr(CLOUDLET_LENGTH_DESIRED_MEAN).sample();
                if (cloudletLength <= 0) {
                    cloudletLength = CLOUDLET_LENGTH_DESIRED_MEAN;
                }
                //randomimze PES needed
                cloudletPes = ThreadLocalRandom.current().nextInt(CLOUDLET_PES_LOWER_BOUND, CLOUDLET_PES_UPPER_BOUND); //uniform integer
                //create cloudlet
                cloudletList.add(createCloudlet(cloudletLength, cloudletPes, secs));
            }
            totalCloudletsCreated += nofCloudletsToCreate;
            secs += ThreadLocalRandom.current().nextInt(INTERARRIVAL_DELAY_LOWER_BOUND, INTERARRIVAL_DELAY_UPPER_BOUND);
        }
        Log.printFormatted("Requests Created with Poisson Distribution: " + totalCloudletsCreated + "\n");
    }

    private void createConstantCloudletArrival() {
        int cloudletPes, nofCloudletsToCreate, totalCloudletsCreated = 0;
        long cloudletLength;
        Random r;
        int secs = 0;
        //constant
        for (secs = 0; secs < TIME_TO_FINISH_SIMULATION; secs++) {
            nofCloudletsToCreate = CONSTANT_TO_CREATE;
            for (int j = 0; j < nofCloudletsToCreate; j++) {
                //r = new Random();
                //cloudletLength = (long) r.nextGaussian() * CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION + CLOUDLET_LENGTH_DESIRED_MEAN; //use: r.nextGaussian()*CLOUDLET_LENGTH_DESIRED_STANDARD_DEVIATION+CLOUDLET_LENGTH_DESIRED_MEAN;
                cloudletLength = (long) new ExponentialDistr(CLOUDLET_LENGTH_DESIRED_MEAN).sample();
                Log.printFormatted("Cloudlet length: " + cloudletLength + "\n");
                if (cloudletLength <= 0) {
                    cloudletLength = CLOUDLET_LENGTH_DESIRED_MEAN;
                }
                //randomimze PES needed
                cloudletPes = ThreadLocalRandom.current().nextInt(CLOUDLET_PES_LOWER_BOUND, CLOUDLET_PES_UPPER_BOUND); //uniform integer
                //create cloudlet
                cloudletList.add(createCloudlet(cloudletLength, cloudletPes, secs));
            }
            totalCloudletsCreated += nofCloudletsToCreate;
        }
        Log.printFormatted("Requests Created with Constant Distribution: " + totalCloudletsCreated + "\n");
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

        final long bw = HOST_BW;
        final long storage = HOST_STORAGE;
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

        return new VmSimple(id, VM_MIPS_CAPACITY, VM_PES)
            .setRam(VM_RAM).setBw(VM_BW).setSize(VM_STORAGE).setBroker(broker0)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    private Cloudlet createCloudlet(long length, int numberOfPes, double delay) {
        final int id = createdCloudlets++;
        //randomly selects a length for the cloudlet
        UtilizationModelDynamic ramModel = new UtilizationModelDynamic(UtilizationModel.Unit.ABSOLUTE, 200);
        ramModel
            .setMaxResourceUtilization(500)
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
//        System.out.println("Before Sorting:");
//        for(double counter: input){
//            System.out.println(counter);
//        }
        Collections.sort(input);
//        System.out.println("After Sorting:");
//        for(double counter: input){
//            System.out.println(counter);
//        }
        int size = input.size();
        double value = input.get((int)Math.round(0.9*size)-1);
//        System.out.println("Position:");
//        System.out.println((int)Math.round(0.9*size));
//        System.out.println("Value:");
//        System.out.println(value);
        return value;
    }
}
