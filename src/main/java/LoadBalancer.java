import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.ArrayList;
import java.util.Random;

// One per POI
public class LoadBalancer {
    private int poiID;
    private ArrayList<Vm> vmPool;
    public ArrayList<Vm>[] vmsOfApp;
    private int[] totalVmCoresPerApp;
    private ArrayList<Double>[] lbCumulativeProbabilities;

    public LoadBalancer(ArrayList<Vm> vmPool, int apps, int poi) {
        this.poiID = poi;
        this.vmPool = vmPool;
        sortVmsPerApp(apps);
        calculateLoadBalancingProbabilities(apps);
    }

    private void sortVmsPerApp(int apps) {
        ArrayList<Vm>[] vmsOfApp = new ArrayList[apps];
        int[] totalVmCoresPerApp = new int[apps];
        for (int app = 0; app < apps; app++) {
            vmsOfApp[app] = new ArrayList<>();
//            System.out.println("--- VMS of App: " + app + " ---");
            for (Vm vm : this.vmPool) {
                if (vm.getDescription().equals("{\"App\": " + app + " }")) {
                    vmsOfApp[app].add(vm);
//                    System.out.println("VM: " + vm.getId() + ", Cores: " + vm.getNumberOfPes());
                    totalVmCoresPerApp[app] += vm.getNumberOfPes();
                }
            }
//            System.out.println("App: " + app + ", Total Cores: " + totalVmCoresPerApp[app]);
        }
        this.vmsOfApp = vmsOfApp;
        this.totalVmCoresPerApp = totalVmCoresPerApp;
    }

    private void calculateLoadBalancingProbabilities(int apps) {
        ArrayList<Double>[] lbProbabilities = new ArrayList[apps];
        ArrayList<Double>[] lbCumulativeProbabilities = new ArrayList[apps];
        for (int app = 0; app < apps; app++) {
            lbProbabilities[app] = new ArrayList<>();
            lbCumulativeProbabilities[app] = new ArrayList<>();
            for (Vm vm : vmsOfApp[app])
                lbProbabilities[app].add((double) vm.getNumberOfPes() / totalVmCoresPerApp[app]);
//            System.out.println("App: " + app + ", Load Balancing Probabilities: " + lbProbabilities[app]);
            double prevP = 0;
            for (double p : lbProbabilities[app]) {
                lbCumulativeProbabilities[app].add(p + prevP);
                prevP += p;
            }
//            System.out.println("POI: " + this.poiID + ", App: " + app + ", Load Balancing Cumulative Probabilities: "
//                    + lbCumulativeProbabilities[app]);
        }
        this.lbCumulativeProbabilities = lbCumulativeProbabilities;
    }

    public void balanceTasks(ArrayList<TaskSimple> tasklist, int app) {
//        System.out.println("Balancing for App " + app);
        Random random = new Random();
        for (TaskSimple task : tasklist) {
            double seed = random.nextDouble();
//            System.out.println("Seed: " + seed);
            for (int i = 0; i < this.lbCumulativeProbabilities[app].size(); i++) {
                if (seed < lbCumulativeProbabilities[app].get(i)) {
                    task.setVm(vmsOfApp[app].get(i));
//                    System.out.println("Task " + task.getId() + ", assigned to VM " + vmsOfApp[app].get(i).getId());
                    break;
                }
            }
        }
    }

}
