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

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class MinimumExample {

    private static final int TIME_TO_FINISH_SIMULATION = 3000;
    private static final double SCHEDULING_INTERVAL = 0.1; // The interval in which the Datacenter will schedule events.
    private static final int SAMPLING_INTERVAL = 30; // The interval in which the Datacenter will schedule events.
    private static final int HOSTS = 1;

    private static final int HOST_PES = 10;
    private static final int HOST_PES_MIPS_CAPACITY = 2000;
    private static final int HOST_RAM = 15000; //in MB
    private static final int HOST_STORAGE = 10000000; //in MB
    private static final int HOST_BW = 100000; //in Megabits/s
    private static final int VMS = 1;
    private static final int VM_PES = 5; //initial
    private static final int VM_MIPS_CAPACITY = 2000;
    private static final int VM_RAM = 5000; //in MB, initial
    private static final int VM_STORAGE = 10000; //in MB
    private static final int VM_BW = 1000;
    private static final int VM_PES_LOWER_BOUND = 2;
    private static final int VM_PES_UPPER_BOUND = 6;

    private final CloudSim simulation;

    private int lastFinished;
    private int intervalCount;
    private int previousBrake;
    private int ticks;
    private int createsVms;
    private int[] howManySubmitted;
    private double totalCpuPercentUse;

    private DatacenterBroker broker0;
    private List<Host> hostList;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    public static void main(String[] args) throws Exception {

        new MinimumExample();
    }

    /**
     * Default constructor that builds the simulation scenario and starts the simulation.
     */
    private MinimumExample() throws Exception {
        //variables init config
        int intervals = Math.round(TIME_TO_FINISH_SIMULATION / SAMPLING_INTERVAL) + 1;
        howManySubmitted = new int[intervals];
        previousBrake = 0;
        lastFinished = 0;
        intervalCount = 0;
        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>();

        //simulation init config
        simulation = new CloudSim();
        this.simulation.addOnClockTickListener(this::onClockTickCollectListener);
        this.simulation.terminateAt(TIME_TO_FINISH_SIMULATION);

        //create entities
        createDatacenter();
        broker0 = new DatacenterBrokerSimple(this.simulation);
        vmList.addAll(createListOfScalableVms(VMS));
        //create a constant cloudlet arrival rate
        //createConstantCloudletArrival();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        //count how many cloudlets are submitted in each interval (for stats purposes only)
        for (Cloudlet cl : cloudletList) {
            howManySubmitted[(int)cl.getSubmissionDelay()/SAMPLING_INTERVAL]++;
        }

        this.simulation.start();
    }

    //collect Vm's cpu percent usage for every tick
    private void onClockTickCollectListener(EventInfo eventInfo) {
        //Sum for every tick
        ticks++;
        Vm vm = vmList.get(0);
        totalCpuPercentUse += vm.getCpuPercentUsage() * 100.0;

        //*fix* stop only once every SAMPLING_INTERVAL
        if (((int) eventInfo.getTime() % SAMPLING_INTERVAL == 0) && (int) eventInfo.getTime() != 0 && (int) eventInfo.getTime() != previousBrake) {
            previousBrake = (int) eventInfo.getTime(); // Duplicate pauses bug fix
            scaleResourcesInInterval(eventInfo);
        }
    }

    //print some stats regarding the previous interval and scale the vm randomly for the next interval
    private void scaleResourcesInInterval(EventInfo pauseInfo) {
        int howManyFinished = 0;
        double totalCpuTime = 0.0;
        double averageCpuUsage;
        double averageResponseTime;

        //create a list with cloudlets finished in this interval
        List<Cloudlet> currentIntervalFinishedCloudlets = new ArrayList<>(broker0.getCloudletFinishedList().subList(lastFinished, broker0.getCloudletFinishedList().size()));
        lastFinished = broker0.getCloudletFinishedList().size();

        //create some average statistics for the interval
        for (Cloudlet cloudlet : currentIntervalFinishedCloudlets) {
            howManyFinished++; //tracks how many cloudlets finished in this interval
            totalCpuTime += cloudlet.getActualCpuTime(); //Returns the total execution time of the Cloudlets finished in this interval, in seconds.
        }
        averageResponseTime = totalCpuTime / howManyFinished;
        averageCpuUsage = totalCpuPercentUse / ticks;

        //scale Vm PES randomly
        int numberOfPesForScaling = ThreadLocalRandom.current().nextInt(VM_PES_LOWER_BOUND, VM_PES_UPPER_BOUND);
        Vm vm = vmList.get(0);
        vm.getHost().getVmScheduler().deallocatePesFromVm(vm);
        List<Double> PEsList = new ArrayList<>();
        PEsList.addAll(Collections.nCopies(numberOfPesForScaling, (double) VM_MIPS_CAPACITY));
        vm.getHost().getVmScheduler().allocatePesForVm(vm, PEsList);
        vm.getProcessor().setCapacity(numberOfPesForScaling);

        //print interval results
        Log.printFormatted("\t\tTime %6.1f: | Average Interval CPU Usage: %6.2f%% " +
                "| Average Interval Response Time: %6.2f s | Requests Submitted: %d | Requests Finished: %d" + "\n",
            pauseInfo.getTime(),
            averageCpuUsage,
            averageResponseTime,
            howManySubmitted[intervalCount],
            howManyFinished
        );

        //Truncate Accumulators
        ticks = 0;
        totalCpuPercentUse = 0.0;
        intervalCount++;
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

    private List<Cloudlet> createCloudlets() {
        final List<Cloudlet> list = new ArrayList<>();
        UtilizationModel utilization = new UtilizationModelFull();
        for (int i = 0; i<2000; i++) {
            int length = ThreadLocalRandom.current().nextInt(600, 900);
            Cloudlet cloudlet =
                new CloudletSimple(length, 3)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModel(utilization);
            cloudlet.setSubmissionDelay(i);
            list.add(cloudlet);
        }
        return list;
    }
}
