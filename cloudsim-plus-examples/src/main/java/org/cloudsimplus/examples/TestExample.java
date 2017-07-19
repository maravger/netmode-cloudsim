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
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisioner;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmScheduler;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TestExample {
    private static final int HOSTS = 1;
    private static final int HOST_PES = 8;

    private static final int VMS = 1;
    private static final int VM_PES = 2;
    private static final int VM_MIPS = 1000;

    private static final int CLOUDLET_PES = 1;
    private static final int CLOUDLET_LENGHTS[] = {600, 1700}; //MI

    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new TestExample();
    }

    public TestExample() {
        simulation = new CloudSim();
        simulation.addOnClockTickListener(e -> System.out.println("ClockTick: " + e.getTime()));
        datacenter0 = createDatacenter();

        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final List<Cloudlet> finishedCloudlets = broker0.getCloudletFinishedList();
        System.out.println();
        for (Cloudlet c : finishedCloudlets) {
            System.out.println(
                "Cloudlet: "+c.getId()+ " Len: " +c.getLength() + " VM: " + c.getVm().getId() +
                " Start: "+c.getExecStartTime() + " End: " + c.getFinishTime() + " Actual Time: " +c.getActualCpuTime());
        }
    }

    private Datacenter createDatacenter() {
        final List<Host> hostList = new ArrayList<>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            Host host = createHost();
            hostList.add(host);
        }

        DatacenterCharacteristics characteristics = new DatacenterCharacteristicsSimple(hostList);
        final Datacenter dc = new DatacenterSimple(simulation, characteristics, new VmAllocationPolicySimple());
        return dc;
    }

    private Host createHost() {
        List<Pe> peList = new ArrayList<>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000, new PeProvisionerSimple()));
        }

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
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

    private List<Vm> createVms() {
        final List<Vm> list = new ArrayList<>(VMS);
        for (int i = 0; i < VMS; i++) {
            Vm vm =
                new VmSimple(i, VM_MIPS, VM_PES)
                    .setRam(512).setBw(1000).setSize(10000)
                    .setCloudletScheduler(new CloudletSchedulerTimeShared());

            list.add(vm);
        }

        return list;
    }

    private List<Cloudlet> createCloudlets() {
        final List<Cloudlet> list = new ArrayList<>();
        UtilizationModel utilization = new UtilizationModelFull();
        //for (int length : CLOUDLET_LENGHTS) {
        for (int i = 0; i<20000; i++) {
            int length = ThreadLocalRandom.current().nextInt(600, 10000);
            Cloudlet cloudlet =
                new CloudletSimple( length, CLOUDLET_PES)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModel(utilization);
            cloudlet.setSubmissionDelay(i);
            list.add(cloudlet);
        }


        return list;
    }
}
